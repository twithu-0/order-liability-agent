package com.twithu.orderliabilityagent.constants;

/**
 * 用户模块常量。
 */
public class UserConstant {

    /**
     * 登录态在 HttpSession 中的属性 key。
     * 登录成功写入，注销时移除，getLoginUser 读取。
     */
    public static final String USER_LOGIN_STATE = "user_login";

    private UserConstant() {
    }
}
