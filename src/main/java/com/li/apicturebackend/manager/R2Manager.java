package com.li.apicturebackend.manager;

import com.li.apicturebackend.config.R2ClientConfig;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Cloudflare R2 对象存储操作，对标 {@link CosManager}
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "r2")
public class R2Manager implements FileStorageManager {

    @Resource
    private R2ClientConfig r2ClientConfig;

    @Resource
    private S3Client s3Client;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResponse putObject(String key, File file) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(r2ClientConfig.getBucketName())
                .key(formatKey(key))
                .build();
        return s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     * @return 对象流
     */
    public ResponseInputStream<GetObjectResponse> downloadObject(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(r2ClientConfig.getBucketName())
                .key(formatKey(key))
                .build();
        return s3Client.getObject(getObjectRequest);
    }

    /**
     * 上传图片并解析图片信息
     * <p>
     * R2 没有万象那种图片处理服务，这里用 JDK 自带的 ImageIO 在本地读取
     */
    @Override
    public void uploadPicture(String key, File file, UploadPictureResult result) {
        putObject(key, file);
        fillImageInfo(file, result);
    }

    @Override
    public void upload(String key, File file) {
        putObject(key, file);
    }

    @Override
    public InputStream download(String key) {
        return downloadObject(key);
    }

    @Override
    public String getHost() {
        String publicUrl = r2ClientConfig.getPublicUrl();
        // 去掉结尾的 /，避免和上传路径拼接出 //
        return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /**
     * 使用 JDK ImageIO 读取图片的宽高和格式，ImageIO 不支持的格式（如 HEIC、WebP）则留空
     */
    private void fillImageInfo(File file, UploadPictureResult result) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(file)) {
            if (imageInputStream == null) {
                log.warn("R2 读取图片信息失败，文件不可读：{}", file.getName());
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                log.warn("R2 读取图片信息失败，ImageIO 不支持该格式：{}", file.getName());
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream);
                result.setPicWidth(reader.getWidth(0));
                result.setPicHeight(reader.getHeight(0));
                // 与万象返回的格式保持一致，如 jpg、png
                String format = reader.getFormatName().toLowerCase();
                result.setPicFormat("jpeg".equals(format) ? "jpg" : format);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.warn("R2 读取图片信息失败，文件：{}", file.getName(), e);
        }
    }

    /**
     * S3 的对象键不能以 / 开头
     */
    private String formatKey(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
