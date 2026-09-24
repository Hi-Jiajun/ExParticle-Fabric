package net.hackermdch.exparticle.util;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 取粒子正在用的 sprite。
 *
 * <p>存在的原因：{@code @Shadow} 只能影子**目标类自己声明**的字段，
 * 而 {@code sprite} 声明在 {@code SingleQuadParticle} 上、被 {@code TerrainParticle} 继承。
 * 直接影子会报
 * {@code @Shadow field field_62632 was not located in the target class}（2026-09-24 实测崩在启动）。
 * 所以改为：由 {@code SingleQuadParticleMixin} 实现本接口暴露 sprite，
 * 子类 mixin 通过 {@code (SpriteAccessor) this} 取用。
 */
public interface SpriteAccessor {
    TextureAtlasSprite exarticle$sprite();
}
