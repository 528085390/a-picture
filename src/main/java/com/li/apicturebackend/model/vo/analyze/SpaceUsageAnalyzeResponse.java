package com.li.apicturebackend.model.vo.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间资源使用分析响应
 */
@Data
public class SpaceUsageAnalyzeResponse implements Serializable {

    /**
     * 已使用大小
     */
    private Long usedSize;

    /**
     * 总大小（全空间 / 公共图库没有上限，为 null）
     */
    private Long maxSize;

    /**
     * 空间使用比例（百分比，分母为 0 时为 null）
     */
    private Double sizeUsageRatio;

    /**
     * 当前图片数量
     */
    private Long usedCount;

    /**
     * 最大图片数量
     */
    private Long maxCount;

    /**
     * 图片数量占比（百分比，分母为 0 时为 null）
     */
    private Double countUsageRatio;

    private static final long serialVersionUID = 1L;
}
