package net.hackermdch.exparticle.util;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

/**
 * GPU 离屏文本光栅化：自己管理 FBO / shader / VAO，**完全不经过 MC 的 RenderPass 与命令编码器**。
 *
 * <p>为什么不用 MC 的 API（这是本移植最耗时的一处，改这里前先读）：
 * 1.21.6+ 的 {@code GpuTexture}/{@code RenderPass} 是给「帧内渲染」用的，把它们借来画离屏目标时，
 * 离屏结果会受整帧状态影响：实测同一份代码第 1 次调用能画出内容（bbox 覆盖整张 36×27），
 * 之后每次只剩顶部 4 行常量色带（{@code 0x40004000}），且显式重设 Projection、重设 GL viewport、
 * 归零 model-view 都无法修正。可复现证据见 README「GPU 离屏管线」一节。
 *
 * <p>所以这里改成「显卡上的等价实现」：仍然用真正的字形图集在 GPU 上光栅化（与上游
 * {@code Font.drawInBatch} 同源），但矩阵、状态、回读全由本类掌握，不再受帧内状态牵连。
 * 顶点 shader 直接把**像素坐标**映射到 NDC（y 向下），所以投影对齐问题根本不存在。
 *
 * <p>状态纪律：所有会改动的 GL 状态都通过 {@link GlStateManager} 存取（它的缓存跟着一起更新），
 * 用完按原值还原；裸 GL 只用于无状态的查询/回读。任何失败都抛异常，由调用方回退 CPU 光栅化。
 */
public final class GlTextRasterizer {
    private static final String VERTEX_SRC = """
        #version 330 core
        in vec2 aPos;
        in vec2 aUV;
        in vec4 aColor;
        uniform vec2 uSize;
        out vec2 vUV;
        out vec4 vColor;
        void main() {
            vec2 ndc = vec2(aPos.x / uSize.x * 2.0 - 1.0, 1.0 - aPos.y / uSize.y * 2.0);
            gl_Position = vec4(ndc, 0.0, 1.0);
            vUV = aUV;
            vColor = aColor;
        }
        """;
    private static final String FRAGMENT_SRC = """
        #version 330 core
        uniform sampler2D uAtlas;
        uniform float uRedOnly;
        in vec2 vUV;
        in vec4 vColor;
        out vec4 fragColor;
        void main() {
            vec4 texel = texture(uAtlas, vUV);
            // 与原版 rendertype_text 一致：图集纹素 × 顶点色（顶点色来自字形样式，默认白）
            fragColor = mix(texel, vec4(1.0, 1.0, 1.0, texel.r), uRedOnly) * vColor;
        }
        """;

    /** 顶点步长：x, y, u, v, r, g, b, a（8 个 float）。 */
    public static final int VERTEX_FLOATS = 8;

    private static int program;
    private static int vao;
    private static int vbo;
    private static int fbo;
    private static int colorTexture;
    private static int targetWidth;
    private static int targetHeight;
    private static int uSize = -1;
    private static int uAtlas = -1;
    private static int uRedOnly = -1;
    private static String unavailable;

    private GlTextRasterizer() {
    }

    /** 永久性失败（shader 编译不过等）后不再重试，直接让调用方走 CPU。 */
    public static String unavailableReason() {
        return unavailable;
    }

    /**
     * 一批顶点：同一张图集纹理上的字形四边形（顶点格式 x, y, u, v，像素坐标、y 向下）。
     * 一段文本可能跨多张图集（例如拉丁字母与 CJK 分属不同 provider 的页），所以按图集分批。
     */
    public record Batch(GpuTexture atlas, float[] vertices, int floats) {
    }

    /**
     * 把各批字形光栅化进一张 {width × height} 的离屏纹理并回读成 {@link NativeImage}（RGBA，行序自上而下）。
     */
    public static NativeImage rasterize(List<Batch> batches, int width, int height) {
        if (unavailable != null) {
            throw new IllegalStateException("GPU 光栅化不可用：" + unavailable);
        }
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("没有要光栅化的字形");
        }
        ensureProgram();
        ensureTarget(width, height);

        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] prevScissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, prevScissorBox);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuffer = GL11.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);
        int prevPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevTexture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevSrgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        int[] prevColorMask = new int[4];
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, prevColorMask);

        long pixels = 0L;
        try {
            // ---- 画进离屏纹理：无混合、无深度、无裁剪，输出即图集原值 ----
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._viewport(0, 0, width, height);
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GlStateManager._glUseProgram(program);
            GL20.glUniform2f(uSize, (float) width, (float) height);
            for (var batch : batches) {
                // 注意：GlStateManager._glBufferData 只接受 ByteBuffer（或裸地址长度），所以这里按字节分配，
                // 再用 FloatBuffer 视图填数据；ByteBuffer 自身 position/limit 保持整块。
                var buffer = MemoryUtil.memAlloc(batch.floats() * 4);
                try {
                    buffer.asFloatBuffer().put(batch.vertices(), 0, batch.floats());
                    buffer.position(0).limit(batch.floats() * 4);
                    GlStateManager._glBindVertexArray(vao);
                    GlStateManager._glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
                    GlStateManager._glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STREAM_DRAW);
                } finally {
                    MemoryUtil.memFree(buffer);
                }
                boolean red = batch.atlas().getFormat() == TextureFormat.RED8
                    || batch.atlas().getFormat() == TextureFormat.RED8I
                    // 诊断：原版 1.21.10 的字体图集是 RGBA8，R8 分支平时不可达；
                    // `-Dexparticle.text.forceRed=true` 强制走一次，验证「覆盖率取红通道」那条路
                    || Boolean.getBoolean("exparticle.text.forceRed");
                GL20.glUniform1f(uRedOnly, red ? 1.0F : 0.0F);
                GlStateManager._bindTexture(((GlTexture) batch.atlas()).glId());
                GlStateManager._drawArrays(GL11.GL_TRIANGLES, 0, batch.floats() / 4);
            }

            // ---- 回读：**不翻转行**，与上游 TextUtil 一致（glReadPixels 的 row 0 就是文字底行）。
            // 这一步很关键：粒子侧用 `y = row / dpb`（row 越大世界 y 越高），所以 row 0 = 底行
            // 恰好让文字在世界里正立；翻过来会让整个文字上下颠倒（那是本 port 早先的偏差）。
            int size = width * height * 4;
            pixels = MemoryUtil.nmemAlloc(size);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GlStateManager._readPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            var image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            MemoryUtil.memCopy(pixels, image.pixels, size);
            return image;
        } finally {
            if (pixels != 0L) {
                MemoryUtil.nmemFree(pixels);
            }
            // ---- 还原：顺序与设置相反；能走 GlStateManager 的一律走它，保证 MC 的状态缓存同步 ----
            if (prevSrgb) {
                GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            } else {
                GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            }
            GlStateManager._colorMask(prevColorMask[0] != 0, prevColorMask[1] != 0, prevColorMask[2] != 0, prevColorMask[3] != 0);
            if (prevCull) {
                GlStateManager._enableCull();
            }
            if (prevDepth) {
                GlStateManager._enableDepthTest();
            }
            if (prevBlend) {
                GlStateManager._enableBlend();
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(prevTexture0);
            GlStateManager._activeTexture(prevActiveTexture);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPackBuffer);
            GlStateManager._glBindBuffer(GL30.GL_ARRAY_BUFFER, prevArrayBuffer);
            GlStateManager._glUseProgram(prevProgram);
            GlStateManager._glBindVertexArray(prevVao);
            if (prevScissor) {
                GlStateManager._scissorBox(prevScissorBox[0], prevScissorBox[1], prevScissorBox[2], prevScissorBox[3]);
                GlStateManager._enableScissorTest();
            }
            GlStateManager._viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
        }
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        int vertexShader = 0;
        int fragmentShader = 0;
        try {
            vertexShader = compile(GL20.GL_VERTEX_SHADER, VERTEX_SRC);
            fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SRC);
            int id = GlStateManager.glCreateProgram();
            GlStateManager.glAttachShader(id, vertexShader);
            GlStateManager.glAttachShader(id, fragmentShader);
            GlStateManager._glBindAttribLocation(id, 0, "aPos");
            GlStateManager._glBindAttribLocation(id, 1, "aUV");
            GlStateManager._glBindAttribLocation(id, 2, "aColor");
            GlStateManager.glLinkProgram(id);
            if (GlStateManager.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("链接文本光栅化 shader 失败：" + GlStateManager.glGetProgramInfoLog(id, 4096));
            }
            uSize = GlStateManager._glGetUniformLocation(id, "uSize");
            uAtlas = GlStateManager._glGetUniformLocation(id, "uAtlas");
            uRedOnly = GlStateManager._glGetUniformLocation(id, "uRedOnly");
            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GlStateManager._glUseProgram(id);
            GlStateManager._glUniform1i(uAtlas, 0);
            GlStateManager._glUseProgram(prevProgram);
            program = id;
        } catch (Throwable e) {
            unavailable = e.getMessage();
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
        } finally {
            if (vertexShader != 0) {
                GlStateManager.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GlStateManager.glDeleteShader(fragmentShader);
            }
        }
        if (vao == 0) {
            vao = GlStateManager._glGenVertexArrays();
            vbo = GlStateManager._glGenBuffers();
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevArrayBuffer = GL11.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
            GlStateManager._vertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 32, 0L);
            GlStateManager._enableVertexAttribArray(0);
            GlStateManager._vertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 32, 8L);
            GlStateManager._enableVertexAttribArray(1);
            GlStateManager._vertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 32, 16L);
            GlStateManager._enableVertexAttribArray(2);
            GlStateManager._glBindBuffer(GL30.GL_ARRAY_BUFFER, prevArrayBuffer);
            GlStateManager._glBindVertexArray(prevVao);
        }
    }

    private static int compile(int type, String source) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, source);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GlStateManager.glGetShaderInfoLog(shader, 4096);
            GlStateManager.glDeleteShader(shader);
            throw new IllegalStateException("编译文本光栅化 shader 失败：" + log);
        }
        return shader;
    }

    private static void ensureTarget(int width, int height) {
        if (fbo != 0 && targetWidth == width && targetHeight == height) {
            return;
        }
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevTexture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (colorTexture != 0) {
                GlStateManager._deleteTexture(colorTexture);
                colorTexture = 0;
            }
            if (fbo != 0) {
                GlStateManager._glDeleteFramebuffers(fbo);
                fbo = 0;
            }
            colorTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(colorTexture);
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(prevTexture0);
            GlStateManager._activeTexture(prevActiveTexture);
            fbo = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorTexture, 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("离屏 FBO 不完整：0x" + Integer.toHexString(status));
            }
            targetWidth = width;
            targetHeight = height;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        }
    }
}
