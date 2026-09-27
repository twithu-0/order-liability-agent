package com.twithu.orderliabilityagent.service;

import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.model.dto.UserLoginRequest;
import com.twithu.orderliabilityagent.model.dto.UserRegisterRequest;

/**
 * 用户服务：注册 / 登录 / 注销 / 获取登录用户。
 * <p>登录态存在 HttpSession，因此这四个方法只能在 HTTP 请求线程上调用。</p>
 */
public interface UserService {

    /**
     * 用户注册：校验账号唯一、两次密码一致，密码 BCrypt 哈希后入库。
     *
     * @return 新建用户（不含密码）
     */
    User userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录：校验账号密码，成功后将用户写入当前 HttpSession。
     *
     * @return 登录用户（不含密码）
     */
    User userLogin(UserLoginRequest userLoginRequest);

    /**
     * 注销登录：从当前 HttpSession 移除登录态。
     *
     * @return 是否成功
     */
    boolean userLogout();

    /**
     * 获取登录用户：读取 HttpSession 登录态并回查数据库。
     *
     * @return 当前登录用户
     * @throws com.twithu.orderliabilityagent.common.exception.BusinessException 未登录时抛 NOT_LOGIN_ERROR
     */
    User getLoginUser();
}
