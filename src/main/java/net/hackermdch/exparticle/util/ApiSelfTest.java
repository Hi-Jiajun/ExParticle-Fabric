package net.hackermdch.exparticle.util;

import net.hackermdch.exparticle.api.ExParticleApi;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.io.File;
import java.util.List;

/**
 * `ExParticleApi` 的自检（诊断用，`-Dexparticle.selftest.api=true` 时由客户端 tick 跑一次）。
 *
 * <p>为什么要它：命令族都已实机验证，但**API 是给别的 mod 调用的**，没有任何命令会走它；
 * 这些方法又是「只造粒子、不入引擎」的语义（`ParticleEngine.makeParticle` + 手动填字段），
 * 与命令路径不是同一条代码路径。这里逐条调用并把返回列表长度打进日志，和命令侧的实测数量交叉核对
 * （例如 `genText("AB",3)` 应为 342、`genImage(test.png)` 应为 256、`genCond` 应与
 * `custom-conditional "abs(x)==1|abs(y)==1|abs(z)==1"` 的 98 一致）。
 */
public final class ApiSelfTest {
    private ApiSelfTest() {
    }

    public static void run(Minecraft client) {
        var level = client.level;
        if (level == null) {
            return;
        }
        var random = RandomSource.create(12345L);
        var pos = new Vec3(0.0, -55.0, 0.0);
        var color = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        var zero = Vec3.ZERO;
        log("start");
        try {
            var single = ExParticleApi.createParticle(ParticleTypes.END_ROD, 0.0, -55.0, 0.0, 0.0, -55.0, 0.0,
                1.0F, 1.0F, 1.0F, 1.0F, 0.0, 0.0, 0.0, 600, null, 1.0);
            log("createParticle -> " + (single != null ? "Particle#" + Integer.toHexString(single.hashCode()) : "null"));
            ExParticleApi.bind(single, () -> new Vec3(1.0, -55.0, 2.0));
            log("bind -> ok");
            report("genNormal(count=100)", ExParticleApi.genNormal(ParticleTypes.END_ROD, random, pos, color, zero,
                new Vec3(1.0, 1.0, 1.0), 100, 600, null, 1.0), 100);
            var condExe = ExpressionUtil.parse("abs(x)==1|abs(y)==1|abs(z)==1");
            report("genCond(unit shell)", ExParticleApi.genCond(ParticleTypes.END_ROD, pos, color, zero,
                new Vec3(1.0, 1.0, 1.0), condExe, 0.5, 600, null, 1.0), 98);
            var paramExe = ExpressionUtil.parse("dis=1.2;s1=t;s2=0");
            report("genParameter(polar=false)", ExParticleApi.genParameter(ParticleTypes.END_ROD, false, pos, color,
                zero, 0.0, 6.283, paramExe, 0.1, 600, null, 1.0), -1);
            report("genParameter(polar=true)", ExParticleApi.genParameter(ParticleTypes.END_ROD, true, pos, color,
                zero, 0.0, 6.283, paramExe, 0.1, 600, null, 1.0), -1);
            report("genImage(test.png)", ExParticleApi.genImage(ParticleTypes.END_ROD, 0.0, -55.0, 0.0,
                new File(ImageUtil.IMAGE_DIR, "test.png"), 1.0, 0, 0, 0, false, null, 10.0, 0.0, 0.0, 0.0, 600,
                null, 1.0), 256);
            report("genText(\"AB\",3)", ExParticleApi.genText(ParticleTypes.END_ROD, pos, Component.literal("AB"),
                3.0, null, 10.0, zero, 600, null, 1.0), 342);
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        log("done");
    }

    private static void report(String name, List<?> particles, int expected) {
        if (particles == null) {
            log(name + " -> null");
            return;
        }
        int nulls = 0;
        for (var p : particles) {
            if (p == null) {
                nulls++;
            }
        }
        var verdict = expected < 0 ? "" : (particles.size() == expected ? " ✓" : " ✗（期望 " + expected + "）");
        log(name + " -> size=" + particles.size() + " nulls=" + nulls + verdict);
    }

    private static void log(String message) {
        System.out.println("[exparticle] api self-test: " + message);
    }
}
