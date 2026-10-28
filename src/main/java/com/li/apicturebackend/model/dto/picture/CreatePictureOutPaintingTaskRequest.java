package com.li.apicturebackend.model.dto.picture;

import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建 AI 扩图任务请求（前端入参）
 */
@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;
}
