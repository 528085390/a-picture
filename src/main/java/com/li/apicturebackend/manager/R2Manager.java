package com.li.apicturebackend.manager;

import com.li.apicturebackend.config.R2ClientConfig;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    /** 图片对象交给浏览器缓存一年（对象键带随机后缀，不会被复用） */
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    /** 视频等大文件缓存一周 */
    private static final String VIDEO_CACHE_CONTROL = "public, max-age=604800";

    /** 私密对象键前缀：空间图片/视频（space/<spaceId>/…）只进私密桶 */
    public static final String PRIVATE_KEY_PREFIX = "space/";

    /**
     * 键是否属于私密空间（空间图片、空间 live 视频）
     */
    public static boolean isPrivateKey(String key) {
        return key != null && formatKeyStatic(key).startsWith(PRIVATE_KEY_PREFIX);
    }

    /**
     * 按键前缀选桶：空间对象走私密桶，其余走公开桶
     */
    private String bucketFor(String key) {
        return isPrivateKey(key) ? r2ClientConfig.getPrivateBucketName() : r2ClientConfig.getBucketName();
    }

    public PutObjectResponse putObject(String key, File file) {
        String contentType = guessContentType(key);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketFor(key))
                .key(formatKey(key))
                .contentType(contentType)
                .cacheControl(contentType.startsWith("video/") ? VIDEO_CACHE_CONTROL : IMAGE_CACHE_CONTROL)
                .build();
        return s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
    }

    /**
     * 按扩展名推断 Content-Type；R2 默认给 application/octet-stream，浏览器无法据此缓存和预览
     */
    private static String guessContentType(String key) {
        int dot = key.lastIndexOf('.');
        String ext = dot >= 0 ? key.substring(dot + 1).toLowerCase() : "";
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            case "gif":
                return "image/gif";
            case "mp4":
            case "m4v":
                return "video/mp4";
            case "mov":
                return "video/quicktime";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     * @return 对象流
     */
    public ResponseInputStream<GetObjectResponse> downloadObject(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketFor(key))
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

    /**
     * 下载对象
     * <p>
     * S3 凭证失效（401/403）时回退到公网访问域名直读。R2 的 S3 令牌会过期或被轮换，
     * 失效后整站图片全部变成坏图；公网域名指向同一批对象，只要桶还开放访问就能读到。
     */
    @Override
    public InputStream download(String key) {
        // 私密对象没有公网域名，读取失败只能报错，绝不能回退公网地址（否则等于绕过鉴权）
        if (isPrivateKey(key)) {
            return downloadObject(key);
        }
        try {
            return downloadObject(key);
        } catch (SdkException e) {
            log.warn("R2 S3 读取失败，回退公网地址：{}，原因：{}", key, e.getMessage());
            try {
                return downloadFromPublicUrl(key);
            } catch (Exception fallbackError) {
                e.addSuppressed(fallbackError);
                throw e;
            }
        }
    }

    /**
     * 通过公开访问域名读取对象（无需签名），失败时抛 IOException
     */
    private InputStream downloadFromPublicUrl(String key) throws IOException {
        String url = getHost() + "/" + formatKey(key);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("公网地址读取失败，HTTP " + status + "：" + url);
        }
        InputStream inputStream = connection.getInputStream();
        return new FilterInputStream(inputStream) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    connection.disconnect();
                }
            }
        };
    }

    /**
     * 对象访问地址：公开对象走 CDN 域名，私密对象走同源后端（读取时按登录态鉴权）
     */
    @Override
    public String getUrl(String key) {
        String normalized = formatKey(key);
        if (isPrivateKey(normalized)) {
            return "/api/picture/file/" + normalized;
        }
        return getHost() + "/" + normalized;
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
        return formatKeyStatic(key);
    }

    private static String formatKeyStatic(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
