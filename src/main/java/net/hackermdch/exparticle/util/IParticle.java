package net.hackermdch.exparticle.util;

import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

public interface IParticle {
    void setExe(IExecutable var1);

    void setStep(double var1);

    double getCenterX();

    void setCenterX(double var1);

    double getCenterY();

    void setCenterY(double var1);

    double getCenterZ();

    void setCenterZ(double var1);

    void setCustomMove(boolean var1);

    void setStop(boolean var1);

    void customTick();

    void setManaged(boolean val);

    boolean isManaged();

    double getCustomSize();

    void setCustomSize(double size);

    void setCustomLight(double light);

    void setGravity(float gravity);

    void setFriction(float friction);

    void setBind(Supplier<Vec3> provider);

    // === Fabric 移植新增：颜色/alpha 访问 ===
    // 1.21.1 里 rCol/gCol/bCol/alpha 是 Particle 的字段（上游靠 access transformer 打开），
    // 1.21.10 把它们移到了 SingleQuadParticle。为了让「非四边形粒子」也能满足本接口，
    // ParticleMixin 提供空实现/默认值，SingleQuadParticleMixin 用真实字段覆盖。
    void setColorRGB(float red, float green, float blue);

    void setAlphaValue(float alpha);

    float getColorR();

    float getColorG();

    float getColorB();

    float getAlphaValue();

    // === Fabric 移植新增：sprite 取景窗（u0,u1,v0,v1，归一化）===
    // 用途（2026-09-24，nbmachina 开场）：**一颗粒子只画贴图的一块矩形**。
    //   · 一整张封面 = 4×4 个 tile 粒子，每颗写自己的 u0..v1 → 可以"按块溶解/按块长出"，
    //     而且全程 alpha=1（开光影时半透明板子暗部会整块消失，见上一条注释）；
    //   · 默认值 0,1,0,1 = 整张贴图，对老效果完全无影响。
    void setSpriteRegion(double u0, double u1, double v0, double v1);

    double getSpriteU0();

    double getSpriteU1();

    double getSpriteV0();

    double getSpriteV1();
}
