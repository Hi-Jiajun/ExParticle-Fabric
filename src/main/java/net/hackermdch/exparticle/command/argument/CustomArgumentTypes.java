package net.hackermdch.exparticle.command.argument;

import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;

import static net.hackermdch.exparticle.ExParticle.MOD_ID;

/**
 * 自定义命令参数类型注册（Fabric 版）。
 *
 * <p>上游用 NeoForge 的 {@code DeferredRegister<ArgumentTypeInfo<?,?>>} +
 * {@code ArgumentTypeInfos.registerByClass}。Fabric 侧由
 * {@code ArgumentTypeRegistry.registerArgumentType} 一次完成同样两件事
 * （写类→info 映射 + 把 info 注册进 {@code BuiltInRegistries.COMMAND_ARGUMENT_TYPE}），
 * 这样多人游戏下客户端也能同步到参数类型。
 */
public class CustomArgumentTypes {
    public static void register() {
        ArgumentTypeRegistry.registerArgumentType(id("color_rgb_a"), Color4ArgumentType.class,
            SingletonArgumentInfo.contextFree(Color4ArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("file"), FileArgumentType.class,
            new FileArgumentType.Info());
        ArgumentTypeRegistry.registerArgumentType(id("flip"), FlipArgumentType.class,
            SingletonArgumentInfo.contextFree(FlipArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("group"), GroupArgumentType.class,
            SingletonArgumentInfo.contextFree(GroupArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("group_change"), GroupChangeTypeArgumentType.class,
            SingletonArgumentInfo.contextFree(GroupChangeTypeArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("range3f"), Range3ArgumentType.class,
            SingletonArgumentInfo.contextFree(Range3ArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("rotate"), RotateArgumentType.class,
            SingletonArgumentInfo.contextFree(RotateArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("speed3f"), Speed3ArgumentType.class,
            SingletonArgumentInfo.contextFree(Speed3ArgumentType::new));
        ArgumentTypeRegistry.registerArgumentType(id("suggest_double"), SuggestDoubleArgumentType.class,
            new SuggestDoubleArgumentType.Info());
        ArgumentTypeRegistry.registerArgumentType(id("suggest_int"), SuggestIntegerArgumentType.class,
            new SuggestIntegerArgumentType.Info());
        ArgumentTypeRegistry.registerArgumentType(id("suggest_string"), SuggestStringArgumentType.class,
            new SuggestStringArgumentType.Info());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
