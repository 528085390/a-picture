package com.li.apicturebackend.manager;

import com.li.apicturebackend.config.CosClientConfig;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "cos", matchIfMissing = true)
public class CosManager implements FileStorageManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;


    /**
     * 上传对象
     *
     * @param key
     * @param file
     * @return
     * @throws CosClientException
     */
    public PutObjectResult putObject(String key, File file)
            throws CosClientException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucketName(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    // 下载文件
    public COSObject downloadObject(String key)
            throws CosClientException {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucketName(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传图片
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucketName(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传图片并使用万象服务解析图片信息
     */
    @Override
    public void uploadPicture(String key, File file, UploadPictureResult result) {
        PutObjectResult putObjectResult = putPictureObject(key, file);
        ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
        result.setPicWidth(imageInfo.getWidth());
        result.setPicHeight(imageInfo.getHeight());
        result.setPicFormat(imageInfo.getFormat());
    }

    @Override
    public void upload(String key, File file) {
        putObject(key, file);
    }

    @Override
    public InputStream download(String key) {
        return downloadObject(key).getObjectContent();
    }

    @Override
    public String getHost() {
        return cosClientConfig.getHost();
    }
}
