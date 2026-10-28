package com.li.apicturebackend.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间资源使用分析请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUsageAnalyzeRequest extends SpaceAnalyzeRequest {

    private static final long serialVersionUID = 1L;
}
