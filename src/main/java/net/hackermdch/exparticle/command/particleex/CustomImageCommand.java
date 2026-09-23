package net.hackermdch.exparticle.command.particleex;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.hackermdch.exparticle.command.argument.*;
import net.hackermdch.exparticle.network.CustomImagePayload;
import net.hackermdch.exparticle.util.ImageUtil;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.hackermdch.exparticle.compat.PacketDistributor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class CustomImageCommand {
    private static final MethodHandle constructor;

    static {
        try {
            constructor = MethodHandles.lookup().unreflectConstructor(CustomImagePayload.class.getConstructors()[0]);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, CommandBuildContext ctx) {
        parent.then(Commands.literal("custom-image")
                .then(Commands.argument("name", ParticleArgument.particle(ctx))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .then(Commands.argument("path", FileArgumentType.file(ImageUtil.IMAGE_DIR))
                                        .then(Commands.argument("scaling", SuggestDoubleArgumentType.doubleArg(0.0, Double.MAX_VALUE, 0.1))
                                                .then(Commands.argument("xRotate", RotateArgumentType.rotate())
                                                        .then(Commands.argument("yRotate", RotateArgumentType.rotate())
                                                                .then(Commands.argument("zRotate", RotateArgumentType.rotate())
                                                                        .then(Commands.argument("flip", FlipArgumentType.flip())
                                                                                .then(Commands.argument("dpb", SuggestDoubleArgumentType.doubleArg(0.0, Double.MAX_VALUE, 10.0))
                                                                                        .executes(execute(false, false, false, false))
                                                                                        .then(Commands.argument("attr", SuggestStringArgumentType.argument("null", "\"size=0.75\""))
                                                                                                .executes(execute(true, false, false, false))
                                                                                                .then(Commands.argument("speedExpression", SuggestStringArgumentType.argument("null", "\"vy=0.1\""))
                                                                                                        .executes(execute(true, true, false, false))
                                                                                                        .then(Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0), Double.MAX_VALUE, 1.0))
                                                                                                                .executes(execute(true, true, true, false))
                                                                                                                .then(Commands.argument("group", SuggestStringArgumentType.argument("null"))
                                                                                                                        .executes(execute(true, true, true, true))))))))))))))));
    }

    static CustomPacketPayload payload(CommandContext<CommandSourceStack> context, boolean a, boolean b, boolean c, boolean d, MethodHandle constructor) throws Throwable {
        var effect = ParticleArgument.getParticle(context, "name");
        var pos = Vec3Argument.getVec3(context, "pos");
        var path = StringArgumentType.getString(context, "path");
        var scaling = DoubleArgumentType.getDouble(context, "scaling");
        var xRotate = RotateArgumentType.getRotate(context, "xRotate");
        var yRotate = RotateArgumentType.getRotate(context, "yRotate");
        var zRotate = RotateArgumentType.getRotate(context, "zRotate");
        var flip = FlipArgumentType.getFlip(context, "flip");
        var dpb = DoubleArgumentType.getDouble(context, "dpb");
        var attrExpression = a ? StringArgumentType.getString(context, "attr") : null;
        var speedExpression = b ? StringArgumentType.getString(context, "speedExpression") : null;
        var speedStep = c ? DoubleArgumentType.getDouble(context, "speedStep") : 1.0;
        var group = d ? StringArgumentType.getString(context, "group") : null;
        if (flip == 2) zRotate += 2;
        return (CustomPacketPayload) constructor.invoke(effect, pos, path, scaling, xRotate, yRotate, zRotate, flip, dpb, attrExpression, speedExpression, speedStep, group);
    }

    private static Command<CommandSourceStack> execute(boolean a, boolean b, boolean c, boolean d) {
        return context -> {
            try {
                PacketDistributor.sendToPlayersInDimension(context.getSource().getLevel(), payload(context, a, b, c, d, constructor));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            return 1;
        };
    }
}