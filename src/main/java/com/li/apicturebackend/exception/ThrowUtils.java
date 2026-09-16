package com.li.apicturebackend.exception;

public class ThrowUtils {


    /**
     * 条件成立抛异常
     *
     * @param condition
     * @param runtimeException
     */
    public static void throwIf(Boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 条件成立抛出指定业务异常
     *
     * @param condition
     * @param errorCode
     */
    public static void throwIf(Boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立抛出自定义错误信息异常
     *
     * @param condition
     * @param errorCode
     * @param message
     */
    public static void throwIf(Boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}
