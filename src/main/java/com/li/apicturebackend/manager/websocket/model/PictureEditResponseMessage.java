package com.li.apicturebackend.manager.websocket.model;

import com.li.apicturebackend.model.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片协同编辑响应消息（后端 -> 前端）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditResponseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型，见 {@link com.li.apicturebackend.manager.websocket.model.enums.PictureEditMessageTypeEnum}
     */
    private String type;

    /**
     * 提示信息，前端直接拿去 toast
     */
    private String message;

    /**
     * 执行的编辑动作
     */
    private String editAction;

    /**
     * 触发这次消息的用户
     */
    private UserVO user;
}
