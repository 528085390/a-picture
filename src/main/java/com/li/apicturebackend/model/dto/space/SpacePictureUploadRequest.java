package com.li.apicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间图片上传请求
 */
@Data
public class SpacePictureUploadRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 图片 id，传入表示在空间内重新上传覆盖
     */
    private Long id;

    /**
     * 图片类型：live|static，未传时新图片默认 live，覆盖上传沿用原类型
     */
    private String type;

    private static final long serialVersionUID = 1L;
}
