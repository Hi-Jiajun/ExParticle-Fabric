package net.hackermdch.exparticle.util;

import net.hackermdch.exparticle.ExParticle;

/** 日志小工具：避免工具类直接引用入口类时把日志实现拉进子加载器。 */
final class ExParticleLog {
    private ExParticleLog() {
    }

    static void warn(String message, Throwable throwable) {
        ExParticle.LOGGER.warn(message, throwable);
    }
}
