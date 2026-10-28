package com.li.apicturebackend.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 空间角色枚举（团队空间内的角色，区别于用户自身的 userRole）
 *
 * @author huang
 */
@Getter
public enum SpaceRoleEnum {

    /**
     * 浏览者：仅可查看空间中的图片
     */
    VIEWER("浏览者", "viewer", Collections.singleton("picture:view")),

    /**
     * 编辑者：可查看、上传、修改、删除图片
     */
    EDITOR("编辑者", "editor", new LinkedHashSet<>(Arrays.asList(
            "picture:view", "picture:upload", "picture:edit", "picture:delete"))),

    /**
     * 管理员：拥有管理空间和成员的所有权限
     */
    ADMIN("管理员", "admin", new LinkedHashSet<>(Arrays.asList(
            "spaceUser:manage", "picture:view", "picture:upload", "picture:edit", "picture:delete")));

    /**
     * 角色描述
     */
    private final String desc;

    /**
     * 角色值，落库到 space_user.spaceRole
     */
    private final String value;

    /**
     * 该角色拥有的权限键集合
     */
    private final Set<String> permissions;

    SpaceRoleEnum(String desc, String value, Set<String> permissions) {
        this.desc = desc;
        this.value = value;
        this.permissions = permissions;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SpaceRoleEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (SpaceRoleEnum e : values()) {
            if (e.getValue().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
