package net.hackermdch.exparticle.compat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 移植的兼容层，对应上游 NeoForge 的
 * {@code net.neoforged.neoforge.network.PacketDistributor}。
 *
 * <p>上游只在服务端命令里用了一个方法：把 payload 发给该维度的所有玩家。
 */
public final class PacketDistributor {
    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** 对应 NeoForge 的 {@code sendToPlayer}：只发给某一个玩家（用于查询回执）。 */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
