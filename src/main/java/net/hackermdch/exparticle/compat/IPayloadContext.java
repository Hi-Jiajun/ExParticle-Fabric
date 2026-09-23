package net.hackermdch.exparticle.compat;

/**
 * Fabric 移植的兼容层，对应上游 NeoForge 的
 * {@code net.neoforged.neoforge.network.handling.IPayloadContext}。
 *
 * <p>上游所有 payload 的 {@code handle()} 都只用到 {@code context.enqueueWork(Runnable)}，
 * 所以这里只保留这一个方法：在对应线程上执行任务
 * （客户端 = 客户端主线程，服务端 = 服务端主线程）。
 */
public interface IPayloadContext {
    void enqueueWork(Runnable task);
}
