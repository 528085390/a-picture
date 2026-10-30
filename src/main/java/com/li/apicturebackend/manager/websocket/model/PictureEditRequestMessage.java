package com.li.apicturebackend.manager.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片协同编辑请求消息（前端 -> 后端）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditRequestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型，见 {@link com.li.apicturebackend.manager.websocket.model.enums.PictureEditMessageTypeEnum}
     */
    private String type;

    /**
     * 执行的编辑动作，见 {@link com.li.apicturebackend.manager.websocket.model.enums.PictureEditActionEnum}
     */
    private String editAction;
}
