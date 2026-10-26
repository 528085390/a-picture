package com.li.apicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 空间图片批量编辑请求（只改元信息，不动文件）
 */
@Data
public class SpacePictureEditByBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIdList;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 命名规则，其中的 {序号} 会被替换为从 1 开始的递增序号
     */
    private String nameRule;

    private static final long serialVersionUID = 1L;
}
