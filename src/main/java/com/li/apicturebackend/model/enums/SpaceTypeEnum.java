package com.li.apicturebackend.model.enums;

import lombok.Getter;

/**
 * 空间类型枚举
 *
 * @author huang
 */
@Getter
public enum SpaceTypeEnum {

    /**
     * 私有空间：仅创建者可访问，一个用户最多一个
     */
    PRIVATE("私有空间", 0),

    /**
     * 团队空间：预留，后续做成员管理
     */
    TEAM("团队空间", 1);

    private final String desc;
    private final int value;

    SpaceTypeEnum(String desc, int value) {
        this.desc = desc;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SpaceTypeEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SpaceTypeEnum e : values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        return null;
    }
}
