package com.li.apicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新空间请求（管理员端，可改级别和限额）
 */
@Data
public class SpaceUpdateRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    /**
     * 空间状态：0-正常 1-禁用
     */
    private Integer status;

    private static final long serialVersionUID = 1L;
}
