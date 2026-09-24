package net.hackermdch.exparticle.util;

import net.minecraft.client.Minecraft;

/**
 * 镜头参照（每客户端刻刷新一次）：把「玩家眼位 + 水平前/右方向」暴露给表达式，
 * 让效果能**锁在屏幕上**（跟着镜头走），而不是钉在世界坐标里被飞行的镜头甩开。
 *
 * <p>对应 {@link ParticleStruct} 里的只读变量：
 * <pre>
 * px, py, pz  玩家眼位（世界坐标）
 * fx, fz      视线水平前方向（单位向量，已去掉俯仰）
 * rx, rz      视线水平右方向（单位向量）
 * yaw         偏航角（弧度，MC 约定：0 = +z，顺时针为正）
 * </pre>
 *
 * <p>典型用法（把粒子锁在「眼前 D 格、画面正中」）：
 * {@code vx=(px+fx*D)-(cx+x); vy=py-(cy+y); vz=(pz+fz*D)-(cz+z)}。
 *
 * <p>注意：这是**刻级**参照（粒子位置每刻更新一次、镜头逐帧插值），飞行速度很快时
 * 屏幕上的位置会有约「一刻位移」的抖动，慢速运镜看不出来。
 */
public final class CameraRef {
    public static double x, y, z;
    public static double fx, fz;
    public static double rx, rz;
    public static double yaw;
    private static boolean valid;

    private CameraRef() {
    }

    /** 客户端每刻开头调用一次（见 ExParticleClient）。 */
    public static void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            valid = false;
            return;
        }
        var eye = player.getEyePosition();
        var yawRad = Math.toRadians(player.getYRot());
        x = eye.x;
        y = eye.y;
        z = eye.z;
        fx = -Math.sin(yawRad);
        fz = Math.cos(yawRad);
        rx = -fz;
        rz = fx;
        yaw = yawRad;
        valid = true;
    }

    public static boolean valid() {
        return valid;
    }

    /** 把当前参照写进表达式数据（只在有效时写，否则保留上一刻的值）。 */
    public static void writeInto(ParticleStruct data) {
        if (!valid) return;
        data.px = x;
        data.py = y;
        data.pz = z;
        data.fx = fx;
        data.fz = fz;
        data.rx = rx;
        data.rz = rz;
        data.yaw = yaw;
    }
}
