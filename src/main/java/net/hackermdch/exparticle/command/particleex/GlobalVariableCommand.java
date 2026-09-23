package net.hackermdch.exparticle.command.particleex;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.hackermdch.exparticle.command.argument.SuggestStringArgumentType;
import net.hackermdch.exparticle.network.GlobalVariablePayload;
import net.hackermdch.exparticle.network.QueryPayload;
import net.hackermdch.exparticle.util.ExpressionUtil;
import net.hackermdch.exparticle.util.GlobalVariableUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.hackermdch.exparticle.compat.PacketDistributor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

public class GlobalVariableCommand {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal("global-variable")
                .then(Commands.literal("define")
                        .then(Commands.argument("type", SuggestStringArgumentType.argument("int", "double", "quat")).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("value", StringArgumentType.string()).executes(
                                                (context) -> define(
                                                        context,
                                                        StringArgumentType.getString(context, "type"),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "value"))
                                        ))
                                )
                        ))
                .then(Commands.literal("undefine").then(Commands.argument("name", StringArgumentType.word()).executes(
                        (context) -> {
                            PacketDistributor.sendToPlayersInDimension(context.getSource().getLevel(), new GlobalVariablePayload(2, 0, StringArgumentType.getString(context, "name"), null));
                            return 1;
                        }
                )))
                // Fabric 移植：上游这条是「客户端命令」，而 Fabric 的客户端同名 root 会遮蔽服务端树，
                // 所以改成服务端命令 + 回执 payload：命令路径与上游一致，读到的仍是发起者客户端里的值。
                .then(Commands.literal("peek").then(Commands.argument("name", StringArgumentType.word()).executes(
                        (context) -> {
                            var player = context.getSource().getPlayer();
                            if (player != null) {
                                PacketDistributor.sendToPlayer(player, new QueryPayload(StringArgumentType.getString(context, "name")));
                            }
                            return 1;
                        }
                )))
        );
    }

    private static int define(CommandContext<CommandSourceStack> context, String type, String name, String value) {
        PacketDistributor.sendToPlayersInDimension(context.getSource().getLevel(), new GlobalVariablePayload(1, switch (type) {
            case "int" -> 1;
            case "double" -> 2;
            case "quat" -> 3;
            default -> throw new IllegalArgumentException();
        }, name, switch (type) {
            case "int" -> Integer.parseInt(value);
            case "double" -> Double.parseDouble(value);
            case "quat" -> ExpressionUtil.toQuaternion(value);
            default -> throw new IllegalArgumentException();
        }));
        return 1;
    }

    public static class Client {
        public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent) {
            parent.then(ClientCommandManager.literal("global-variable")
                    .then(ClientCommandManager.literal("peek").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(
                            (context) -> {
                                context.getSource().sendFeedback(Component.literal(GlobalVariableUtil.peek(StringArgumentType.getString(context, "name"))));
                                return 1;
                            }
                    )))
            );
        }
    }
}
