package net.hackermdch.exparticle.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;

import java.util.function.BiConsumer;

/**
 * Fabric 移植的兼容层，对应上游 NeoForge 的
 * {@code net.neoforged.neoforge.network.registration.PayloadRegistrar}。
 *
 * <p>上游 21 个 payload 只调用 {@code registrar.playBidirectional(TYPE, CODEC, X::handle)}，
 * 语义是「注册成双向 payload，两个方向共用同一个 codec 和 handler」。
 * Fabric 侧对应：{@code PayloadTypeRegistry.playS2C()/playC2S()} 注册类型 +
 * {@code Client/ServerPlayNetworking.registerGlobalReceiver} 注册接收端。
 */
public final class PayloadRegistrar {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends CustomPacketPayload> void playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, IPayloadContext> handler) {
        var streamCodec = (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
        PayloadTypeRegistry.playS2C().register(type, streamCodec);
        PayloadTypeRegistry.playC2S().register(type, streamCodec);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                handler.accept(payload, task -> context.client().execute(task)));
        }
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
            handler.accept(payload, task -> {
                MinecraftServer server = context.player().level().getServer();
                if (server != null) server.execute(task);
                else task.run();
            }));
    }
}
