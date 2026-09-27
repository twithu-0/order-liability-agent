package com.twithu.orderliabilityagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求。
 */
@Data
public class UserLoginRequest {

    /** 账号 */
    @NotBlank(message = "账号不能为空")
    private String userAccount;

    /** 密码（明文，服务端 BCrypt 比对后不留存） */
    @NotBlank(message = "密码不能为空")
    private String userPassword;
}
