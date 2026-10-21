package com.twithu.orderliabilityagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求。
 */
@Data
public class UserRegisterRequest {

    /** 账号：字母、数字、下划线，4~25 位 */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,25}$", message = "账号只能包含字母、数字、下划线，长度 4~25")
    private String userAccount;

    /** 密码：4~20 位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 4, max = 20, message = "密码长度需在 4~20 位之间")
    private String userPassword;

    /** 确认密码：必须与密码一致 */
    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

    /** 用户昵称：可为空，为空时取账号 */
    @Size(max = 20, message = "昵称最长 20 位")
    private String userName;
}
