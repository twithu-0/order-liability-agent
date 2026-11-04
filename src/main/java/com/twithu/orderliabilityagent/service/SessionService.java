package com.twithu.orderliabilityagent.service;

import com.twithu.orderliabilityagent.entity.Session;

import java.math.BigDecimal;

/**
 * 判责会话服务。
 * <p>管理 t_liability_session 生命周期（PENDING → RUNNING → COMPLETED/FAILED）
 * 与 t_audit_log 审计写入，供 Orchestrator 及各步骤模块调用。</p>
 */
public interface SessionService {

    /**
     * 创建判责会话。
     *
     * @param userId    归属用户
     * @param chatId    对话页全局 id，同一对话多轮共享；与 t_chat_memory.chat_id 一致
     * @param userInput 用户本轮输入
     */
    Session createSession(Long userId, String chatId, String userInput);

    /**
     * 标记会话开始处理：PENDING → RUNNING。
     * 幂等：已是 RUNNING 时直接返回。
     */
    void markRunning(Long sessionId);

    /**
     * 标记会话完成：RUNNING → COMPLETED，并记录消耗统计。
     *
     * @param sessionId    会话 id
     * @param totalTokens  消耗 token 总数
     * @param costEstimate 成本估算（元），可为 null
     * @param latencyMs    总耗时（毫秒）
     */
    void markCompleted(Long sessionId, Long totalTokens,
                       BigDecimal costEstimate, Integer latencyMs);

    /**
     * 回写判责结论：P8 守卫通过后调用，落面向用户的 result 与判责依据 reason。
     *
     * @param sessionId 会话 id
     * @param result    面向用户的最终结论
     * @param reason    判责依据
     */
    void saveJudgment(Long sessionId, String result, String reason);

    /**
     * 回写代码可信度分：P8 ConfidenceScorer 计算后调用，落 confidence_score（decimal(3,2)）。
     * 与 saveJudgment 分离，避免改动既有结论回写签名；score 为 null 时不落库。
     *
     * @param sessionId 会话 id
     * @param score     代码可信度总分 0~1
     */
    void saveConfidenceScore(Long sessionId, BigDecimal score);

    /**
     * 追加 token 消耗：P9 流式表达在会话 markCompleted 之后才结束，
     * 其用量以增量方式并入 total_tokens，保持 trace 表为唯一事实源、会话表为汇总视图。
     *
     * @param sessionId   会话 id
     * @param extraTokens 本次新增 token 数
     */
    void appendTokens(Long sessionId, long extraTokens);

    /**
     * 标记会话失败：PENDING/RUNNING → FAILED。
     * 幂等：已是 FAILED 时直接返回。
     *
     * @param sessionId    会话 id
     * @param errorMessage 失败原因（超长自动截断）
     */
    void markFailed(Long sessionId, String errorMessage);

}
