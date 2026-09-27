package com.twithu.orderliabilityagent.controller;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.common.exception.ThrowUtil;
import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.model.dto.UserLoginRequest;
import com.twithu.orderliabilityagent.model.dto.UserRegisterRequest;
import com.twithu.orderliabilityagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录注册接口。
 * <p>登录态走 HttpSession（浏览器/Cookie 自动携带），
 * 前端跨域调用需带 {@code withCredentials}，服务端 CorsConfig 已 allowCredentials(true)。</p>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册：POST /api/user/register
     */
    @PostMapping("/register")
    public BaseResponse<User> userRegister(@RequestBody @Valid UserRegisterRequest userRegisterRequest) {
        ThrowUtil.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtil.success(userService.userRegister(userRegisterRequest));
    }

    /**
     * 用户登录：POST /api/user/login
     */
    @PostMapping("/login")
    public BaseResponse<User> userLogin(@RequestBody @Valid UserLoginRequest userLoginRequest) {
        ThrowUtil.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtil.success(userService.userLogin(userLoginRequest));
    }

    /**
     * 注销登录：POST /api/user/logout
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout() {
        return ResultUtil.success(userService.userLogout());
    }

    /**
     * 获取登录用户：GET /api/user/get/login
     */
    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser() {
        return ResultUtil.success(userService.getLoginUser());
    }
}
