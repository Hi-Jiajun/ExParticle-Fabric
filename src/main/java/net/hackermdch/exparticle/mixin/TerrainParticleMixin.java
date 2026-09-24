package net.hackermdch.exparticle.mixin;

import net.hackermdch.exparticle.util.IParticle;
import net.hackermdch.exparticle.util.SpriteAccessor;
import net.minecraft.client.particle.TerrainParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 {@code minecraft:block} 粒子在**被 ExParticle 的 size 表达式接管**时画**整张 sprite**。
 *
 * <p>原版 {@code TerrainParticle} 只拿 sprite 的 1/4×1/4 随机子块（{@code uo/vo}），
 * 那是给"方块被打碎"用的。对 ExParticle 来说这有两个后果：
 * <ul>
 *   <li>一张贴图想当"整幅画面"用时，每颗粒子只显示四分之一块；</li>
 *   <li>子块是随机的，同一张贴图的多颗粒子拼不到一起。</li>
 * </ul>
 *
 * <p>因此：**只要这个粒子被 size 接管过**（{@link IParticle#getCustomSize()} 非 NaN，也就是
 * 走 ExParticle 命令生成、表达式里写了 {@code size=}），UV 就换成整张 sprite。
 * 判定条件刻意选"有没有被 ExParticle 接管"，这样原版方块碎裂粒子（不写 size）不受影响。
 *
 * <p>用途：nbmachina 的 STYX HELIX 视觉层要把专辑封面/标题当成**一整块贴图**画出来
 * （一颗粒子 = 一整张图），而不是"一像素一颗粒子"的点阵——后者在引擎里会被位置抖动
 * 打散（2026-09-24 实测：dpb=2、size 恰好铺满时，画面变成彩纸屑）。
 */
@Mixin(TerrainParticle.class)
public abstract class TerrainParticleMixin implements IParticle {
    private SpriteAccessor exarticle$spriteAccessor() {
        return (SpriteAccessor) this;
    }

    @Inject(method = "getU0", at = @At("HEAD"), cancellable = true)
    private void exarticle$fullSpriteU0(CallbackInfoReturnable<Float> cir) {
        if (!Double.isNaN(getCustomSize())) {
            cir.setReturnValue(exarticle$spriteAccessor().exarticle$sprite().getU(0.0F));
        }
    }

    @Inject(method = "getU1", at = @At("HEAD"), cancellable = true)
    private void exarticle$fullSpriteU1(CallbackInfoReturnable<Float> cir) {
        if (!Double.isNaN(getCustomSize())) {
            cir.setReturnValue(exarticle$spriteAccessor().exarticle$sprite().getU(1.0F));
        }
    }

    @Inject(method = "getV0", at = @At("HEAD"), cancellable = true)
    private void exarticle$fullSpriteV0(CallbackInfoReturnable<Float> cir) {
        if (!Double.isNaN(getCustomSize())) {
            cir.setReturnValue(exarticle$spriteAccessor().exarticle$sprite().getV(0.0F));
        }
    }

    @Inject(method = "getV1", at = @At("HEAD"), cancellable = true)
    private void exarticle$fullSpriteV1(CallbackInfoReturnable<Float> cir) {
        if (!Double.isNaN(getCustomSize())) {
            cir.setReturnValue(exarticle$spriteAccessor().exarticle$sprite().getV(1.0F));
        }
    }
}
