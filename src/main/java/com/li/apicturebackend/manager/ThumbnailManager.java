package com.li.apicturebackend.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;

/**
 * 缩略图规范与生成工具
 * <p>
 * 约定：缩略图宽度只允许 {@link #WIDTHS} 这几档，对象键在源对象键扩展名前插入 _w{宽度}.jpg，
 * 上传时预生成写回对象存储，读取时直接取对象，取不到才现场生成并异步补齐。
 */
@Component
public class ThumbnailManager {

    /**
     * 预生成的固定宽度档位，前端请求宽度会向上归一到最近的档位
     */
    public static final int[] WIDTHS = {240, 360, 640};

    /**
     * 允许请求的最小宽度
     */
    private static final int MIN_WIDTH = 160;

    /**
     * 允许请求的最大宽度
     */
    private static final int MAX_WIDTH = 640;

    /**
     * 热缩略图内存缓存：按字节数限容，超出按最近最少使用淘汰，不会整体清空
     */
    private final Cache<String, byte[]> cache = Caffeine.newBuilder()
            .maximumWeight(48L * 1024 * 1024)
            .weigher((String key, byte[] value) -> value.length)
            .expireAfterAccess(Duration.ofHours(6))
            .build();

    /**
     * 读取内存缓存
     */
    public byte[] getCached(String objectKey) {
        return cache.getIfPresent(objectKey);
    }

    /**
     * 写入内存缓存
     */
    public void putCached(String objectKey, byte[] bytes) {
        if (objectKey != null && bytes != null && bytes.length > 0) {
            cache.put(objectKey, bytes);
        }
    }

    /**
     * 把请求宽度归一到最近的预生成档位
     */
    public static int snapWidth(int requestedWidth) {
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, requestedWidth));
        for (int candidate : WIDTHS) {
            if (width <= candidate) {
                return candidate;
            }
        }
        return MAX_WIDTH;
    }

    /**
     * 生成缩略图对象键，例如 a/b/c.jpg -> a/b/c_w360.jpg
     */
    public static String thumbKey(String sourceKey, int width) {
        String key = sourceKey.startsWith("/") ? sourceKey.substring(1) : sourceKey;
        int slashIndex = key.lastIndexOf('/');
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex > slashIndex) {
            return key.substring(0, dotIndex) + "_w" + width + ".jpg";
        }
        return key + "_w" + width + ".jpg";
    }

    /**
     * 等比缩到指定宽度，小图不放大
     */
    public static BufferedImage downscale(BufferedImage source, int targetWidth) {
        int width = Math.min(targetWidth, source.getWidth());
        int height = Math.max(1,
                (int) Math.round(source.getHeight() * (width * 1.0 / source.getWidth())));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * 编码为 JPEG 字节数组
     */
    public static byte[] toJpegBytes(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(byteStream)) {
            writeJpeg(image, outputStream, quality);
        }
        return byteStream.toByteArray();
    }

    /**
     * 编码为 JPEG 文件
     */
    public static void writeJpeg(BufferedImage image, File targetFile, float quality) throws IOException {
        try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(targetFile)) {
            writeJpeg(image, outputStream, quality);
        }
    }

    private static void writeJpeg(BufferedImage image, ImageOutputStream outputStream, float quality)
            throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("没有可用的 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(outputStream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
