package com.twithu.orderliabilityagent.common;

import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import lombok.Data;
import tools.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.ArrayList;

@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}


