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
     * 创建判责会话（状态 PENDING，生成唯一 sessionNo）。
     *
     * @param userInput 客服输入原文
     * @param caseNo    关联案例编号（评测场景使用，可为 null）
     * @return 新建会话（含自增 id）
     */
    Session createSession(String userInput, String caseNo);

    /**
     * 标记会话开始处理：PENDING → RUNNING。
     * 幂等：已是 RUNNING 时直接返回。
     */
    void markRunning(Long sessionId);

    /**
     * 标记会话完成：RUNNING → COMPLETED，并记录模型统计。
     *
     * @param sessionId    会话 id
     * @param modelName    使用的模型名
     * @param totalTokens  消耗 token 总数
     * @param costEstimate 成本估算（元），可为 null
     * @param latencyMs    总耗时（毫秒）
     */
    void markCompleted(Long sessionId, String modelName, Integer totalTokens,
                       BigDecimal costEstimate, Integer latencyMs);

    /**
     * 标记会话失败：PENDING/RUNNING → FAILED。
     * 幂等：已是 FAILED 时直接返回。
     *
     * @param sessionId    会话 id
     * @param errorMessage 失败原因（超长自动截断）
     */
    void markFailed(Long sessionId, String errorMessage);

    /**
     * 按主键查询会话，不存在抛 NOT_FOUND_ERROR。
     */
    Session getById(Long sessionId);

    /**
     * 按会话编号查询会话，不存在抛 NOT_FOUND_ERROR。
     */
    Session getBySessionNo(String sessionNo);

}
