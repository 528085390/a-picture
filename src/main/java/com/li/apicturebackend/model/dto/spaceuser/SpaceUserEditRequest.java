package com.li.apicturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑空间成员请求（设置成员角色）
 */
@Data
public class SpaceUserEditRequest implements Serializable {

    /**
     * 成员记录 id
     */
    private Long id;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
