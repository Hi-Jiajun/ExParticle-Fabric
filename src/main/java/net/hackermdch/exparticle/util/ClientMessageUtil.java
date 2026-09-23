package net.hackermdch.exparticle.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 客户端聊天栏输出。
 *
 * <p>上游实现是给 {@code PrintStream} 覆写 {@code println(Object)}，指望
 * {@code printStackTrace} 走那条分支——但 JDK 的 {@code printStackTrace} 走的是
 * {@code print(String)} / {@code println(String)}，所以异常其实**只进了 stdout、没进聊天栏**
 * （移植调试时实测：游戏里看不到任何报错）。这里改成把栈逐行塞进聊天栏，语义与上游意图一致。
 */
public class ClientMessageUtil {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    public static void addChatMessage(Throwable e) {
        var sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        for (var line : sw.toString().split("\r?\n")) {
            if (!line.isBlank()) {
                CLIENT.gui.getChat().addMessage(Component.literal(line));
            }
        }
    }

    public static void addChatMessage(Component message) {
        CLIENT.gui.getChat().addMessage(message);
    }
}
