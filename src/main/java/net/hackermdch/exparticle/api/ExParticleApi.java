package net.hackermdch.exparticle.api;

import net.hackermdch.exparticle.util.*;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static net.hackermdch.exparticle.util.ParticleUtil.CLIENT;
import static net.hackermdch.exparticle.util.ParticleUtil.getRotateFlipMat;

public class ExParticleApi {
    public static Particle createParticle(ParticleOptions effect, double x, double y, double z, double cx, double cy, double cz, float red, float green, float blue, float alpha, double vx, double vy, double vz, int age, @Nullable IExecutable expression, double step) {
        var particle = CLIENT.particleEngine.makeParticle(effect, x, y, z, vx, vy, vz);
        if (particle != null) {
            ((IParticle) particle).setManaged(true);
            ((IParticle) particle).setColorRGB(red, green, blue);
            ((IParticle) particle).setAlphaValue(alpha);
            if (vx == (double) 0.0F && vy == (double) 0.0F && vz == (double) 0.0F) {
                ((IParticle) particle).setStop(true);
            } else {
                ((IParticle) particle).setStop(false);
                particle.xd = vx;
                particle.yd = vy;
                particle.zd = vz;
            }
            ((IParticle) particle).setGravity(0.0f);
            ((IParticle) particle).setCenterX(cx);
            ((IParticle) particle).setCenterY(cy);
            ((IParticle) particle).setCenterZ(cz);
            if (age > 0) {
                particle.setLifetime(age);
            } else if (age == -1) {
                particle.setLifetime(Integer.MAX_VALUE);
            }
            if (expression != null) {
                ((IParticle) particle).setExe(expression);
                ((IParticle) particle).setStep(step);
                ((IParticle) particle).setCustomMove(true);
            }
        }
        return particle;
    }

    public static Particle createParticleFromData(ParticleOptions effect, double x, double y, double z, double cx, double cy, double cz, IExecutable speedExpression, double speedStep, ParticleStruct data) {
        var particle = createParticle(effect, x, y, z, cx, cy, cz, (float) data.cr, (float) data.cg, (float) data.cb, (float) data.alpha, data.vx, data.vy, data.vz, (int) data.age, speedExpression, speedStep);
        if (particle != null) {
            ((IParticle) particle).setCustomSize(data.size);
            ((IParticle) particle).setCustomLight(data.light);
            ((IParticle) particle).setGravity((float) data.gravity);
            ((IParticle) particle).setFriction((float) data.friction);
        }
        return particle;
    }

    public static List<Particle> genNormal(ParticleOptions effect, RandomSource random, Vec3 pos, Vector4f color, Vec3 speed, Vec3 range, int count, int age, @Nullable IExecutable expression, double step) {
        var ps = new ArrayList<Particle>();
        double x = pos.x, y = pos.y, z = pos.z;
        double vx = speed.x, vy = speed.y, vz = speed.z;
        double dx = range.x, dy = range.y, dz = range.z;
        for (int i = 0; i < count; ++i) {
            double rx = random.nextGaussian() * dx;
            double ry = random.nextGaussian() * dy;
            double rz = random.nextGaussian() * dz;
            ps.add(createParticle(effect, x + rx, y + ry, z + rz, x, y, z, color.x, color.y, color.z, color.w, vx, vy, vz, age, expression, step));
        }
        return ps.stream().filter(Objects::nonNull).toList();
    }

    public static List<Particle> genCond(ParticleOptions effect, Vec3 pos, Vector4f color, Vec3 speed, Vec3 range, IExecutable expression, double step, int age, @Nullable IExecutable speedExpression, double speedStep) {
        var ps = new ArrayList<Particle>();
        double x = pos.x, y = pos.y, z = pos.z;
        double vx = speed.x, vy = speed.y, vz = speed.z;
        double dx = range.x, dy = range.y, dz = range.z;
        var data = expression.getData();
        for (var cx = -dx; cx <= dx; cx += step) {
            for (var cy = -dy; cy <= dy; cy += step) {
                for (var cz = -dz; cz <= dz; cz += step) {
                    data.x = cx;
                    data.y = cy;
                    data.z = cz;
                    data.s1 = Math.atan2(cz, cx);
                    data.s2 = Math.atan2(cy, Math.hypot(cx, cz));
                    data.dis = Math.sqrt(cx * cx + cy * cy + cz * cz);
                    if (expression.invoke() != 0)
                        ps.add(createParticle(effect, x + cx, y + cy, z + cz, x, y, z, color.x, color.y, color.z, color.w, vx, vy, vz, age, speedExpression, speedStep));
                }
            }
        }
        return ps.stream().filter(Objects::nonNull).toList();
    }

    public static List<Particle> genParameter(ParticleOptions effect, boolean polar, Vec3 pos, @Nullable Vector4f color, Vec3 speed, double begin, double end, IExecutable expression, double step, int age, @Nullable IExecutable speedExpression, double speedStep) {
        var ps = new ArrayList<Particle>();
        double x = pos.x, y = pos.y, z = pos.z;
        double vx = speed.x, vy = speed.y, vz = speed.z;
        var data = expression.getData();
        for (double t = begin; t <= end; t += step) {
            data.t = t;
            expression.invoke();
            double dx;
            double dy;
            double dz;
            if (polar) {
                dx = data.dis * Math.cos(data.s2) * Math.cos(data.s1);
                dy = data.dis * Math.sin(data.s2);
                dz = data.dis * Math.cos(data.s2) * Math.sin(data.s1);
            } else {
                dx = data.x;
                dy = data.y;
                dz = data.z;
            }
            if (color == null) {
                ps.add(createParticle(effect, x + dx, y + dy, z + dz, x, y, z, (float) data.cr, (float) data.cg, (float) data.cb, (float) data.alpha, vx, vy, vz, age, speedExpression, speedStep));
            } else {
                ps.add(createParticle(effect, x + dx, y + dy, z + dz, x, y, z, color.x, color.y, color.z, color.w, vx, vy, vz, age, speedExpression, speedStep));
            }
        }
        return ps.stream().filter(Objects::nonNull).toList();
    }

    public static List<Particle> genImage(ParticleOptions effect, double x, double y, double z, File file, double scaling, int xRotate, int yRotate, int zRotate, boolean flip, double[][] matrix, double dpb, double vx, double vy, double vz, int age, IExecutable speedExpression, double speedStep) throws IOException {
        var ps = new ArrayList<Particle>();
        var image = ImageUtil.readImage(file, scaling, true);
        int rows = image.getHeight();
        int cols = image.getWidth();
        var rotateFlipMat = getRotateFlipMat(xRotate, yRotate, zRotate, flip, rows, cols);
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < cols; ++col) {
                int pixel = image.getRGB(col, row);
                float alpha = (float) ((pixel & 0xff000000) >>> 24) / 255.0F;
                float red = (float) ((pixel & 0xff0000) >>> 16) / 255.0F;
                float green = (float) ((pixel & 0xff00) >>> 8) / 255.0F;
                float blue = (float) (pixel & 0xff) / 255.0F;
                double[][] pos = MatrixUtil.matDiv(MatrixUtil.matMul(rotateFlipMat, new int[][]{{col}, {row}, {0}, {1}}), dpb);
                if (matrix != null) pos = MatrixUtil.matMul(matrix, pos);
                double dx = pos[0][0];
                double dy = pos[1][0];
                double dz = pos[2][0];
                if (alpha != 0.0F)
                    ps.add(createParticle(effect, x + dx, y + dy, z + dz, x, y, z, red, green, blue, alpha, vx, vy, vz, age, speedExpression, speedStep));
            }
        }
        return ps.stream().filter(Objects::nonNull).toList();
    }

    public static List<Particle> genText(ParticleOptions effect, Vec3 startPos, Component text, double scaling, @Nullable IExecutable expression, double dpb, Vec3 speed, int age, @Nullable IExecutable speedExpression, double speedStep) {
        var ps = new ArrayList<Particle>();
        double x = startPos.x, y = startPos.y, z = startPos.z;
        double vx = speed.x, vy = speed.y, vz = speed.z;
        // 与 ParticleUtil.spawnTextParticle 同理：离屏字体绘制必须发生在渲染阶段，
        // 所以这里是「先返回空列表、渲染阶段再填充」的异步语义（上游是同步返回）。
        TextUtil.requestImage(text, (float) scaling, img -> {
            var data = expression != null ? expression.getData() : new ParticleStruct();
            int rows = img.getHeight();
            int cols = img.getWidth();
            for (int row = 0; row < rows; ++row) {
                for (int col = 0; col < cols; ++col) {
                    int pixel = img.getPixel(col, row);
                    float alpha = ARGB.alpha(pixel) / 255f;
                    float red = ARGB.red(pixel) / 255f;
                    float green = ARGB.green(pixel) / 255f;
                    float blue = ARGB.blue(pixel) / 255f;
                    double[][] pos = MatrixUtil.matDiv(new int[][]{{col}, {row}, {0}, {1}}, dpb);
                    data.x = pos[0][0];
                    data.y = pos[1][0];
                    data.z = pos[2][0];
                    data.cr = red;
                    data.cg = green;
                    data.cb = blue;
                    data.alpha = alpha;
                    data.vx = vx;
                    data.vy = vy;
                    data.vz = vz;
                    data.age = age;
                    if (expression != null) expression.invoke();
                    if (alpha != 0.0F)
                        ps.add(createParticleFromData(effect, x + data.x, y + data.y, z + data.z, x, y, z, speedExpression, speedStep, data));
                }
            }
        });
        // 与上游逐字对齐：`TextUtil.requestImage` 现在是同步出图，回调已在此行之前跑完，过滤 null 与上游一致
        return ps.stream().filter(Objects::nonNull).toList();
    }

    public static void bind(Particle particle, Supplier<Vec3> provider) {
        ((IParticle) particle).setBind(provider);
    }
}
