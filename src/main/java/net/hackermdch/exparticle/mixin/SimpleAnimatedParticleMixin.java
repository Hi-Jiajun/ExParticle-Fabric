package net.hackermdch.exparticle.mixin;

import net.hackermdch.exparticle.util.IParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code SimpleAnimatedParticle}（end_rod / firefly 这类）的两处上游补丁：
 *
 * <ol>
 *   <li>它在 tick 里按存活时间改写 alpha，会把受控粒子自己设的 alpha 冲掉 —— 在那一句
 *       {@code setAlpha} 处取消；
 *   <li>它把 {@code getLightColor} 覆写成全亮，遮蔽了 {@code ParticleMixin} 里的自定义亮度 ——
 *       这里在 HEAD 处改回 {@code super.getLightColor(partialTick)}
 *       （即 {@code Particle.getLightColor}，会正常经过我们注入的那层，受控粒子才能用 light 表达式）。
 * </ol>
 *
 * <p>与上游的唯一差别是继承的类名：1.21.1 的 {@code TextureSheetParticle} 在 1.21.10
 * 叫 {@code SingleQuadParticle}，构造签名随之从 (level,x,y,z) 变成 (level,x,y,z,sprite)。
 * 这个构造只用于满足编译，不会被 Mixin 合并到目标类（上游同款写法）。
 */
@Mixin(SimpleAnimatedParticle.class)
public abstract class SimpleAnimatedParticleMixin extends SingleQuadParticle {
    protected SimpleAnimatedParticleMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    @Inject(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/SimpleAnimatedParticle;setAlpha(F)V"),
            cancellable = true)
    private void exarticle$onSetAlpha(CallbackInfo ci) {
        if (((IParticle) this).isManaged()) ci.cancel();
    }

    @Inject(method = "getLightColor", at = @At("HEAD"), cancellable = true)
    private void exarticle$getLightColor(float partialTick, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(super.getLightColor(partialTick));
    }
}
