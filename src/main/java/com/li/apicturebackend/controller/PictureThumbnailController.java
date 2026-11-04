package com.li.apicturebackend.controller;

import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.manager.FileStorageManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网格缩略图接口。
 * 列表页只需要 480px 左右的图片，直接加载原图/预览图会带来很大的解码与显存压力。
 */
@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureThumbnailController {

    private static final Map<String, byte[]> THUMBNAIL_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 512;

    @Resource
    private FileStorageManager fileStorageManager;

    @GetMapping("/thumb")
    public void getThumbnail(@RequestParam String key,
                             @RequestParam(defaultValue = "480") int w,
                             HttpServletResponse response) throws Exception {
        if (key == null || key.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 key 不能为空");
        }
        int width = Math.max(160, Math.min(640, w));
        String cacheKey = width + ":" + key;
        byte[] bytes = THUMBNAIL_CACHE.get(cacheKey);
        if (bytes == null) {
            bytes = buildThumbnail(key, width);
            if (THUMBNAIL_CACHE.size() >= MAX_CACHE_ENTRIES) {
                THUMBNAIL_CACHE.clear();
            }
            THUMBNAIL_CACHE.put(cacheKey, bytes);
        }
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "public, max-age=604800, immutable");
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private byte[] buildThumbnail(String key, int targetWidth) throws Exception {
        try (InputStream inputStream = fileStorageManager.download(key)) {
            BufferedImage source = ImageIO.read(inputStream);
            if (source == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法解析图片");
            }
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            int width = Math.min(targetWidth, sourceWidth);
            int height = Math.max(1, (int) Math.round(sourceHeight * (width * 1.0 / sourceWidth)));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法生成缩略图");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(0.82f);
                }
                writer.write(null, new IIOImage(target, null, null), params);
            } finally {
                writer.dispose();
            }
            return output.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("build thumbnail failed, key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成缩略图失败");
        }
    }
}
