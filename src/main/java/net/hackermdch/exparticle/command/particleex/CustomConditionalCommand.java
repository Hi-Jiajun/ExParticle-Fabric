package net.hackermdch.exparticle.command.particleex;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.hackermdch.exparticle.command.argument.Range3ArgumentType;
import net.hackermdch.exparticle.command.argument.SuggestDoubleArgumentType;
import net.hackermdch.exparticle.command.argument.SuggestStringArgumentType;
import net.hackermdch.exparticle.network.CustomConditionalPayload;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.hackermdch.exparticle.compat.PacketDistributor;

public class CustomConditionalCommand {
    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, CommandBuildContext ctx) {
        parent.then(Commands.literal("custom-conditional")
                .then(Commands.argument("name", ParticleArgument.particle(ctx))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .then(Commands.argument("attr", SuggestStringArgumentType.argument("null", "\"vx=1; friction=0.8\"", "\"vx=1; friction=-0.5\""))
                                        .then(Commands.argument("range", Range3ArgumentType.range3())
                                                .then(Commands.argument("condition", SuggestStringArgumentType.argument("null", "\"y>0.25|y<-0.25\"", "\"dis>0.5&dis<1\"", "\"s1>0&s1<0.5&s2>0&dis<1\""))
                                                        .executes(execute(false, false, false, false))
                                                        .then(Commands.argument("step", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0), Double.MAX_VALUE, 0.1))
                                                                .executes(execute(true, false, false, false))
                                                                .then(Commands.argument("speedExpression", SuggestStringArgumentType.argument("null", "\"(vx,vy,vz)=(x,y,z,1)*(rotateDeg(0,10,0)-identity(4))\""))
                                                                        .executes(execute(true, true, false, false))
                                                                        .then(Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0), Double.MAX_VALUE, 1.0))
                                                                                .executes(execute(true, true, true, false))
                                                                                .then(Commands.argument("group", SuggestStringArgumentType.argument("null"))
                                                                                        .executes(execute(true, true, true, true))))))))))));
    }

    private static Command<CommandSourceStack> execute(boolean a, boolean b, boolean c, boolean d) {
        return context -> {
            var effect = ParticleArgument.getParticle(context, "name");
            var pos = Vec3Argument.getVec3(context, "pos");
            var attr = StringArgumentType.getString(context, "attr");
            var range = Range3ArgumentType.getRange3(context, "range");
            var cond = StringArgumentType.getString(context, "condition");
            var step = a ? DoubleArgumentType.getDouble(context, "step") : 0.1;
            var speedExpression = b ? StringArgumentType.getString(context, "speedExpression") : null;
            var speedStep = c ? DoubleArgumentType.getDouble(context, "speedStep") : 1.0;
            var group = d ? StringArgumentType.getString(context, "group") : null;
            PacketDistributor.sendToPlayersInDimension(context.getSource().getLevel(), new CustomConditionalPayload(effect, pos, attr, range, cond, step, speedExpression, speedStep, group));
            return 1;
        };
    }
}