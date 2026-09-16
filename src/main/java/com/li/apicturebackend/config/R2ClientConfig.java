package com.li.apicturebackend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Cloudflare R2 客户端配置，对标 {@link CosClientConfig}
 */
@Configuration
@ConfigurationProperties(prefix = "r2.client")
@Data
@Slf4j
public class R2ClientConfig {

    /**
     * S3 兼容端点，格式：https://<accountId>.r2.cloudflarestorage.com
     */
    private String endpoint;

    /**
     * R2 API 令牌的 Access Key ID
     */
    private String accessKeyId;

    /**
     * R2 API 令牌的 Secret Access Key
     */
    private String secretAccessKey;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 公开访问地址（r2.dev 子域或自定义域名），对标 COS 的 host
     */
    private String publicUrl;

    /**
     * R2 固定使用 auto
     */
    private String region = "auto";

    @Bean
    @ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "r2")
    public S3Client getS3Client() {
        // 1 传入 R2 API 令牌生成的 Access Key ID 和 Secret Access Key
        // 2 R2 的地域固定为 auto，访问地址为 https://<accountId>.r2.cloudflarestorage.com
        // 3 R2 使用路径风格的访问方式
        warnIfLengthWrong(accessKeyId, 32, "Access Key ID");
        warnIfLengthWrong(secretAccessKey, 64, "Secret Access Key");
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // R2 不支持 SDK 默认开启的灵活校验和，改为按需计算，避免上传被拒绝
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /**
     * R2 的 S3 凭证长度是固定的，填错时在上传阶段只会得到一句含义不明的 400，这里提前提示
     */
    private void warnIfLengthWrong(String value, int expectedLength, String name) {
        int length = value == null ? 0 : value.length();
        if (length != expectedLength) {
            log.warn("R2 的 {} 长度是 {}，R2 的 S3 接口要求 {} 位；请确认填的是 R2 的 S3 凭证，" +
                    "而不是 cfat_ 开头的 Cloudflare API Token", name, length, expectedLength);
        }
    }
}
