package net.hackermdch.exparticle.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Predicate;

/**
 * 视频 → 粒子（Fabric 版）。
 *
 * <p>与上游完全一致的用户体验：把 JavaCV 的 jar 放进 {@code gamedir/javacv} 就能用；
 * 没装时给出与上游同一句提示（含 JavaCV 链接，可点击）。
 * 差别只在装载机制：上游走 NeoForge 模块层（Unsafe + 反射），Fabric 走
 * {@link JavaCvSupport} 的 child-first 类加载器。
 */
public class VideoUtil {
    public static final File VIDEO_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("particleVideos").toFile();

    public static void decoder(String path, Predicate<BufferedImage> consumer) {
        if (!JavaCvSupport.available()) {
            System.out.println("[exparticle] video util: JavaCV unavailable, command rejected");
            var click = new ClickEvent.OpenUrl(java.net.URI.create("https://github.com/bytedeco/javacv"));
            var link = Component.literal("JavaCV").withStyle(Style.EMPTY.withClickEvent(click).withUnderlined(true).applyFormat(ChatFormatting.BLUE));
            ClientMessageUtil.addChatMessage(Component.translatable("command.video.unavailable", link).withStyle(ChatFormatting.RED));
            return;
        }
        JavaCvSupport.decode(path, consumer);
    }

    static {
        if (VIDEO_DIR.exists() && VIDEO_DIR.isFile()) {
            VIDEO_DIR.delete();
        }
        if (!VIDEO_DIR.exists()) {
            VIDEO_DIR.mkdirs();
        }
    }
}
