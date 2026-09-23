package net.hackermdch.exparticle.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * 子优先（child-first）类加载器：命中 {@code prefixes} 的类先在自己的 URL 里找，
 * 其余照常委派父加载器。
 *
 * <p>为什么需要它：JavaCV 的 jar 不在 mod 的类路径上（用户按上游习惯放进 {@code gamedir/javacv}），
 * 而 {@code VideoDecoder} 是**直接引用 JavaCV 类**的桥接类。桥接类必须由「看得见 JavaCV 的加载器」定义，
 * 同时又不能影响其余 ExParticle 类的身份（{@code ClientMessageUtil} 等仍走父加载器，保持同一份类）。
 */
final class ChildFirstClassLoader extends URLClassLoader {
    private final Set<String> prefixes;

    ChildFirstClassLoader(URL[] urls, ClassLoader parent, Set<String> prefixes) {
        super(urls, parent);
        this.prefixes = prefixes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            var loaded = findLoadedClass(name);
            if (loaded == null) {
                var childFirst = false;
                for (var prefix : prefixes) {
                    if (name.startsWith(prefix)) {
                        childFirst = true;
                        break;
                    }
                }
                if (childFirst) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // 回落到父加载器
                    }
                }
            }
            if (loaded == null) {
                loaded = super.loadClass(name, resolve);
            } else if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
