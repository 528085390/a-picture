package com.li.apicturebackend.model.dto.space;

import com.li.apicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 空间图片查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpacePictureQueryRequest extends PageRequest implements Serializable {

    /**
     * 空间 id，必填
     */
    private Long spaceId;

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

    /**
     * 搜索词（同时搜名称和简介）
     */
    private String searchText;

    private static final long serialVersionUID = 1L;
}
