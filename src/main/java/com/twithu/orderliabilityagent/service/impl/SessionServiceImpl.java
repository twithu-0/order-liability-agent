package com.twithu.orderliabilityagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.twithu.orderliabilityagent.common.exception.BusinessException;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 判责会话服务实现。
 * <p>设计要点：</p>
 * <ul>
 *   <li>状态流转有合法性校验：PENDING → RUNNING → COMPLETED/FAILED，非法流转抛异常</li>
 *   <li>审计写入为 best-effort：审计失败只记 error 日志，不阻断判责主流程</li>
 *   <li>所有自由文本入库前截断到列宽以内，避免超长导致 SQL 失败</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    /** 会话编号日期部分格式 */
    private static final DateTimeFormatter SESSION_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** sessionNo 冲突重试次数（随机尾号撞唯一键的概率极低，重试只为兜底） */
    private static final int SESSION_NO_MAX_RETRY = 5;

    /** 对应列宽：t_liability_session.error_message VARCHAR(1024) */
    private static final int ERROR_MESSAGE_MAX = 1000;

    /** 审计 content 为 MEDIUMTEXT，防御性截断到 60KB */
    private static final int AUDIT_CONTENT_MAX = 60_000;

    private final SessionMapper sessionMapper;
    // ==================== 生命周期 ====================

    @Override
    public Session createSession(String userInput, String caseNo) {
        ThrowUtil.throwIf(userInput == null || userInput.isBlank(),
                ErrorCode.PARAMS_ERROR, "客服输入不能为空");
        Session session = Session.builder()
                .sessionNo(generateSessionNo())
                .content(userInput)
                .status(SessionStatus.PENDING.getCode())
                .build();
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public void markRunning(Long sessionId) {
        Session session = getById(sessionId);
        // 幂等：已在处理中则跳过
        if (session.getStatus() == SessionStatus.RUNNING.getCode()) {
            return;
        }
        ThrowUtil.throwIf(session.getStatus() != SessionStatus.PENDING.getCode(),
                ErrorCode.OPERATION_ERROR,
                "非法状态流转：" + session.getStatus() + " → RUNNING");
        session.setStatus(SessionStatus.RUNNING.getCode());
        sessionMapper.updateById(session);
    }

    @Override
    public void markCompleted(Long sessionId, String modelName, Integer totalTokens,
                              BigDecimal costEstimate, Integer latencyMs) {
        Session session = getById(sessionId);
        // 幂等：已完成则跳过
        if (session.getStatus() == SessionStatus.COMPLETED.getCode()) {
            return;
        }
        ThrowUtil.throwIf(session.getStatus() != SessionStatus.RUNNING.getCode(),
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
    public void markFailed(Long sessionId, String errorMessage) {
        Session session = getById(sessionId);
        // 幂等：已失败则跳过
        if (session.getStatus() == SessionStatus.FAILED.getCode()) {
            return;
        }
        ThrowUtil.throwIf(session.getStatus() == SessionStatus.COMPLETED.getCode(),
                ErrorCode.OPERATION_ERROR, "会话已完成，不能转为 FAILED");
        String safeMessage = truncate(errorMessage, ERROR_MESSAGE_MAX);
        session.setStatus(SessionStatus.FAILED.getCode());
        session.setRemark(safeMessage);
        sessionMapper.updateById(session);
    }

    // ==================== 查询 ====================

    @Override
    public Session getById(Long sessionId) {
        ThrowUtil.throwIf(sessionId == null, ErrorCode.PARAMS_ERROR, "sessionId 不能为空");
        Session session = sessionMapper.selectById(sessionId);
        ThrowUtil.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在：id=" + sessionId);
        return session;
    }

    @Override
    public Session getBySessionNo(String sessionNo) {
        ThrowUtil.throwIf(sessionNo == null || sessionNo.isBlank(),
                ErrorCode.PARAMS_ERROR, "sessionNo 不能为空");
        Session session = sessionMapper.selectOne(new LambdaQueryWrapper<Session>()
                .eq(Session::getSessionNo, sessionNo));
        ThrowUtil.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在：sessionNo=" + sessionNo);
        return session;
    }

    // ==================== 私有方法 ====================

    /**
     * 生成唯一会话编号：SES-yyyyMMdd-6位随机数字。
     */
    private String generateSessionNo() {
        for (int i = 0; i < SESSION_NO_MAX_RETRY; i++) {
            String candidate = "SES-" + LocalDate.now().format(SESSION_DATE_FMT)
                    + "-" + RandomUtil.randomNumbers(6);
            Long count = sessionMapper.selectCount(new LambdaQueryWrapper<Session>()
                    .eq(Session::getSessionNo, candidate));
            if (count == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话编号生成失败，请重试");
    }


    /**
     * 对象序列化为 JSON；null 返回 null，序列化失败降级为 toString。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return truncate(s, AUDIT_CONTENT_MAX);
        }
        try {
            return truncate(JSONUtil.toJsonStr(value), AUDIT_CONTENT_MAX);
        } catch (Exception e) {
            return truncate(String.valueOf(value), AUDIT_CONTENT_MAX);
        }
    }

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
