package net.hackermdch.exparticle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.hackermdch.exparticle.util.JavaCvSupport;
import net.hackermdch.exparticle.util.ParticleUtil;
import net.hackermdch.exparticle.util.TextUtil;

/**
 * 客户端入口（Fabric 移植版）。
 *
 * <p>上游这里做的事是把 JavaCV 塞进 NeoForge 的模块层；Fabric 没有那套机制，
 * 本移植第一版**不接视频功能**（{@code /particlex video*} 会提示 JavaCV 不可用），
 * 其余效果（parameter/polar/conditional/image/text/rgba/自定义）全部保留。
 *
 * <p>另一处移植差异：上游在 NeoForge 上同时注册「服务端 /particlex」与
 * 「客户端 /particlex（仅 global-variable peek / user-function list）」，NeoForge 会合并两棵树。
 * Fabric 的客户端命令**优先级更高且不合并**：只要客户端注册了同名 root，聊天里就只看得到
 * 客户端那棵子树（实测报错正好停在位置 10，即 /particlex 之后）。因此这里不注册客户端 root；
 * 那两个查询改成服务端命令 + {@code QueryPayload} 回执，命令路径与输出与上游一致。
 */
public class ExParticleClient implements ClientModInitializer {
    /** 临时自检：本次进程只跑一次（窗口无法操作时用它验证 text 管线：CPU 光栅 + GPU 离屏对照）。 */
    private static boolean selfTestDone;
    /** 命令自驱动自检：把所有同族命令变体过一遍（不依赖窗口输入）。 */
    private static final String[] SELF_TEST_COMMANDS = {
        "particlex normal minecraft:end_rod ~ ~1 ~ 1 1 1 1 0 0 0 1 1 1 30 600 \"null\" 1 mygrp",
        "particlex image-matrix minecraft:end_rod ~ ~1 ~ \"test.png\" 1 \"(x,y,z)=(x,y,z,1)*rotate(0,90,0)\" 10 0 0 0 600",
        "particlex video-matrix minecraft:end_rod ~ ~1 ~ \"test.mp4\" 0.5 \"(x,y,z)=(x,y,z,1)*rotate(0,90,0)\" 10 0 0 0 600",
        "particlex custom-parameter minecraft:end_rod ~ ~1 ~ 0 6.283 \"dis=1.2;s1=t;s2=0\" 0.1 200 600",
        "particlex custom-image minecraft:end_rod ~ ~1 ~ \"test.png\" 1 0 0 0 not 10 \"size=1;cr,cg,cb,alpha=1,1,1,1;age=600\"",
        "particlex custom-conditional minecraft:end_rod ~ ~1 ~ \"size=1;age=600\" 1 1 1 \"abs(x)==1|abs(y)==1|abs(z)==1\" 0.5",
        "particlex tick-parameter minecraft:end_rod ~ ~1 ~ 1 1 1 1 0 6.283 \"dis=1;s1=t;s2=0\" 0.1 50 600",
        "particlex rgba-tick-polar-parameter minecraft:end_rod ~ ~1 ~ 0 0 0 0 6.283 \"dis=1;s1=t;s2=0;cr=1;cg=0.5;cb=0;alpha=1\" 0.1 50 600",
        "particlex text minecraft:end_rod ~ ~1 ~ \"AB\" 3 \"null\" 10 0 0 0 600",
        "particlex group change 1 mygrp \"(vx,vy,vz)=(0,0.05,0)\"",
        "particlex functions"
    };
    private static int selfTestTick = -1;
    private static int selfTestIndex;
    private static int gpuProbeTick;

    public static boolean hasJavaCV() {
        return JavaCvSupport.available();
    }

    @Override
    public void onInitializeClient() {
        // 提前探测 gamedir/javacv：有 jar 就把桥接类挂到子加载器上
        JavaCvSupport.init();
        ClientTickEvents.START_CLIENT_TICK.register(client -> ParticleUtil.onStartClientTick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ParticleUtil.onEndClientTick());
        // text* 命令的离屏字体绘制由 GuiRendererMixin 在 GUI 执行阶段驱动（见该 mixin 的说明）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 测试通道默认关闭，发布版不启用（见 -Dexparticle.testChannel）
            TestCommandChannel.tick(client);
            // GPU 重复实验：每秒跑一次 GPU 自建管线，用成功/失败模式找控制变量
            if (Boolean.getBoolean("exparticle.gpuText") && client.level != null && ++gpuProbeTick % 20 == 0) {
                TextUtil.probeGpu(net.minecraft.network.chat.Component.literal("AB"), 3.0F);
            }
            // 默认关闭；需要诊断时用 -Dexparticle.selftest=true 启动（用于无窗口操作时验证 text 管线）
            if (!selfTestDone && client.level != null
                && (Boolean.getBoolean("exparticle.selftest") || Boolean.getBoolean("exparticle.gpuText"))) {
                selfTestDone = true;
                var sample = net.minecraft.network.chat.Component.literal("AB");
                // 默认出图路径（现在是 GPU 离屏光栅化）
                TextUtil.requestImage(sample, 3.0F, image -> System.out.println("[exparticle] text self-test: 默认路径 "
                    + image.getWidth() + "x" + image.getHeight() + " " + TextUtil.stats()));
                // CPU / GPU 两路逐像素对照（含 ASCII 图）
                TextUtil.comparePaths(sample, 3.0F);
                // 方向校验：L（左下重）与 T（顶部横杠）—— 若上下翻转会一眼看出来
                TextUtil.comparePaths(net.minecraft.network.chat.Component.literal("L"), 4.0F);
                TextUtil.comparePaths(net.minecraft.network.chat.Component.literal("T"), 4.0F);
                // 多图集校验：拉丁 + CJK 分属不同 provider 的页，必须按图集分批绘制
                TextUtil.comparePaths(net.minecraft.network.chat.Component.literal("A中"), 4.0F);
            }
            if (Boolean.getBoolean("exparticle.selftest.commands") && client.player != null) {
                if (selfTestTick < 0) {
                    selfTestTick = 0;
                }
                selfTestTick++;
                if (selfTestTick > 40 && selfTestTick % 100 == 0 && selfTestIndex < SELF_TEST_COMMANDS.length) {
                    var command = SELF_TEST_COMMANDS[selfTestIndex++];
                    System.out.println("[exparticle] selftest cmd[" + selfTestIndex + "/" + SELF_TEST_COMMANDS.length
                        + "]: " + command);
                    client.player.connection.sendCommand(command);
                }
            }
            // API 自检（诊断）：ExParticleApi 是给别的 mod 用的，命令不会走它，所以单独跑一遍
            if (!apiTestDone && client.level != null && Boolean.getBoolean("exparticle.selftest.api")) {
                apiTestDone = true;
                net.hackermdch.exparticle.util.ApiSelfTest.run(client);
            }
        });
    }

    /** API 自检只跑一次。 */
    private static boolean apiTestDone;
}
