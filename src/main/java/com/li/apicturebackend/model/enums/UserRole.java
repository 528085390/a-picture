package com.li.apicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Data;

/*
    用户角色枚举
 */
public enum UserRole {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;
    private final String value;

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }

    UserRole(String text, String value) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据value获取枚举
     *
     * @param value
     * @return
     */
    public static UserRole getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRole item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }
}
