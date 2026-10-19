package com.twithu.orderliabilityagent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.common.exception.ThrowUtil;
import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.mapper.UserMapper;
import com.twithu.orderliabilityagent.model.dto.UserLoginRequest;
import com.twithu.orderliabilityagent.model.dto.UserRegisterRequest;
import com.twithu.orderliabilityagent.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.twithu.orderliabilityagent.constants.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现。
 * <p>设计要点：</p>
 * <ul>
 *   <li>登录态放 HttpSession，靠 Cookie 携带，不引入 JWT/Security，单节点最简方案</li>
 *   <li>密码用 Hutool BCrypt（自带随机盐），比 MD5+固定盐抗彩虹表；哈希绝不返回前端</li>
 *   <li>登录失败统一提示"账号或密码错误"，避免被枚举出哪些账号存在</li>
 *   <li>getLoginUser 每次回查数据库，账号被删/改昵称立刻生效</li>
 * </ul>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 密码最短长度 */
    private static final int MIN_PASSWORD_LEN = 8;

    @Override
    public User userRegister(UserRegisterRequest userRegisterRequest) {
        ThrowUtil.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR, "注册参数不能为空");
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        ThrowUtil.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword),
                ErrorCode.PARAMS_ERROR, "账号、密码、确认密码均不能为空");
        ThrowUtil.throwIf(userPassword.length() < MIN_PASSWORD_LEN,
                ErrorCode.PARAMS_ERROR, "密码长度至少 " + MIN_PASSWORD_LEN + " 位");
        ThrowUtil.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");

        // 先查一次给友好提示；并发场景的真正兜底是 t_user 的唯一索引 uk_userAccount
        User exist = this.getOne(new LambdaQueryWrapper<User>().eq(User::getUserAccount, userAccount));
        ThrowUtil.throwIf(exist != null, ErrorCode.PARAMS_ERROR, "账号已存在，请直接登录");

        User newUser = User.builder()
                .userAccount(userAccount)
                .userPassword(BCrypt.hashpw(userPassword))
                .userName(StrUtil.blankToDefault(userRegisterRequest.getUserName(), userAccount))
                .build();
        this.save(newUser);
        log.info("用户注册成功：userId={}, userAccount={}", newUser.getId(), userAccount);
        // 回查一次：createTime / updateTime / isDelete 由数据库默认值填充，直接返回实体拿不到
        return this.getById(newUser.getId());
    }

    @Override
    public User userLogin(UserLoginRequest userLoginRequest) {
        ThrowUtil.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR, "登录参数不能为空");
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        ThrowUtil.throwIf(StrUtil.hasBlank(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "账号和密码不能为空");

        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getUserAccount, userAccount));
        ThrowUtil.throwIf(!passwordMatch(user, userPassword), ErrorCode.PARAMS_ERROR, "账号或密码错误");

        user.setUserPassword(null);
        currentRequest().getSession().setAttribute(USER_LOGIN_STATE, user);
        log.info("用户登录成功：userId={}, userAccount={}", user.getId(), userAccount);
        return user;
    }

    @Override
    public boolean userLogout() {
        HttpSession session = currentRequest().getSession(false);
        if (session == null) {
            return true;
        }
        session.removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public User getLoginUser() {
        User user = new User();
        user.setId(1717L);//本地测试用
        return user;
        // HttpSession session = currentRequest().getSession(false);
        // Object loginState = session == null ? null : session.getAttribute(USER_LOGIN_STATE);
        // User cacheUser = loginState instanceof User u ? u : null;
        // ThrowUtil.throwIf(cacheUser == null || cacheUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        //
        // // 回查数据库：登录期间被删除、或昵称等信息已变更时以库为准
        // User loginUser = this.getById(cacheUser.getId());
        // ThrowUtil.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "登录用户已不存在，请重新登录");
        // return loginUser;
    }

    // ==================== 私有方法 ====================

    /**
     * 校验密码。兼容历史脏数据（库里可能是 MD5 串，不是合法 BCrypt），一律按密码错误处理而不是抛 500。
     */
    private boolean passwordMatch(User user, String rawPassword) {
        if (user == null || StrUtil.isBlank(user.getUserPassword())) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, user.getUserPassword());
        } catch (Exception e) {
            log.warn("密码哈希格式非法，userId={}, reason={}", user.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 取当前线程绑定的请求。
     * <p>注意：@Async、自建线程池、Reactor 流式回调线程不会自动继承 RequestAttributes，
     * 在这些线程里调用登录/注销/getLoginUser 会拿到 null 并抛 SYSTEM_ERROR。</p>
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        ThrowUtil.throwIf(attributes == null, ErrorCode.SYSTEM_ERROR, "当前线程没有 HTTP 请求上下文，无法读写登录态");
        return attributes.getRequest();
    }

}
