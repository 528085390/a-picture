package com.li.apicturebackend.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.li.apicturebackend.annotation.AuthCheck;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.constant.UserConstant;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.manager.FileStorageManager;
import com.li.apicturebackend.manager.SpaceMediaGuard;
import com.li.apicturebackend.manager.ThumbnailManager;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片缩略图接口
 * <p>
 * 优先读取上传时预生成的缩略图对象，取不到才现场生成并异步补齐，配合 ETag 让浏览器复用缓存。
 */
@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureThumbnailController {

    /**
     * 缩略图 JPEG 质量
     */
    private static final float THUMB_JPEG_QUALITY = 0.82f;

    /**
     * 缓存有效期，缩略图内容与对象键一一绑定，可以放心长缓存
     */
    private static final String CACHE_CONTROL_VALUE = "public, max-age=604800, immutable";

    /**
     * 回填/自愈线程，避免拖慢请求
     */
    private static final ExecutorService HEAL_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "thumbnail-heal");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private FileStorageManager fileStorageManager;

    @Resource
    private SpaceMediaGuard spaceMediaGuard;

    @Resource
    private ThumbnailManager thumbnailManager;

    @Resource
    private PictureService pictureService;

    /**
     * 获取缩略图
     *
     * @param key 原图或预览图的对象键
     * @param w   期望宽度，会被归一到最近的预生成档位
     */
    @GetMapping("/thumb")
    public void getThumbnail(@RequestParam String key,
                             @RequestParam(value = "w", defaultValue = "480") int w,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {
        if (StrUtil.isBlank(key)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 key 不能为空");
        }
        String objectKey = ThumbnailManager.thumbKey(key, ThumbnailManager.snapWidth(w));
        // 空间缩略图同样在私密桶里，必须先校验空间权限；失败按 404，不泄露存在性
        boolean privateObject = SpaceMediaGuard.isPrivateKey(key);
        if (privateObject) {
            if (!spaceMediaGuard.hasAccess(key, request)) {
                writePrivateNotFound(response);
                return;
            }
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Vary", "Cookie");
        } else {
            String etag = "\"" + Integer.toHexString(objectKey.hashCode()) + "\"";
            response.setHeader("ETag", etag);
            response.setHeader("Cache-Control", CACHE_CONTROL_VALUE);
            if (etag.equals(request.getHeader("If-None-Match"))) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }

        byte[] thumbnail;
        try {
            thumbnail = thumbnailManager.getCached(objectKey);
            if (thumbnail == null) {
                thumbnail = loadOrRender(key, objectKey, ThumbnailManager.snapWidth(w));
            }
        } catch (Exception e) {
            log.error("缩略图不可用：{}", key, e);
            writeImageError(response, "thumbnail unavailable");
            return;
        }

        response.setContentType("image/jpeg");
        response.setContentLength(thumbnail.length);
        response.getOutputStream().write(thumbnail);
        response.flushBuffer();
    }

    /**
     * 先取预生成的缩略图对象，取不到再现场生成并异步补写回对象存储
     */
    private byte[] loadOrRender(String sourceKey, String objectKey, int width) {
        try (InputStream inputStream = fileStorageManager.download(objectKey)) {
            byte[] cachedBytes = IoUtil.readBytes(inputStream);
            if (cachedBytes != null && cachedBytes.length > 0) {
                thumbnailManager.putCached(objectKey, cachedBytes);
                return cachedBytes;
            }
        } catch (Exception e) {
            log.debug("缩略图对象不存在，回退现场生成：{}", objectKey);
        }
        byte[] bytes = render(sourceKey, width);
        thumbnailManager.putCached(objectKey, bytes);
        healAsync(objectKey, bytes);
        return bytes;
    }

    /**
     * 现场从源图生成缩略图
     */
    private byte[] render(String sourceKey, int width) {
        BufferedImage sourceImage;
        try (InputStream inputStream = fileStorageManager.download(sourceKey)) {
            sourceImage = ImageIO.read(inputStream);
        } catch (Exception e) {
            log.error("读取原图失败：{}", sourceKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取图片失败");
        }
        if (sourceImage == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的图片格式");
        }
        try {
            return ThumbnailManager.toJpegBytes(
                    ThumbnailManager.downscale(sourceImage, width), THUMB_JPEG_QUALITY);
        } catch (Exception e) {
            log.error("生成缩略图失败：{}", sourceKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成缩略图失败");
        } finally {
            sourceImage.flush();
        }
    }

    /**
     * 异步把现场生成的缩略图写回对象存储，下次直接命中
     */
    private void healAsync(String objectKey, byte[] bytes) {
        HEAL_EXECUTOR.submit(() -> {
            File tempFile = null;
            try {
                tempFile = File.createTempFile("thumb_heal_", ".jpg");
                FileUtil.writeBytes(bytes, tempFile);
                fileStorageManager.upload(objectKey, tempFile);
                log.info("缩略图已补齐：{}", objectKey);
            } catch (Exception e) {
                log.warn("缩略图补齐失败：{}", objectKey, e);
            } finally {
                FileUtil.del(tempFile);
            }
        });
    }

    /**
     * 给历史图片批量补生成缩略图（管理员），按 id 升序分页执行，幂等覆盖
     *
     * @param limit  每批处理条数
     * @param lastId 上一批返回的 lastId，首次传 0
     */
    @PostMapping("/thumb/backfill")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> backfillThumbnails(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") long lastId) {
        int batchSize = Math.max(1, Math.min(200, limit));
        List<Picture> pictures = pictureService.lambdaQuery()
                .gt(Picture::getId, lastId)
                .orderByAsc(Picture::getId)
                .last("limit " + batchSize)
                .list();

        AtomicInteger generated = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long maxId = lastId;
        for (Picture picture : pictures) {
            maxId = Math.max(maxId, picture.getId());
            String sourceKey = toObjectKey(StrUtil.blankToDefault(picture.getPreviewUrl(), picture.getUrl()));
            if (StrUtil.isBlank(sourceKey)) {
                skipped.incrementAndGet();
                continue;
            }
            try {
                BufferedImage previewImage;
                try (InputStream inputStream = fileStorageManager.download(sourceKey)) {
                    previewImage = ImageIO.read(inputStream);
                }
                if (previewImage == null) {
                    skipped.incrementAndGet();
                    continue;
                }
                try {
                    for (int width : ThumbnailManager.WIDTHS) {
                        if (width >= previewImage.getWidth()) {
                            continue;
                        }
                        byte[] bytes = ThumbnailManager.toJpegBytes(
                                ThumbnailManager.downscale(previewImage, width), THUMB_JPEG_QUALITY);
                        uploadThumbnail(ThumbnailManager.thumbKey(sourceKey, width), bytes);
                        generated.incrementAndGet();
                    }
                } finally {
                    previewImage.flush();
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                log.warn("补生成缩略图失败，pictureId={}", picture.getId(), e);
            }
        }

        Map<String, Object> summary = new HashMap<>(8);
        summary.put("lastId", maxId);
        summary.put("scanned", pictures.size());
        summary.put("generated", generated.get());
        summary.put("skipped", skipped.get());
        summary.put("failed", failed.get());
        summary.put("finished", pictures.size() < batchSize);
        log.info("缩略图回填：{}", summary);
        return Result.success(summary);
    }

    private void uploadThumbnail(String objectKey, byte[] bytes) throws Exception {
        File tempFile = File.createTempFile("thumb_backfill_", ".jpg");
        try {
            FileUtil.writeBytes(bytes, tempFile);
            fileStorageManager.upload(objectKey, tempFile);
        } finally {
            FileUtil.del(tempFile);
        }
    }

    /**
     * 把图片 URL 还原成对象键
     */
    private static String toObjectKey(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String key = url.trim();
        int schemeIndex = key.indexOf("://");
        if (schemeIndex > 0) {
            int pathIndex = key.indexOf('/', schemeIndex + 3);
            if (pathIndex < 0) {
                return null;
            }
            key = key.substring(pathIndex + 1);
        } else if (key.startsWith("/")) {
            key = key.substring(1);
        }
        int queryIndex = key.indexOf('?');
        if (queryIndex > 0) {
            key = key.substring(0, queryIndex);
        }
        return key;
    }

    /**
     * 图片接口失败时返回非 200 的纯文本响应
     * <p>
     * 必须避免「HTTP 200 + JSON 错误体」：nginx 对这两个图片路径开了 proxy_cache，
     * 只按状态码缓存，200 的错误体会被当成图片缓存 7~30 天，用户端长期坏图。
     */
    /**
     * 私密缩略图无权访问时统一回 404
     */
    private static void writePrivateNotFound(HttpServletResponse response) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Vary", "Cookie");
            response.getWriter().write("not found");
            response.flushBuffer();
        } catch (Exception ignored) {
            // 客户端已断开，忽略
        }
    }

    private static void writeImageError(HttpServletResponse response, String message) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(message);
            response.flushBuffer();
        } catch (Exception ignored) {
            // 客户端已断开，忽略
        }
    }
}
