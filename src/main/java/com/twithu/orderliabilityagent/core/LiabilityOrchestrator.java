package com.twithu.orderliabilityagent.core;

import cn.hutool.json.JSONUtil;
import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import com.twithu.orderliabilityagent.common.exception.BusinessException;
import com.twithu.orderliabilityagent.core.evidence.EvidenceComparator;
import com.twithu.orderliabilityagent.core.evidence.OrderEvidenceTool;
import com.twithu.orderliabilityagent.entity.Session;
import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import com.twithu.orderliabilityagent.service.SessionService;
import com.twithu.orderliabilityagent.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主要任务流程编排器
 * P1 预处理闸门 → P2 LLM 事实抽取 → P3 缺槽追问 → P4 获取证据 → P5 比对证据。
 * P6 规则 RAG 召回 / P7 LLM 判责 / P8 守卫校验待接入。
 */
@Component
public class LiabilityOrchestrator {
    @Resource
    private SessionService sessionService;
    @Resource
    private LiabilityAgent agent;
    @Resource
    private UserService userService;
    @Resource
    private OrderEvidenceTool orderEvidenceTool;

    public BaseResponse<String> execute(String userPrompt, String chatId){
        User user = userService.getLoginUser();
        // 会话记录入库
        Session session = sessionService.createSession(user.getId(), userPrompt);
        sessionService.markRunning(session.getId());
        long start = System.currentTimeMillis();

        try {
            // P1 预处理：只硬校验订单号；渠道/供应商可由证据快照补齐
            PreprocessResult preResult = UserPromptPreprocess.extract(userPrompt);
            if (!preResult.isLiabilityRequest()){
                return complete(session.getId(), start,
                        "请补充订单号（MT开头+8位数字）后再次提问。");
            }

            // P2 LLM 结构化抽取
            ExtractResult extractResult = agent.extract(preResult, chatId);

            // P3 缺槽追问：订单号以正则 hint 为准；争议字段/实际值缺失才追问，
            // 承诺值缺失不追问（从渠道展示快照补齐）
            String orderNo = preResult.getOrderNoHint() != null
                    ? preResult.getOrderNoHint() : extractResult.getOrderNo();
            if (orderNo == null) {
                return complete(session.getId(), start, "请补充订单号（MT开头+8位数字）后再次提问。");
            }
            if (extractResult.getDisputedField() == null || extractResult.getActualValue() == null) {
                return complete(session.getId(), start,
                        "已收到订单 " + orderNo + "，请补充：具体是哪个信息不一致（如床型、早餐、电话），以及客人实际遇到的情况。");
            }

            // P4 获取证据：确定性调用 mock 工具拿四跳快照
            OrderEvidence evidence = orderEvidenceTool.getOrderEvidence(orderNo);

            // 客人实际体验值以本次投诉抽取为准，覆盖/补齐快照中的 customerActual
            Map<String, String> actual = new HashMap<>();
            actual.put(extractResult.getDisputedField(), extractResult.getActualValue());
            evidence = new OrderEvidence(evidence.orderNo(), evidence.channel(), evidence.supplier(),
                    evidence.supplierSource(), evidence.ourTransfer(), evidence.channelDisplay(), actual);

            // P5 比对证据：逐跳 diff 定位 firstBadHop
            FieldDiff diff = EvidenceComparator.diff(extractResult.getDisputedField(), evidence);

            // TODO P6 规则 RAG 召回：dimension metadata 过滤 + pgvector 语义检索 topK
            // TODO P7 LLM 判责：事实 + diff + 候选规则 → JudgmentResult 结构化输出
            // TODO P8 守卫校验：ruleId 存在性/候选集成员/枚举与分支一致性，落 RunTrace

            Map<String, Object> interim = new LinkedHashMap<>();
            interim.put("stage", "P5_EVIDENCE_DIFF");
            interim.put("orderNo", orderNo);
            interim.put("extract", extractResult);
            interim.put("diff", diff);
            return complete(session.getId(), start, JSONUtil.toJsonStr(interim));
        } catch (BusinessException e) {
            sessionService.markFailed(session.getId(), e.getMessage());
            return ResultUtil.success(e.getMessage());
        } catch (Exception e) {
            sessionService.markFailed(session.getId(), e.getMessage());
            return ResultUtil.success("判责流程异常，请稍后重试或转人工处理。");
        }
    }

    private BaseResponse<String> complete(Long sessionId, long start, String data) {
        sessionService.markCompleted(sessionId, null, null, null,
                (int) (System.currentTimeMillis() - start));
        return ResultUtil.success(data);
    }
}
