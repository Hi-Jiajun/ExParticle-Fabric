package net.hackermdch.exparticle.mixin;

import net.hackermdch.exparticle.ExParticleConfig;
import net.hackermdch.exparticle.util.IParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

/**
 * 逐粒子自定义 tick + 并行 tick 的挂载点（Fabric / 1.21.10 版）。
 *
 * <p>上游 1.21.1 挂在 {@code ParticleEngine.tickParticle / tickParticleList}；
 * 1.21.10 这个循环搬到了 {@code ParticleGroup}，所以：
 * <ul>
 *   <li>{@code @Redirect} 把原版 {@code Particle.tick()} 换成 {@code customTick()}（语义与上游一致）；
 *   <li>{@code tickParticles} HEAD 注入实现配置项 {@code ParallelParticleUpdate}
 *       （上游是 {@code tickParticleList(Collection)} 的并行 + removeIf + cancel）。
 * </ul>
 */
@Mixin(ParticleGroup.class)
public abstract class ParticleGroupMixin {
    @Shadow
    protected Queue<Particle> particles;

    @Shadow
    private void tickParticle(Particle particle) {
    }

    @Redirect(method = "tickParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At(value = "INVOKE", target = "net.minecraft.client.particle.Particle.tick()V"))
    private void exarticle$customTick(Particle particle) {
        ((IParticle) particle).customTick();
    }

    @Inject(method = "tickParticles", at = @At("HEAD"), cancellable = true)
    private void exarticle$parallelTick(CallbackInfo ci) {
        if (!ExParticleConfig.config.ParallelParticleUpdate) {
            return;
        }
        particles.parallelStream().forEach(this::tickParticle);
        particles.removeIf(particle -> !particle.isAlive());
        ci.cancel();
    }
}
