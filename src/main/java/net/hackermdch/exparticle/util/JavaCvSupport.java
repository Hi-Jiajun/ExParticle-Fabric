package net.hackermdch.exparticle.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

/**
 * JavaCV 装载器（Fabric 版）。
 *
 * <p>上游在 NeoForge 上用 {@code sun.misc.Unsafe} 取 {@code IMPL_LOOKUP}，再反射改
 * {@code ModuleClassLoader} 的私有字段把 {@code gamedir/javacv} 里的 jar 挂进模块层。
 * Fabric 没有模块层，等价做法是：用 child-first 的 {@link ChildFirstClassLoader}
 * 把 {@code gamedir/javacv/*.jar} 与「本 mod jar」一起作为搜索路径，
 * 再由它定义只引用 JavaCV 的桥接类 {@link VideoDecoder}。
 *
 * <p>用户体验与上游一致：把 JavaCV 相关 jar 丢进 {@code gamedir/javacv} 即可启用 video 命令；
 * 目录为空或加载失败时 {@link #available()} 为 false，video 命令会给出与上游同样的提示。
 */
public final class JavaCvSupport {
    /** 只引用 JavaCV、必须由子加载器定义的桥接类。 */
    private static final String BRIDGE_CLASS = "net.hackermdch.exparticle.util.VideoDecoder";
    /** 子优先前缀：桥接类本身 + JavaCV 全部包。 */
    private static final Set<String> CHILD_FIRST_PREFIXES = Set.of(
        "net.hackermdch.exparticle.util.VideoDecoder",
        "org.bytedeco."
    );

    private static final File JAVACV_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("javacv").toFile();
    private static boolean initialized;
    private static Method decodeMethod;

    private JavaCvSupport() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!JAVACV_DIR.isDirectory()) {
            JAVACV_DIR.mkdirs();
        }
        var jars = JAVACV_DIR.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        System.out.println("[exparticle] JavaCV probe: dir=" + JAVACV_DIR.getAbsolutePath()
            + " exists=" + JAVACV_DIR.isDirectory() + " jars=" + (jars == null ? -1 : jars.length));
        if (jars == null || jars.length == 0) {
            return;
        }
        // 注意顺序：必须在任何 JavaCPP 类被初始化之前完成原生库抽取与属性设置，
        // 因为 JavaCPP 的 Loader 会在类初始化时缓存这些属性（pathsFirst / 库路径）。
        prepareNatives(jars);
        try {
            var urls = new ArrayList<URL>();
            for (var jar : jars) {
                urls.add(jar.toURI().toURL());
            }
            // 关键：把本 mod 自身也加进搜索路径，子加载器才能定义 VideoDecoder（它引用 JavaCV 类）。
            var self = JavaCvSupport.class.getProtectionDomain().getCodeSource().getLocation();
            if (self != null) {
                urls.add(self);
            }
            var loader = new ChildFirstClassLoader(urls.toArray(URL[]::new),
                JavaCvSupport.class.getClassLoader(), CHILD_FIRST_PREFIXES);
            var bridge = Class.forName(BRIDGE_CLASS, true, loader);
            // 用上游同款异步解码（VideoDecoder.decode 内部投到 5 线程池）；decodeSync 只用于诊断。
            decodeMethod = bridge.getMethod("decode", String.class, Predicate.class);
            System.out.println("[exparticle] JavaCV bridge ready: " + bridge + " method=" + decodeMethod);
        } catch (Throwable e) {
            decodeMethod = null;
            System.out.println("[exparticle] JavaCV bridge FAILED");
            e.printStackTrace();
            ExParticleLog.warn("Failed to load JavaCV from " + JAVACV_DIR.getAbsolutePath(), e);
        }
    }

    public static boolean available() {
        init();
        return decodeMethod != null;
    }

    public static void decode(String path, Predicate<BufferedImage> consumer) {
        init();
        if (decodeMethod == null) {
            return;
        }
        try {
            decodeMethod.invoke(null, path, consumer);
        } catch (Throwable e) {
            ClientMessageUtil.addChatMessage(e);
        }
    }

    /**
     * 把平台原生库从 `*-<platform>.jar` 里抽到 `gamedir/javacv/natives/`，并把该目录塞进
     * {@code java.library.path}。
     *
     * <p>为什么需要：JavaCPP 在「非系统类加载器」环境下有时找不到平台 jar 里的资源
     * （实测：同一批 jar 用命令行独立跑没问题，进了游戏就回退成
     * {@code System.loadLibrary("jniavutil")} → {@code UnsatisfiedLinkError: no jniavutil in java.library.path}）。
     * 把 DLL 摊到磁盘目录并让库路径包含它，JavaCPP 的兜底分支就能命中；
     * 只要本 mod 比任何一次 {@code System.loadLibrary} 更早设置，JVM 的库路径缓存就还没定型。
     */
    private static void prepareNatives(File[] jars) {
        try {
            var platform = detectPlatform();
            var natives = new File(JAVACV_DIR, "natives");
            if (!natives.isDirectory() && !natives.mkdirs()) {
                return;
            }
            int extracted = 0;
            for (var jar : jars) {
                try (var zip = new ZipFile(jar)) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        var name = entry.getName();
                        if (entry.isDirectory() || !name.endsWith(".dll") || !name.contains("/" + platform + "/")) {
                            continue;
                        }
                        var target = new File(natives, name.substring(name.lastIndexOf('/') + 1));
                        if (target.isFile() && target.length() == entry.getSize()) {
                            continue;
                        }
                        try (var in = zip.getInputStream(entry); var out = new java.io.FileOutputStream(target)) {
                            in.transferTo(out);
                        }
                        extracted++;
                    }
                }
            }
            var path = natives.getAbsolutePath();
            var current = System.getProperty("java.library.path", "");
            if (!current.contains(path)) {
                System.setProperty("java.library.path", path + File.pathSeparator + current);
            }
            // JavaCPP 默认先在被加载类的资源里找本地库；在 Fabric 的子加载器环境下这一步实测会找不到
            // （同一批 jar 用命令行独立跑没问题）。打开 pathsFirst 后 JavaCPP 会优先扫描
            // java.library.path ——它读的是系统属性而不是 JVM 内部缓存，所以这里设置一定生效。
            System.setProperty("org.bytedeco.javacpp.pathsFirst", "true");
            System.out.println("[exparticle] JavaCV natives ready: platform=" + platform + " dir=" + path
                + " extracted=" + extracted);
        } catch (Throwable e) {
            System.out.println("[exparticle] JavaCV natives prepare FAILED");
            e.printStackTrace();
        }
    }

    /** 自算 JavaCPP 平台串（不触碰 JavaCPP 类，避免过早初始化它的静态属性）。 */
    private static String detectPlatform() {
        var os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        var arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        var archName = switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch;
        };
        if (os.contains("win")) {
            return "windows-" + archName;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "macosx-" + archName;
        } else if (os.contains("linux")) {
            return "linux-" + archName;
        }
        return os.replaceAll("\\s+", "") + "-" + archName;
    }
}
