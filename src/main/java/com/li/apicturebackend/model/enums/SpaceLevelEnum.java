package com.li.apicturebackend.model.enums;

import lombok.Getter;

/**
 * 空间等级枚举
 *
 * @author huang
 */
@Getter
public enum SpaceLevelEnum {
    /**
     * 普通版：最多 100 张图片，总容量 100 MB
     */
    COMMON("普通版", 0, 100, 100L * 1024 * 1024),
    /**
     * 专业版：最多 1000 张图片，总容量 1 GB
     */
    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 * 1024),
    /**
     * 旗舰版：最多 10000 张图片，总容量 10 GB
     */
    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 * 1024);

    private final String desc;
    private final int value;
    /** 最大图片数量 */
    private final long maxCount;
    /** 最大总容量（字节） */
    private final long maxSize;

    SpaceLevelEnum(String desc, int value, long maxCount, long maxSize) {
        this.desc = desc;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    public static SpaceLevelEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SpaceLevelEnum e : values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        return null;
    }
}
