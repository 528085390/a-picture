package com.li.apicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 空间图片编辑请求（只改元信息，不动文件）
 */
@Data
public class SpacePictureEditRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long id;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
