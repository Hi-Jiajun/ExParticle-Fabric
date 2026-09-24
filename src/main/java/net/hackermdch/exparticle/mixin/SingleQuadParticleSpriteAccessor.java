package net.hackermdch.exparticle.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 {@code SingleQuadParticle.sprite} 的**独立访问器 mixin**。
 *
 * <p>为什么不像第一版那样把 {@code SpriteAccessor} 接口 + {@code @Shadow} 字段挂在
 * {@code SingleQuadParticleMixin} 上：2026-09-24 二分实测——那样做之后
 * **end_rod 的 image 粒子整段不再渲染**（同一批命令、同一会话，摘掉补丁就恢复），
 * 而清晰板（block + E4）又必须要能拿到整张 sprite。改成独立的 {@code @Accessor}
 * 接口后，主 mixin 不再声明字段、不再实现额外接口，两个功能互不干扰。
 */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleSpriteAccessor {
    @Accessor("sprite")
    TextureAtlasSprite exarticle$sprite();
}
