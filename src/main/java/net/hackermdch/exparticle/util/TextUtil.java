package net.hackermdch.exparticle.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 文本 → NativeImage。
 *
 * <p>上游（1.21.1 NeoForge）走的是 `TextureTarget` + `Font.drawInBatch` + `glReadPixels` 的 GPU 离屏渲染。
 * 本移植给出两条等价路径：
 * <ul>
 *   <li><b>GPU（默认）</b>：{@link GlTextRasterizer} —— 自管 FBO/shader 的离屏光栅化。字形度量与图集 UV
 *       取自 `Font.getGlyph` + `BakedSheetGlyph`（access widener 打开），像素来自 GPU 对真实字体图集的采样
 *       （因此带抗锯齿覆盖率），与上游的渲染结果同源；</li>
 *   <li><b>CPU 回退</b>：{@link #toImage} —— 从字体图集逐像素拼字，任何 GL 异常（非渲染线程调用、
 *       shader 编译失败、FBO 不完整…）都会自动走这条路。</li>
 * </ul>
 * 两条路的输出语义一致：像素 = 图集原值（白字 + 覆盖率 alpha），调用方按 ARGB 取色生成粒子。
 * 开关：`-Dexparticle.text=cpu|auto|gpu`（默认 auto）、`-Dexparticle.text.compare=true` 打 CPU/GPU 对照图。
 *
 * <p>为什么不用 MC 的 `RenderPass` API 做离屏：见 {@link GlTextRasterizer} 的类注释（实测走不通）。
 */
public class TextUtil {
    private static final Map<GpuTexture, BufferedImage> ATLASES = new IdentityHashMap<>();
    /** GPU 路径的成功/失败计数与日志节流。 */
    private static int gpuRenders;
    private static int gpuFailures;
    private static int gpuLogs;
    private static int comparisons;
    /** 诊断日志开关：默认只保留失败/回退日志，正常出图不刷屏。 */
    private static final boolean VERBOSE = Boolean.getBoolean("exparticle.selftest")
        || Boolean.getBoolean("exparticle.gpuText") || Boolean.getBoolean("exparticle.text.compare");

    private TextUtil() {
    }

    /**
     * 与上游同名 API：立即生成图像并交付；图像在回调返回后释放。
     *
     * <p>默认走 GPU；任何异常都会回退 CPU 光栅化。`-Dexparticle.text=gpu`（严格模式）时失败会写进聊天栏，
     * 便于及时发现「悄悄退回 CPU」；默认 auto 只在前几次打 stdout。
     */
    public static void requestImage(Component text, float scale, Consumer<NativeImage> callback) {
        var mode = System.getProperty("exparticle.text", "auto");
        NativeImage image = null;
        if (!"cpu".equalsIgnoreCase(mode)) {
            try {
                image = toImageGpu(text, scale);
            } catch (Throwable e) {
                gpuFailures++;
                if (gpuLogs++ < 3) {
                    System.out.println("[exparticle] GPU 文本光栅化失败，回退 CPU：" + e);
                }
                if ("gpu".equalsIgnoreCase(mode)) {
                    ClientMessageUtil.addChatMessage(e);
                }
            }
        }
        if (image == null) {
            image = toImage(text, scale);
        }
        if (Boolean.getBoolean("exparticle.text.compare")) {
            comparisons++;
            compareWithCpu(text, scale, image);
        }
        try {
            callback.accept(image);
        } finally {
            image.close();
        }
    }

    /**
     * 诊断入口（`-Dexparticle.gpuText=true` 时由客户端 tick 每秒调一次）：只走 GPU 路径并打点，
     * 用来做「连续 N 次是否稳定」的重复实验，不影响正常出图。
     */
    public static void probeGpu(Component text, float scale) {
        int index = gpuRenders + 1;
        try (var image = toImageGpu(text, scale)) {
            System.out.println("[exparticle] gpu-probe #" + index + describe(image));
        } catch (Throwable e) {
            System.out.println("[exparticle] gpu-probe #" + index + " failed: " + e);
        }
    }

    /** CPU / GPU 两条路的逐像素对照（诊断用）：尺寸、着色像素数、差异像素数与两张 ASCII 图。 */
    public static void comparePaths(Component text, float scale) {
        try (var gpu = toImageGpu(text, scale); var cpu = toImage(text, scale)) {
            report(gpu, cpu);
            logMap("gpu", gpu);
            logMap("cpu", cpu);
        } catch (Throwable e) {
            System.out.println("[exparticle] text compare failed: " + e);
        }
    }

    /** GPU 渲染统计（自检日志用）。 */
    public static String stats() {
        return "gpu ok=" + gpuRenders + " failed=" + gpuFailures
            + (GlTextRasterizer.unavailableReason() != null ? " unavailable=" + GlTextRasterizer.unavailableReason() : "");
    }

    private static void compareWithCpu(Component text, float scale, NativeImage current) {
        try (var cpu = toImage(text, scale)) {
            report(current, cpu);
            logMap("gpu", current);
            logMap("cpu", cpu);
        } catch (Throwable e) {
            System.out.println("[exparticle] text compare failed: " + e);
        }
    }

    /**
     * 判据：**可见像素（alpha≠0）必须逐位一致**，`visibleDiff` / `alphaDiff` 应为 0；
     * `transparentDiff` 只统计「两边都透明但 RGB 不同」（GPU 在字形四边形内写 `(1,1,1,0)*样式色`，
     * CPU 的空白处是 0），它不影响粒子生成（调用方按 alpha≠0 过滤），只作记录。
     */
    private static void report(NativeImage gpu, NativeImage cpu) {
        int gpuPainted = 0;
        int cpuPainted = 0;
        int visibleDiff = 0;
        int transparentDiff = 0;
        int alphaDiff = 0;
        var samples = new StringBuilder();
        int w = Math.max(gpu.getWidth(), cpu.getWidth());
        int h = Math.max(gpu.getHeight(), cpu.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = x < gpu.getWidth() && y < gpu.getHeight() ? gpu.getPixel(x, y) : 0;
                int c = x < cpu.getWidth() && y < cpu.getHeight() ? cpu.getPixel(x, y) : 0;
                if ((g >>> 24) != 0) {
                    gpuPainted++;
                }
                if ((c >>> 24) != 0) {
                    cpuPainted++;
                }
                if (g != c) {
                    if ((g >>> 24) == 0 && (c >>> 24) == 0) {
                        transparentDiff++;
                    } else {
                        visibleDiff++;
                        if (samples.length() < 220) {
                            samples.append(" (").append(x).append(',').append(y).append(") gpu=0x")
                                .append(Integer.toHexString(g)).append(" cpu=0x").append(Integer.toHexString(c));
                        }
                    }
                }
                if ((g >>> 24) != (c >>> 24)) {
                    alphaDiff++;
                }
            }
        }
        System.out.println("[exparticle] text compare: gpu=" + gpu.getWidth() + "x" + gpu.getHeight()
            + " painted=" + gpuPainted + " | cpu=" + cpu.getWidth() + "x" + cpu.getHeight()
            + " painted=" + cpuPainted + " | visibleDiff=" + visibleDiff + " alphaDiff=" + alphaDiff
            + " transparentDiff=" + transparentDiff + "/" + (w * h)
            // NativeImage(Format.RGBA) 的包序是 0xAABBGGRR，所以纯红会显示成 0xff0000ff
            + " gpuMid=0x" + Integer.toHexString(gpu.getPixel(gpu.getWidth() / 2, gpu.getHeight() / 2))
            + (samples.length() > 0 ? " first~" + samples : ""));
    }

    private static void logMap(String label, NativeImage image) {
        var sb = new StringBuilder("\n[exparticle] text map[" + label + "] ('.'=空 '#'=实 '+'=半透明):\n");
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int a = image.getPixel(x, y) >>> 24;
                sb.append(a == 0 ? '.' : (a > 128 ? '#' : '+'));
            }
            sb.append('\n');
        }
        System.out.print(sb);
    }

    private static String describe(NativeImage image) {
        int painted = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getPixel(x, y) >>> 24) != 0) {
                    painted++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return " size=" + image.getWidth() + "x" + image.getHeight() + " painted=" + painted
            + " bbox=[" + minX + "," + minY + " .. " + maxX + "," + maxY + "]"
            + " px[0,0]=" + Integer.toHexString(image.getPixel(0, 0))
            + " px[mid]=" + Integer.toHexString(image.getPixel(image.getWidth() / 2, image.getHeight() / 2));
    }

    /**
     * GPU 路径：把字形四边形（每顶点 x,y,u,v）交给 {@link GlTextRasterizer} 光栅化。
     * 顶点坐标是像素空间、y 向下；UV 取自字形在图集里的位置。
     */
    private static NativeImage toImageGpu(Component text, float scale) {
        if (scale <= 0.0F) {
            return blank(1, 1);
        }
        var run = collect(text, scale);
        if (run.glyphs().isEmpty()) {
            return blank(run.width(), run.height());
        }
        // 一段文本可能跨多张图集（拉丁 / CJK / 特殊字形分属不同 provider 的页），按图集批次组织顶点
        var grouped = new LinkedHashMap<GpuTexture, float[]>();
        var counts = new LinkedHashMap<GpuTexture, Integer>();
        for (var g : run.glyphs()) {
            var atlas = g.sheet().textureView.texture();
            var vertices = grouped.computeIfAbsent(atlas,
                key -> new float[run.glyphs().size() * 6 * GlTextRasterizer.VERTEX_FLOATS]);
            int at = counts.getOrDefault(atlas, 0);
            float u0 = g.sheet().u0;
            float u1 = g.sheet().u1;
            float v0 = g.sheet().v0;
            float v1 = g.sheet().v1;
            float tlX = g.tlX() * scale;
            float tlY = g.tlY() * scale;
            float blX = g.blX() * scale;
            float blY = g.blY() * scale;
            float brX = g.brX() * scale;
            float brY = g.brY() * scale;
            float trX = g.trX() * scale;
            float trY = g.trY() * scale;
            // 与 MC 相同的四边形（左上→左下→右下→右上），拆成两个三角形
            at = vertex(vertices, at, tlX, tlY, u0, v0, g.color());
            at = vertex(vertices, at, blX, blY, u0, v1, g.color());
            at = vertex(vertices, at, brX, brY, u1, v1, g.color());
            at = vertex(vertices, at, tlX, tlY, u0, v0, g.color());
            at = vertex(vertices, at, brX, brY, u1, v1, g.color());
            at = vertex(vertices, at, trX, trY, u1, v0, g.color());
            counts.put(atlas, at);
        }
        var batches = new ArrayList<GlTextRasterizer.Batch>();
        for (var entry : grouped.entrySet()) {
            batches.add(new GlTextRasterizer.Batch(entry.getKey(), entry.getValue(), counts.get(entry.getKey())));
        }
        var image = GlTextRasterizer.rasterize(batches, run.width(), run.height());
        gpuRenders++;
        if (VERBOSE && gpuRenders <= 3) {
            var detail = new StringBuilder();
            for (var batch : batches) {
                detail.append(" atlas=").append(batch.atlas().getFormat()).append('#')
                    .append(Integer.toHexString(((com.mojang.blaze3d.opengl.GlTexture) batch.atlas()).glId()))
                    .append("/").append(batch.floats() / (6 * GlTextRasterizer.VERTEX_FLOATS)).append("glyphs");
            }
            System.out.println("[exparticle] gpu-render #" + gpuRenders + " glyphs=" + run.glyphs().size()
                + " atlases=" + batches.size() + detail + describe(image));
        }
        return image;
    }

    private static int vertex(float[] out, int at, float x, float y, float u, float v, int color) {
        out[at] = x;
        out[at + 1] = y;
        out[at + 2] = u;
        out[at + 3] = v;
        out[at + 4] = ((color >> 16) & 0xFF) / 255.0F;
        out[at + 5] = ((color >> 8) & 0xFF) / 255.0F;
        out[at + 6] = (color & 0xFF) / 255.0F;
        out[at + 7] = ((color >>> 24) & 0xFF) / 255.0F;
        return at + GlTextRasterizer.VERTEX_FLOATS;
    }

    private static NativeImage blank(int width, int height) {
        var image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        // NativeImage 的内存**不清零**，不显式清一遍的话未绘制像素会带垃圾 alpha，调用方会误生成粒子
        MemoryUtil.memSet(image.pixels, 0, (long) width * height * 4);
        return image;
    }

    /**
     * 收集字形四边形（CPU/GPU 两条路共用）：字形度量与图集 UV 来自
     * {@code Font.getGlyph} + {@code BakedSheetGlyph} 的 (left,right,up,down)/(u0,u1,v0,v1)。
     *
     * <p>四边形顶点**照抄 MC {@code BakedSheetGlyph.render} 的公式**：斜体按 (1 - 0.25*up/down) 错切、
     * 粗体四周各加厚 0.1（extraThickness），颜色取 {@code Style.getColor()}（默认白）；
     * 步进也按原版用 {@code GlyphInfo.getAdvance(bold)}（粗体多一个 boldOffset）。
     * 这三处不对齐的话，`text` 的粗体/斜体/彩色文字会和上游（`Font.drawInBatch`）出图不一致。
     */
    private static GlyphRun collect(Component text, float scale) {
        var font = Minecraft.getInstance().font;
        var glyphs = new ArrayList<GlyphBlit>();
        var pen = new float[]{0.0F};
        text.getVisualOrderText().accept((index, style, codepoint) -> {
            var glyph = font.getGlyph(codepoint, style);
            if (glyph instanceof BakedSheetGlyph sheet) {
                boolean bold = style.isBold();
                boolean italic = style.isItalic();
                float thickness = bold ? 0.1F : 0.0F;
                float shearTop = italic ? 1.0F - 0.25F * sheet.up : 0.0F;
                float shearBottom = italic ? 1.0F - 0.25F * sheet.down : 0.0F;
                float left = pen[0] + sheet.left;
                float right = pen[0] + sheet.right;
                glyphs.add(new GlyphBlit(sheet, color(style),
                    left + shearTop - thickness, sheet.up - thickness,
                    left + shearBottom - thickness, sheet.down + thickness,
                    right + shearBottom + thickness, sheet.down + thickness,
                    right + shearTop + thickness, sheet.up - thickness));
            }
            pen[0] += glyph.info().getAdvance(style.isBold());
            return true;
        });
        // 尺寸口径与上游 TextUtil 完全一致：`(int) (font.width(text) * scale)` × `(int) (font.lineHeight * scale)`，
        // 且**不做 y 归一化**（上游把字形按基线直接画在 0..lineHeight 的离屏目标里，越界就裁掉）。
        // 之前按「ink 高度」裁高度会让粒子布局比上游少几行（scale=3 时 24 vs 27），所以这里改回原版口径。
        int width = Math.max(1, (int) (font.width(text) * scale));
        int height = Math.max(1, (int) (font.lineHeight * scale));
        return new GlyphRun(glyphs, width, height);
    }

    /** 字形颜色：样式带颜色就用它，否则默认白（等价上游 `Font.drawInBatch(..., -1, ...)`）。 */
    private static int color(Style style) {
        var textColor = style.getColor();
        return textColor != null ? 0xFF000000 | textColor.getValue() : 0xFFFFFFFF;
    }

    /** CPU 回退路径：从字体图集逐像素拼字（完全不碰 GPU 渲染管线）。 */
    public static NativeImage toImage(Component text, float scale) {
        if (scale <= 0.0F) {
            return blank(1, 1);
        }
        var run = collect(text, scale);
        int width = run.width();
        int height = run.height();
        var image = blank(width, height);
        int atlasNonZero = -1;
        int painted = 0;
        boolean trace = VERBOSE && cpuTraces++ < 10;
        // 每次调用重读图集（字体是按需拼接的），但同一次调用里的多字形共用一份快照
        var atlases = new IdentityHashMap<GpuTexture, BufferedImage>();
        for (var g : run.glyphs()) {
            var texture = g.sheet().textureView.texture();
            var atlas = atlases.computeIfAbsent(texture, TextUtil::readAtlas);
            if (trace) {
                System.out.println("[exparticle] cpu glyph: texture=" + texture.getFormat() + "#"
                    + ((com.mojang.blaze3d.opengl.GlTexture) texture).glId()
                    + " u=(" + g.sheet().u0 + "," + g.sheet().u1 + ") v=(" + g.sheet().v0 + "," + g.sheet().v1
                    + ") updown=(" + g.sheet().up + "," + g.sheet().down + ") bold/italic 后 quad=("
                    + g.tlX() + "," + g.tlY() + ")-(" + g.brX() + "," + g.brY()
                    + ") atlas=" + atlas.getWidth() + "x" + atlas.getHeight());
            }
            if (atlasNonZero < 0) {
                int nz = 0;
                for (int yy = 0; yy < atlas.getHeight() && nz == 0; yy++) {
                    for (int xx = 0; xx < atlas.getWidth(); xx++) {
                        if ((atlas.getRGB(xx, yy) & 0xFF000000) != 0) {
                            nz++;
                            break;
                        }
                    }
                }
                atlasNonZero = nz;
            }
            int aw = atlas.getWidth();
            int ah = atlas.getHeight();
            int sx0 = Math.round(g.sheet().u0 * aw);
            int sx1 = Math.round(g.sheet().u1 * aw);
            int sy0 = Math.round(g.sheet().v0 * ah);
            int sy1 = Math.round(g.sheet().v1 * ah);
            int sw = Math.max(1, sx1 - sx0);
            int sh = Math.max(1, sy1 - sy0);
            // 目标像素驱动：把字形四边形（含斜体错切 / 粗体加厚）按仿射逆变换回字形盒坐标，再取图集像素。
            // 与 GPU 侧的四边形采样等价（这里是最邻近取样，GPU 用图集自身的过滤方式）。
            float tlX = g.tlX() * scale;
            float tlY = g.tlY() * scale;
            float blX = g.blX() * scale;
            float blY = g.blY() * scale;
            float brX = g.brX() * scale;
            float brY = g.brY() * scale;
            float trX = g.trX() * scale;
            float trY = g.trY() * scale;
            float ax = trX - tlX;
            float ay = trY - tlY;
            float bx = blX - tlX;
            float by = blY - tlY;
            float det = ax * by - ay * bx;
            if (Math.abs(det) < 1.0E-6F) {
                continue;
            }
            int ink = 0;
            int px0 = Math.max(0, (int) Math.floor(Math.min(Math.min(tlX, blX), Math.min(brX, trX))));
            int px1 = Math.min(width - 1, (int) Math.ceil(Math.max(Math.max(tlX, blX), Math.max(brX, trX))));
            int py0 = Math.max(0, (int) Math.floor(Math.min(Math.min(tlY, blY), Math.min(brY, trY))));
            int py1 = Math.min(height - 1, (int) Math.ceil(Math.max(Math.max(tlY, blY), Math.max(brY, trY))));
            for (int py = py0; py <= py1; py++) {
                for (int px = px0; px <= px1; px++) {
                    float dx = px + 0.5F - tlX;
                    float dy = py + 0.5F - tlY;
                    float s = (dx * by - dy * bx) / det;
                    float t = (ax * dy - ay * dx) / det;
                    if (s < 0.0F || s >= 1.0F || t < 0.0F || t >= 1.0F) {
                        continue;
                    }
                    int sx = Math.min(aw - 1, sx0 + (int) (s * sw));
                    int sy = Math.min(ah - 1, sy0 + (int) (t * sh));
                    int src = tint(atlas.getRGB(sx, sy), g.color());
                    if ((src >>> 24) == 0) {
                        continue;
                    }
                    ink++;
                    // NativeImage.setPixel 收的是 ARGB（内部自己转 ABGR），不要再手工换序
                    // 行序与上游一致：row 0 = 文字底行（GPU 侧靠 glReadPixels 天然如此，这里手工翻过来）
                    image.setPixel(px, height - 1 - py, src);
                    painted++;
                }
            }
            if (trace) {
                System.out.println("[exparticle] cpu glyph: atlas=" + aw + "x" + ah + " rect=[" + sx0 + "," + sy0
                    + " " + sw + "x" + sh + "] ink=" + ink + " quad=(" + tlX + "," + tlY + ")(" + brX + "," + brY + ")");
            }
        }
        if (painted == 0) {
            System.out.println("[exparticle] text raster produced no pixels (glyphs=" + run.glyphs().size()
                + ", size=" + width + "x" + height + ", atlasNonZero=" + atlasNonZero + ")");
        }
        return image;
    }

    private static int cpuTraces;

    /**
     * 图集回读：直接用 glGetTexImage（**不要**用 MC 的 `CommandEncoder.copyTextureToBuffer` ——
     * 帧级 render pass 打开时它会被拒，历史上这里静默拿到过半透明的垃圾数据）。
     * R8 图集按 GL_RED 读（1 字节/像素），其余按 RGBA 读。
     */
    private static NativeImage readback(GpuTexture texture, int width, int height) {
        boolean red = texture.getFormat() == com.mojang.blaze3d.textures.TextureFormat.RED8
            || texture.getFormat() == com.mojang.blaze3d.textures.TextureFormat.RED8I;
        var format = red ? NativeImage.Format.LUMINANCE : NativeImage.Format.RGBA;
        var image = blankImage(format, width, height);
        int size = width * height * (red ? 1 : 4);
        long pixels = MemoryUtil.nmemAlloc(size);
        int prevPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int prevAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int prevRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int prevSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int prevSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevBound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GlStateManager._bindTexture(((GlTexture) texture).glId());
            GlStateManager.clearGlErrors();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, red ? GL11.GL_RED : GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, pixels);
            int glError = GlStateManager._getError();
            MemoryUtil.memCopy(pixels, image.pixels, size);
            int nonzero = 0;
            for (int i = 0; i < size; i += Math.max(1, (red ? 1 : 4) * 7)) {
                if (MemoryUtil.memGetByte(pixels + i) != 0) {
                    nonzero++;
                }
            }
            if (VERBOSE) {
                System.out.println("[exparticle] atlas readback: id=" + ((GlTexture) texture).glId() + " format="
                    + texture.getFormat() + " size=" + width + "x" + height + " glError=0x" + Integer.toHexString(glError)
                    + " nonzeroSamples=" + nonzero);
            }
        } finally {
            GlStateManager._bindTexture(prevBound);
            GlStateManager._activeTexture(prevActiveTexture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevAlignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, prevRowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, prevSkipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, prevSkipPixels);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPackBuffer);
            MemoryUtil.nmemFree(pixels);
        }
        return image;
    }

    private static NativeImage blankImage(NativeImage.Format format, int width, int height) {
        var image = new NativeImage(format, width, height, false);
        MemoryUtil.memSet(image.pixels, 0, (long) width * height * format.components());
        return image;
    }

    /**
     * 图集像素。**调用级缓存，不跨调用复用**：原版字体是「按需光栅化」的——`Font.getGlyph` 会把新字形
     * 即时拼进图集纹理，所以任何早于本次调用的快照都会缺少后面才用到的字形（实测：跨调用缓存后，
     * 随后才用到的 L/T 采样区全透明，CPU 回退路径直接出 0 像素）。
     */
    private static BufferedImage readAtlas(GpuTexture texture) {
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        NativeImage read = null;
        try {
            read = readback(texture, width, height);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            boolean luminance = read.format() == NativeImage.Format.LUMINANCE || read.format() == NativeImage.Format.LUMINANCE_ALPHA;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb;
                    if (luminance) {
                        int alpha = read.getLuminanceOrAlpha(x, y) & 0xFF;
                        argb = (alpha << 24) | 0xFFFFFF;
                    } else {
                        // NativeImage.getPixel 返回的就是 ARGB（内部已从 ABGR 转好）
                        argb = read.getPixel(x, y);
                    }
                    image.setRGB(x, y, argb);
                }
            }
            return image;
        } catch (Throwable e) {
            // 回退：原版字体 dump 走的就是这条（PNG 落盘再读）
            try {
                var file = Files.createTempFile("exparticle-font-atlas", ".png");
                TextureUtil.writeAsPNG(file, "exparticle-font-atlas", texture, 0, value -> value);
                var image = ImageIO.read(file.toFile());
                Files.deleteIfExists(file);
                if (image != null) {
                    return image;
                }
            } catch (Throwable e2) {
                ClientMessageUtil.addChatMessage(e2);
            }
            System.out.println("[exparticle] font atlas readback failed: " + e);
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        } finally {
            if (read != null) {
                read.close();
            }
        }
    }

    /** 图集颜色乘法（等价 shader 里的 `texel * vertexColor`）：输入/输出都是 ARGB。 */
    private static int tint(int argb, int color) {
        float r = ((argb >> 16) & 0xFF) * ((color >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) * ((color >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) * (color & 0xFF) / 255.0F;
        float a = ((argb >>> 24) & 0xFF) * ((color >>> 24) & 0xFF) / 255.0F;
        return ((int) a << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    /**
     * 一个字形四边形（字体单位、y 向下），顶点顺序与 MC `BakedSheetGlyph.render` 一致：
     * 左上 → 左下 → 右下 → 右上，UV 依次 (u0,v0) (u0,v1) (u1,v1) (u1,v0)。
     */
    private record GlyphBlit(BakedSheetGlyph sheet, int color,
                             float tlX, float tlY, float blX, float blY,
                             float brX, float brY, float trX, float trY) {
    }

    private record GlyphRun(List<GlyphBlit> glyphs, int width, int height) {
    }
}

