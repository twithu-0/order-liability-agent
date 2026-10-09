package com.twithu.orderliabilityagent.common.exception;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtil.error(e.getCode(), e.getMessage());
    }

    /**
     * @RequestBody + @Valid 校验失败：默认会被下面的 RuntimeException 分支之外的逻辑接住，
     * 返回 Spring 原生错误体。这里统一转成 BaseResponse(40000, 具体字段提示)。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> validExceptionHandler(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? e.getMessage() : fieldError.getDefaultMessage();
        log.warn("参数校验失败：{}", message);
        return ResultUtil.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtil.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}

