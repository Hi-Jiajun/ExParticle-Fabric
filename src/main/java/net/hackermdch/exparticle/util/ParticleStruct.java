package net.hackermdch.exparticle.util;

public class ParticleStruct {
    public final double PI = Math.PI;
    public final double E = Math.E;
    public double x;
    public double y;
    public double z;
    public double s1;
    public double s2;
    public double dis;
    public double t;
    public double size = 1.0;
    public double cr = 1.0;
    public double cg = 1.0;
    public double cb = 1.0;
    public double alpha = 1.0;
    public double light = 1.0;
    public double vx = 0.0;
    public double vy = 0.0;
    public double vz = 0.0;
    public double gravity = 0.0;
    public double friction = 1.0;
    public double age = 0.0;
    public double cx;
    public double cy;
    public double cz;
    public double dx;
    public double dy;
    public double dz;
    public double ds1;
    public double ds2;
    public double ddis;
    public double destroy;
    // ── 镜头参照（每刻由 CameraRef 写入，见该类说明）──
    /** 玩家眼位（世界坐标）。 */
    public double px;
    public double py;
    public double pz;
    /** 视线水平前方向（单位向量，已去掉俯仰）。 */
    public double fx;
    public double fz;
    /** 视线水平右方向（单位向量）。 */
    public double rx;
    public double rz;
    /** 偏航角（弧度）。 */
    public double yaw;
}
