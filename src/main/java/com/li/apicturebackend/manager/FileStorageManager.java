package com.li.apicturebackend.manager;

import com.li.apicturebackend.model.dto.file.UploadPictureResult;

import java.io.File;
import java.io.InputStream;

/**
 * 文件存储抽象，通过 file.storage.type 在腾讯云 COS 和 Cloudflare R2 之间切换
 */
public interface FileStorageManager {

    /**
     * 上传图片，并把图片信息（宽高、格式）填充到 result 中
     * <p>
     * COS 使用万象图片处理服务，R2 使用 JDK ImageIO 本地解析，解析不出来时留空
     *
     * @param key    唯一键
     * @param file   文件
     * @param result 图片上传结果，由实现类填充 picWidth、picHeight、picFormat
     */
    void uploadPicture(String key, File file, UploadPictureResult result);

    /**
     * 上传普通文件（如 live 视频）
     *
     * @param key  唯一键
     * @param file 文件
     */
    void upload(String key, File file);

    /**
     * 下载文件，由调用方负责关闭流
     *
     * @param key 唯一键
     * @return 文件流
     */
    InputStream download(String key);

    /**
     * 获得访问地址前缀，对标 CosClientConfig#getHost()
     *
     * @return 访问地址前缀
     */
    String getHost();

    /**
     * 把对象键换成对外访问地址。公开对象是 CDN 绝对地址，私密对象是同源后端地址。
     *
     * @param key 对象键
     * @return 访问地址
     */
    default String getUrl(String key) {
        return getHost() + "/" + (key.startsWith("/") ? key.substring(1) : key);
    }
}
