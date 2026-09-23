package net.hackermdch.exparticle.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Predicate;

/**
 * JavaCV 视频解码桥接（**只由 JavaCvSupport 的子加载器加载**，不要在本工程其他类里直接引用）。
 *
 * <p>代码逻辑与上游 {@code VideoUtil.decoder} 一致：FFmpeg 抓帧 → Java2D 转 BufferedImage →
 * 按原视频帧率喂给消费者；消费者返回 false 时提前结束。
 *
 * <p>与上游的唯一差别（必须的修正）：上游用 {@code Java2DFrameConverter#convert(Frame)}，
 * 它按 {@code frame.imageDepth/imageChannels} 推断 BufferedImage 类型；在本机的
 * JavaCV 1.5.11 + FFmpeg 7.1 组合下，抓到的帧报 {@code depth=8 ch=6/12}（实际是 6 字节/像素），
 * 推断结果为 0 → {@code ComponentColorModel} 拿到 null ColorSpace → NPE（实测）。
 * 所以这里自己建 {@code TYPE_3BYTE_BGR} 图像再 {@code Java2DFrameConverter.copy(frame, image)}——
 * 已用纯红/纯蓝测试视频验证通道顺序正确（红 → #FD0000，蓝 → #0000FE）。
 */
public final class VideoDecoder {
    private static final ThreadPoolExecutor VIDEO_DECODER_THREAD_POOL = new ScheduledThreadPoolExecutor(5,
        new ThreadFactoryBuilder().setNameFormat("Video Decoder #%d").setDaemon(true)
            .setUncaughtExceptionHandler((thread, throwable) -> ClientMessageUtil.addChatMessage(throwable)).build());

    private VideoDecoder() {
    }

    public static void decode(String path, Predicate<BufferedImage> consumer) {
        VIDEO_DECODER_THREAD_POOL.execute(() -> decodeSync(path, consumer));
    }

    /** 同步解码（供诊断/单线程场景使用）。 */
    public static void decodeSync(String path, Predicate<BufferedImage> consumer) {
        {
            try (var grabber = new FFmpegFrameGrabber(new File(VideoUtil.VIDEO_DIR, path))) {
                grabber.start();
                var image = new BufferedImage(Math.max(1, grabber.getImageWidth()), Math.max(1, grabber.getImageHeight()),
                    BufferedImage.TYPE_3BYTE_BGR);
                System.out.println("[exparticle] video decode start: " + path + " " + image.getWidth() + "x" + image.getHeight()
                    + " rate=" + grabber.getFrameRate() + " frames=" + grabber.getLengthInVideoFrames());
                var startTime = System.currentTimeMillis();
                var count = 0;
                var rate = grabber.getFrameRate();
                var length = grabber.getLengthInVideoFrames() - 1;
                while (count < length) {
                    long curTime = System.currentTimeMillis();
                    if (curTime - startTime > (count * 1000) / rate) {
                        var frame = grabber.grab();
                        if (frame != null && frame.image != null) {
                            Java2DFrameConverter.copy(frame, image);
                            if (!consumer.test(image)) {
                                break;
                            }
                            ++count;
                        }
                    } else {
                        Thread.sleep(10L);
                    }
                }
                grabber.stop();
                System.out.println("[exparticle] video decode end: " + path + " frames=" + count);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
