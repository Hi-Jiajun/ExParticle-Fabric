package net.hackermdch.exparticle.command.particleex;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.hackermdch.exparticle.command.argument.SuggestDoubleArgumentType;
import net.hackermdch.exparticle.command.argument.SuggestIntegerArgumentType;
import net.hackermdch.exparticle.command.argument.SuggestStringArgumentType;
import net.hackermdch.exparticle.network.CustomParameterPayload;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.hackermdch.exparticle.compat.PacketDistributor;

public class CustomParameterCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, CommandBuildContext ctx) {
        var customNode = Commands.literal("custom-parameter");
        customNode.then(buildMode("normal", false, false, ctx));
        customNode.then(buildMode("polar", true, false, ctx));
        customNode.then(buildMode("tick", false, true, ctx));
        customNode.then(buildMode("tick-polar", true, true, ctx));
        parent.then(customNode);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMode(String literal, boolean polar, boolean tick, CommandBuildContext ctx) {
        return Commands.literal(literal)
                .then(Commands.argument("name", ParticleArgument.particle(ctx))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .then(buildMainChain(polar, tick))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildMainChain(boolean polar, boolean tick) {
        return Commands.argument("begin", SuggestDoubleArgumentType.doubleArg(-Double.MAX_VALUE, Double.MAX_VALUE, -10.0))
                .then(Commands.argument("end", SuggestDoubleArgumentType.doubleArg(-Double.MAX_VALUE, Double.MAX_VALUE, 10.0))
                        .then(Commands.argument("expression", SuggestStringArgumentType.argument("null", "\"x,y=t,sin(t); size=cos(t)+1\"", "\"vx=sin(t); vy=cos(t)\""))
                                .executes(createExecuteCommand(polar, tick, false, false, false))
                                .then(Commands.argument("step", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0), Double.MAX_VALUE, 0.1))
                                        .executes(createExecuteCommand(polar, tick, true, false, false))
                                        .then(buildOptionalChain(polar, tick)))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildOptionalChain(boolean polar, boolean tick) {
        ArgumentBuilder<CommandSourceStack, ?> chain;
        if (tick) {
            chain = Commands.argument("cpt", SuggestIntegerArgumentType.integer(1, Integer.MAX_VALUE, 10))
                    .executes(createExecuteCommand(polar, tick, true, true, false))
                    .then(buildSpeedChain(polar, tick));
        } else chain = buildSpeedChain(polar, tick);
        return chain;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildSpeedChain(boolean polar, boolean tick) {
        return Commands.argument("speedExpression", SuggestStringArgumentType.argument("null", "\"(vx,vy,vz)=(x,y,z,1)*(rotateDeg(0,10,0)-identity(4))\""))
                .executes(createExecuteCommand(polar, tick, true, tick, true, false))
                .then(Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0), Double.MAX_VALUE, 1.0))
                        .executes(createExecuteCommand(polar, tick, true, tick, true, true))
                        .then(Commands.argument("group", SuggestStringArgumentType.argument("null"))
                                .executes(createExecuteCommand(polar, tick, true, tick, true, true, true))));
    }

    private static Command<CommandSourceStack> createExecuteCommand(boolean polar, boolean tick, boolean... flags) {
        return context -> {
            ParticleOptions effect = ParticleArgument.getParticle(context, "name");
            Vec3 pos = Vec3Argument.getVec3(context, "pos");
            double begin = DoubleArgumentType.getDouble(context, "begin");
            double end = DoubleArgumentType.getDouble(context, "end");
            String expression = StringArgumentType.getString(context, "expression");
            double step = flags.length > 0 && flags[0] ? DoubleArgumentType.getDouble(context, "step") : 0.1;
            int cpt = flags.length > 1 && flags[1] ? IntegerArgumentType.getInteger(context, "cpt") : (tick ? 10 : 0);
            String speedExpression = flags.length > 2 && flags[2] ? StringArgumentType.getString(context, "speedExpression") : null;
            double speedStep = flags.length > 3 && flags[3] ? DoubleArgumentType.getDouble(context, "speedStep") : 1.0;
            String group = flags.length > 4 && flags[4] ? StringArgumentType.getString(context, "group") : null;

            PacketDistributor.sendToPlayersInDimension(context.getSource().getLevel(),
                    new CustomParameterPayload(polar, tick, effect, pos, begin, end, expression, step, cpt, speedExpression, speedStep, group));
            return 1;
        };
    }
}