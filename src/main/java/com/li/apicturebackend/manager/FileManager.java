package com.li.apicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.dto.file.UploadLiveVideoResult;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;

@Component
@Slf4j
public class FileManager {

    private static final int PREVIEW_MAX_SIDE = 1280;
    private static final float PREVIEW_JPEG_QUALITY = 0.8f;

    @Resource
    private FileStorageManager fileStorageManager;

    /**
     * 上传图片并解析数据
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        String originFilename = multipartFile.getOriginalFilename();
        String uploadPath = getUploadPath(uploadPathPrefix, originFilename);


        // 将 MultipartFile 转换为 File
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);

            // 构建返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(fileStorageManager.getUrl(uploadPath));

            // 上传图片并填充图片信息（COS 走万象服务，R2 走 JDK ImageIO）
            fileStorageManager.uploadPicture(uploadPath, file, uploadPictureResult);

            // 预览图只用于列表展示，失败时不影响原图上传
            uploadPreview(uploadPath, file, uploadPictureResult);

            // 图片信息读取不出来时留空，宽高比例同样留空
            Integer picWidth = uploadPictureResult.getPicWidth();
            Integer picHeight = uploadPictureResult.getPicHeight();
            if (picWidth != null && picHeight != null && picHeight > 0) {
                uploadPictureResult.setPicScale(NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue());
            }
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("upload picture failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传图片失败");
        } finally {
            // 删除临时文件
            FileUtil.del(file);
        }
    }

    /**
     * 生成并上传压缩预览图，并顺带预生成各档缩略图
     */
    private void uploadPreview(String uploadPath, File sourceFile, UploadPictureResult result) {
        File previewFile = null;
        try {
            previewFile = File.createTempFile("preview_", ".jpg");
            BufferedImage previewImage = createPreviewImage(sourceFile);
            ThumbnailManager.writeJpeg(previewImage, previewFile, PREVIEW_JPEG_QUALITY);

            String previewPath = getPreviewPath(uploadPath);
            fileStorageManager.upload(previewPath, previewFile);
            result.setPreviewUrl(fileStorageManager.getUrl(previewPath));

            uploadThumbnails(previewPath, previewImage);
        } catch (Exception e) {
            log.warn("生成或上传预览图失败，原图地址：{}", result.getUrl(), e);
        } finally {
            FileUtil.del(previewFile);
        }
    }

    /**
     * 按固定档位预生成缩略图并上传，单档失败只记日志，不影响图片本身
     */
    private void uploadThumbnails(String previewPath, BufferedImage previewImage) {
        int previewWidth = previewImage.getWidth();
        for (int width : ThumbnailManager.WIDTHS) {
            // 小图不放大：请求宽度不小于预览图宽度时前端直接用预览图
            if (width >= previewWidth) {
                continue;
            }
            File thumbFile = null;
            try {
                thumbFile = File.createTempFile("thumb_", ".jpg");
                ThumbnailManager.writeJpeg(
                        ThumbnailManager.downscale(previewImage, width), thumbFile, PREVIEW_JPEG_QUALITY);
                fileStorageManager.upload(ThumbnailManager.thumbKey(previewPath, width), thumbFile);
            } catch (Exception e) {
                log.warn("生成缩略图失败，width={}，previewPath={}", width, previewPath, e);
            } finally {
                FileUtil.del(thumbFile);
            }
        }
    }

    /**
     * 按长边等比缩放生成预览图，小图不放大
     */
    private BufferedImage createPreviewImage(File sourceFile) throws IOException {
        BufferedImage sourceImage = ImageIO.read(sourceFile);
        if (sourceImage == null) {
            throw new IOException("ImageIO 不支持该图片格式");
        }
        double scale = Math.min(1.0,
                PREVIEW_MAX_SIDE / (double) Math.max(sourceImage.getWidth(), sourceImage.getHeight()));
        int previewWidth = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
        int previewHeight = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
        BufferedImage previewImage = new BufferedImage(
                previewWidth, previewHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = previewImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, previewWidth, previewHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, previewWidth, previewHeight, null);
        } finally {
            graphics.dispose();
            sourceImage.flush();
        }
        return previewImage;
    }

    /**
     * 在原图路径旁生成 _preview.jpg 对象键
     */
    private String getPreviewPath(String uploadPath) {
        int suffixIndex = uploadPath.lastIndexOf('.');
        int separatorIndex = uploadPath.lastIndexOf('/');
        if (suffixIndex > separatorIndex) {
            return uploadPath.substring(0, suffixIndex) + "_preview.jpg";
        }
        return uploadPath + "_preview.jpg";
    }

    /**
     * 上传live的视频
     */
    public UploadLiveVideoResult uploadLiveVideo(MultipartFile multipartFile, String uploadPathPrefix) {
        String originFilename = multipartFile.getOriginalFilename();
        String uploadPath = getUploadPath(uploadPathPrefix, originFilename);

        // 将 MultipartFile 转换为 File
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);

            // 上传视频
            fileStorageManager.upload(uploadPath, file);
            // 构建返回结果
            UploadLiveVideoResult uploadLiveVideoResult = new UploadLiveVideoResult();
            uploadLiveVideoResult.setUrl(fileStorageManager.getUrl(uploadPath));
            uploadLiveVideoResult.setVdoName(FileUtil.mainName(originFilename));
            return uploadLiveVideoResult;

        } catch (Exception e) {
            log.error("upload live video failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传视频失败");
        } finally {
            // 删除临时文件
            FileUtil.del(file);
        }
    }

    /**
     * 获得上传路径
     */
    public String getUploadPath(String uploadPathPrefix, String originFilename) {
        String uploadPath;
        // 针对备份图片优化
        if (originFilename.startsWith("20")) {
            String year = originFilename.substring(0, 4);
            uploadPath = uploadPathPrefix + "/" + year + "/" + originFilename;
        } else {
            String uuid = RandomUtil.randomString(16);
            String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                    FileUtil.getSuffix(originFilename));
            uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);
        }
        return uploadPath;
    }


}
