package com.li.apicturebackend.model.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片分析通用请求，所有分析请求都继承它
 */
@Data
public class SpaceAnalyzeRequest implements Serializable {

    /**
     * 空间 id（queryAll、queryPublic 都为 false 时生效）
     */
    private Long spaceId;

    /**
     * 是否查询公共图库（仅管理员）
     */
    private boolean queryPublic;

    /**
     * 是否查询全空间（仅管理员）
     */
    private boolean queryAll;

    private static final long serialVersionUID = 1L;
}
