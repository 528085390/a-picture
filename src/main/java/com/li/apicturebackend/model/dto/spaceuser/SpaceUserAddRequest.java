package com.li.apicturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加空间成员请求（空间管理员使用）
 */
@Data
public class SpaceUserAddRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin，为空时默认 viewer
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
