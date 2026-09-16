package com.li.apicturebackend.exception;

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
