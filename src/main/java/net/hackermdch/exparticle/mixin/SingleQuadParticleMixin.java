package net.hackermdch.exparticle.mixin;

import net.hackermdch.exparticle.util.IParticle;
import net.hackermdch.exparticle.util.SpriteAccessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 四边形粒子（原版绝大多数粒子）的尺寸与颜色挂载点。
 *
 * <p>1.21.1 → 1.21.10 的变化：{@code rCol/gCol/bCol/alpha} 由 {@code Particle}
 * 移到了 {@code SingleQuadParticle}，所以颜色/alpha 的读写在这里实现
 * （ParticleMixin 里只有空实现），尺寸覆盖仍然注入 {@code getQuadSize}。
 */
@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin implements IParticle, SpriteAccessor {
    @Shadow
    protected TextureAtlasSprite sprite;

    @Shadow
    protected float rCol;
    @Shadow
    protected float gCol;
    @Shadow
    protected float bCol;
    @Shadow
    protected float alpha;

    @Shadow
    protected abstract void setAlpha(float alpha);

    @Inject(method = "getQuadSize", at = @At("HEAD"), cancellable = true)
    private void exarticle$getQuadSize(float scaleFactor, CallbackInfoReturnable<Float> cir) {
        var custom = getCustomSize();
        if (!Double.isNaN(custom)) cir.setReturnValue((float) custom);
    }

    @Override
    public void setColorRGB(float red, float green, float blue) {
        rCol = red;
        gCol = green;
        bCol = blue;
    }

    @Override
    public void setAlphaValue(float alpha) {
        setAlpha(alpha);
    }

    @Override
    public float getColorR() {
        return rCol;
    }

    @Override
    public float getColorG() {
        return gCol;
    }

    @Override
    public float getColorB() {
        return bCol;
    }

    @Override
    public float getAlphaValue() {
        return alpha;
    }

    /** 供子类 mixin（TerrainParticleMixin 等）取 sprite：见 {@link SpriteAccessor} 的说明。 */
    @Override
    public TextureAtlasSprite exarticle$sprite() {
        return sprite;
    }
}
