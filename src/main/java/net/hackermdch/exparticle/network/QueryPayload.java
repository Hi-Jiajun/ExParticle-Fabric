package net.hackermdch.exparticle.network;

import net.hackermdch.exparticle.compat.IPayloadContext;
import net.hackermdch.exparticle.compat.PayloadRegistrar;
import net.hackermdch.exparticle.util.ClientMessageUtil;
import net.hackermdch.exparticle.util.GlobalVariableUtil;
import net.hackermdch.exparticle.util.UserFunctionUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static net.hackermdch.exparticle.ExParticle.MOD_ID;

/**
 * 查询回执（S2C）：Fabric 移植新增，用来替代上游的两个**客户端命令**
 * {@code /particlex global-variable peek <name>} 与 {@code /particlex user-function list}。
 *
 * <p>为什么不能照搬：Fabric 里客户端命令同名 root 会**遮蔽**服务端命令树（实测报错停在位置 10），
 * 而上游在 NeoForge 上两棵树是合并的。所以这里把这两个查询做成服务端命令 + 回执 payload：
 * 命令路径与上游完全一致，输出仍是「发起命令的那个客户端的本地数据」——
 * 因为全局变量/用户函数本来就是通过 payload 下发到客户端的，服务端命令只是帮你把它们读出来。
 */
public class QueryPayload implements CustomPacketPayload {
    private static final Type<QueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "query"));
    private static final StreamCodec<RegistryFriendlyByteBuf, QueryPayload> CODEC = StreamCodec.ofMember(QueryPayload::write, QueryPayload::new);

    private final boolean userFunctionList;
    private final String name;

    /** 全局变量查询。 */
    public QueryPayload(String name) {
        this(false, name);
    }

    private QueryPayload(boolean userFunctionList, String name) {
        this.userFunctionList = userFunctionList;
        this.name = name;
    }

    /** 用户函数列表查询。 */
    public static QueryPayload userFunctions() {
        return new QueryPayload(true, "");
    }

    private QueryPayload(RegistryFriendlyByteBuf buf) {
        this.userFunctionList = buf.readBoolean();
        this.name = buf.readUtf();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(userFunctionList);
        buf.writeUtf(name);
    }

    private void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (userFunctionList) {
                UserFunctionUtil.list();
            } else {
                ClientMessageUtil.addChatMessage(Component.literal(GlobalVariableUtil.peek(name)));
            }
        });
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playBidirectional(TYPE, CODEC, QueryPayload::handle);
    }
}
