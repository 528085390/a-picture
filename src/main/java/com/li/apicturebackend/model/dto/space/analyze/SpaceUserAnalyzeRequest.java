package com.li.apicturebackend.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户上传行为分析请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUserAnalyzeRequest extends SpaceAnalyzeRequest {

    /**
     * 用户 id（为空表示不按用户过滤）
     */
    private Long userId;

    /**
     * 时间维度：day / week / month
     */
    private String timeDimension;

    private static final long serialVersionUID = 1L;
}
