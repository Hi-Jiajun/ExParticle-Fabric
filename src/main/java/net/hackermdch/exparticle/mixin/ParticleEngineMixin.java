package net.hackermdch.exparticle.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 上游 {@code ParticleEngineMixin} 的一部分：无条件取消 {@code updateCount}，
 * 让 `/particle` 命令的粒子数配额计数不再生效（ExParticle 需要突破原版配额）。
 *
 * <p>另外两项上游行为在 1.21.10 已无对应物，故不移植：
 * <ul>
 *   <li>{@code maxParticleCount}：上游改的是 {@code ParticleEngine.tick()} 里统计粒子数的
 *       {@code EvictingQueue} 容量（调试用历史），1.21.10 的 {@code countParticles()} 改成
 *       实时求和，队列已不存在；
 *   <li>{@code maxParticleTickMillis}：上游只声明未使用。
 * </ul>
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "updateCount", at = @At("HEAD"), cancellable = true)
    private void exarticle$updateCount(ParticleLimit limit, int count, CallbackInfo ci) {
        ci.cancel();
    }
}
