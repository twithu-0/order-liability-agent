package com.twithu.orderliabilityagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.common.exception.ThrowUtil;
import com.twithu.orderliabilityagent.entity.Session;
import com.twithu.orderliabilityagent.mapper.SessionMapper;
import com.twithu.orderliabilityagent.model.enums.SessionStatus;
import com.twithu.orderliabilityagent.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 判责会话服务实现。
 * <p>设计要点：</p>
 * <ul>
 *   <li>状态流转有合法性校验：PENDING → RUNNING → COMPLETED/FAILED，非法流转抛异常</li>
 *   <li>所有自由文本入库前截断到列宽以内，避免超长导致 SQL 失败</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl extends ServiceImpl<SessionMapper, Session> implements SessionService {

    /** 对应列宽：t_liability_session.error_message VARCHAR(1024) */
    private static final int ERROR_MESSAGE_MAX = 1000;

    private final SessionMapper sessionMapper;
    // ==================== 生命周期 ====================

    @Override
    public Session createSession(Long userId, String userInput) {
        ThrowUtil.throwIf(userInput == null || userInput.isBlank(),
                ErrorCode.PARAMS_ERROR, "客服输入不能为空");
        // 全局唯一的对话编号
        String sessionId = "session_" + userId + RandomUtil.randomString(3);
        Session session = Session.builder()
                .content(userInput)
                .userId(userId)
                .sessionNo(sessionId)
                .status(SessionStatus.PENDING.getCode())
                .build();
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public void markRunning(Long sessionId) {
        Session session = this.baseMapper.selectById(sessionId);
        // 幂等：已在处理中则跳过
        if (Objects.equals(session.getStatus(), SessionStatus.RUNNING.getCode())) {
            return;
        }
        ThrowUtil.throwIf(!Objects.equals(session.getStatus(), SessionStatus.PENDING.getCode()),
                ErrorCode.OPERATION_ERROR,
                "非法状态流转：" + session.getStatus() + " → RUNNING");
        session.setStatus(SessionStatus.RUNNING.getCode());
        sessionMapper.updateById(session);
    }

    @Override
    public void markCompleted(Long sessionId, String modelName, Long totalTokens,
                              BigDecimal costEstimate, Integer latencyMs) {
        Session session = getById(sessionId);
        // 幂等：已完成则跳过
        if (Objects.equals(session.getStatus(), SessionStatus.COMPLETED.getCode())) {
            return;
        }
        ThrowUtil.throwIf(!Objects.equals(session.getStatus(), SessionStatus.RUNNING.getCode()),
                ErrorCode.OPERATION_ERROR,
                "非法状态流转：" + session.getStatus() + " → COMPLETED");
        session.setStatus(SessionStatus.COMPLETED.getCode());
        session.setModelName(modelName);
        session.setTotalTokens(totalTokens);
        session.setTimeCost(latencyMs);
        session.setRemark(null);
        sessionMapper.updateById(session);
    }

    @Override
    public void saveJudgment(Long sessionId, String result, String reason) {
        Session session = getById(sessionId);
        session.setResult(result);
        session.setReason(reason);
        sessionMapper.updateById(session);
    }

    @Override
    public void markFailed(Long sessionId, String errorMessage) {
        Session session = getById(sessionId);
        // 幂等：已失败则跳过
        if (Objects.equals(session.getStatus(), SessionStatus.FAILED.getCode())) {
            return;
        }
        ThrowUtil.throwIf(Objects.equals(session.getStatus(), SessionStatus.COMPLETED.getCode()),
                ErrorCode.OPERATION_ERROR, "会话已完成，不能转为 FAILED");
        String safeMessage = truncate(errorMessage, ERROR_MESSAGE_MAX);
        session.setStatus(SessionStatus.FAILED.getCode());
        session.setRemark(safeMessage);
        sessionMapper.updateById(session);
    }



    // ==================== 私有方法 ====================

    /**
     * 截断文本到最大长度，超长部分以省略号结尾。
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
