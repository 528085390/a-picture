package com.li.apicturebackend.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * 全局异常处理
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Sa-Token 未登录（团队空间账号体系）
     */
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginExceptionHandler(NotLoginException exception) {
        log.warn("NotLoginException: {}", exception.getMessage());
        return Result.error(ErrorCode.NOT_LOGIN_ERROR);
    }

    /**
     * Sa-Token 权限不足（团队空间账号体系）
     */
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException exception) {
        log.warn("NotPermissionException: 缺少权限 {}", exception.getPermission());
        return Result.error(ErrorCode.NO_AUTH_ERROR);
    }

    /**
     * 业务错误
     *
     * @param exception
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException exception) {
        log.error("BusinessException", exception);
        return Result.error(exception.getCode(), exception.getMessage());
    }

    /**
     * 系统错误
     *
     * @param exception
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> businessExceptionHandler(RuntimeException exception) {
        log.error("BusinessException", exception);
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
