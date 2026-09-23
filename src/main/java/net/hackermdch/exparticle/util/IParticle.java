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
}
