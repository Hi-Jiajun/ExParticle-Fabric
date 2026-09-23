package net.hackermdch.exparticle.util;

import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;

public class CustomParticleBuilder {
    public static Particle buildParticle(ParticleOptions effect, double x, double y, double z, double cx, double cy, double cz, String speedExpression, double speedStep, String group, ParticleStruct data) {
        var particle = ParticleUtil.spawnParticle(effect, x, y, z, cx, cy, cz, (float) data.cr, (float) data.cg, (float) data.cb, (float) data.alpha, data.vx, data.vy, data.vz, (int) data.age, speedExpression, speedStep, group);
        if (particle != null) {
            ((IParticle) particle).setCustomSize(data.size);
            ((IParticle) particle).setCustomLight(data.light);
            ((IParticle) particle).setGravity((float) data.gravity);
            ((IParticle) particle).setFriction((float) data.friction);
        }
        return particle;
    }
}