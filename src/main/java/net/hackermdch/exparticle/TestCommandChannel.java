package net.hackermdch.exparticle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 无人值守测试通道（**默认关闭**，用 {@code -Dexparticle.testChannel=true} 启用）。
 *
 * <p>用途：给 Minecraft 注入键盘输入不稳定（中文输入法激活时收不到按键、窗口被压住等），
 * 所以自动化测试改为**写文件驱动**：
 *
 * <ul>
 *   <li>写 {@code <gamedir>/config/exparticle-live.txt}：第一行 {@code seq=<n>}（数字变化才重跑），
 *       其余每行一条不带斜杠的 {@code particlex ...} 命令，{@code #} 开头的行忽略；</li>
 *   <li>进度写 {@code <gamedir>/config/exparticle-live.out.json}：{@code {"seq":n,"sent":k,"total":m,"last":"..."}}；</li>
 *   <li>每条命令间隔 20 tick（1 秒）发送，走 {@code ClientPacketListener.sendCommand}（等于聊天栏执行，
 *       绕过本地解析与键盘）。</li>
 * </ul>
 *
 * <p>发布版默认不启用；启用后也只是**每 10 tick 轮询一次文件**（不是每 tick 读盘）。
 */
public final class TestCommandChannel {
    private static final boolean ENABLED = Boolean.getBoolean("exparticle.testChannel");
    /** 轮询间隔（tick）：10 tick = 0.5 秒一次，足够自动化用，且不会每帧读盘。 */
    private static final int POLL_INTERVAL = 10;
    private static final Path IN = FabricLoader.getInstance().getGameDir().resolve("config").resolve("exparticle-live.txt");
    private static final Path OUT = FabricLoader.getInstance().getGameDir().resolve("config").resolve("exparticle-live.out.json");

    private static int currentSeq = Integer.MIN_VALUE;
    private static List<String> pending = List.of();
    private static int index;
    private static int sentTotal;
    private static int cooldown;
    private static int pollCounter;
    private static String last = "";

    private TestCommandChannel() {
    }

    public static void tick(Minecraft client) {
        if (!ENABLED || client.player == null || client.level == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (index >= pending.size()) {
            if (++pollCounter < POLL_INTERVAL) {
                return;
            }
            pollCounter = 0;
            reload();
        }
        if (index < pending.size()) {
            var command = pending.get(index++);
            last = command;
            try {
                client.player.connection.sendCommand(command);
                sentTotal++;
            } catch (Throwable e) {
                last = "FAILED: " + command + " -> " + e;
            }
            cooldown = 20;
            writeStatus();
        }
    }

    private static void reload() {
        try {
            if (!Files.isRegularFile(IN)) {
                return;
            }
            var lines = Files.readAllLines(IN);
            if (lines.isEmpty()) {
                return;
            }
            var seq = currentSeq;
            var commands = new ArrayList<String>();
            for (var raw : lines) {
                var line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("seq=")) {
                    try {
                        seq = Integer.parseInt(line.substring(4).trim());
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }
                if (line.startsWith("#")) {
                    continue;
                }
                commands.add(line.startsWith("/") ? line.substring(1) : line);
            }
            if (seq == currentSeq) {
                return;
            }
            currentSeq = seq;
            pending = List.copyOf(commands);
            index = 0;
            sentTotal = 0;
            writeStatus();
        } catch (Throwable e) {
            last = "RELOAD FAILED: " + e;
        }
    }

    private static void writeStatus() {
        try {
            var json = "{\"seq\":" + currentSeq + ",\"sent\":" + sentTotal + ",\"total\":" + pending.size()
                + ",\"last\":" + quote(last) + "}";
            Files.writeString(OUT, json);
        } catch (Throwable ignored) {
        }
    }

    private static String quote(String value) {
        var sb = new StringBuilder("\"");
        for (var c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
