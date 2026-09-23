package net.hackermdch.exparticle;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.hackermdch.exparticle.command.ParticleExCommand;
import net.hackermdch.exparticle.command.argument.CustomArgumentTypes;
import net.hackermdch.exparticle.network.Networking;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 通用入口（Fabric 移植版）。
 *
 * <p>上游是 NeoForge：{@code @Mod} + {@code IEventBus} + {@code RegisterCommandsEvent}，
 * 并用 {@code sun.misc.Unsafe} 取 {@code IMPL_LOOKUP} 反射改 {@code ModuleClassLoader}
 * （为了把 JavaCV 挂进模块层）。Fabric 没有模块层、也不该用 Unsafe，所以这一层重写成
 * ModInitializer：配置 → payload 注册 → 自定义参数类型注册 → 服务端命令注册。
 */
public class ExParticle implements ModInitializer {
    public static final String MOD_ID = "exparticle";
    public static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        try {
            ExParticleConfig.init();
        } catch (Exception e) {
            LOGGER.error("Failed to init config", e);
        }
        Networking.register();
        CustomArgumentTypes.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            ParticleExCommand.register(dispatcher, registryAccess));
    }
}
