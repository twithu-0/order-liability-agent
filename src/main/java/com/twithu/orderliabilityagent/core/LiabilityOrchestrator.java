package com.twithu.orderliabilityagent.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import com.twithu.orderliabilityagent.common.exception.BusinessException;
import com.twithu.orderliabilityagent.core.evidence.EvidenceComparator;
import com.twithu.orderliabilityagent.core.evidence.OrderEvidenceTool;
import com.twithu.orderliabilityagent.core.guard.JudgmentGuard;
import com.twithu.orderliabilityagent.entity.Rule;
import com.twithu.orderliabilityagent.entity.RunTrace;
import com.twithu.orderliabilityagent.entity.Session;
import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.JudgmentResult;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import com.twithu.orderliabilityagent.mapper.RuleMapper;
import com.twithu.orderliabilityagent.mapper.RunTraceMapper;
import com.twithu.orderliabilityagent.service.SessionService;
import com.twithu.orderliabilityagent.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 主要任务流程编排器
 * P1 预处理闸门 → P2 LLM 事实抽取 → P3 缺槽追问 → P4 获取证据 → P5 比对证据
 * → P6 规则 RAG 召回（查询变换+检索+重排+MySQL回查） → P7 LLM 判责 → P8 守卫校验。
 * LLM 调用点：P2 抽取、P6 查询变换（压缩/改写）、P7 判责；检索/回查/守卫均为确定性代码。
 */
@Slf4j
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
    @Resource
    private RuleMapper ruleMapper;
    @Resource
    private RunTraceMapper runTraceMapper;
    @Resource
    private JudgmentGuard judgmentGuard;

    public BaseResponse<String> execute(String userPrompt, String chatId){
        User user = userService.getLoginUser();
        // 会话记录入库
        Session session = sessionService.createSession(user.getId(), chatId, userPrompt);
        sessionService.markRunning(session.getId());
        long start = System.currentTimeMillis();

        try {
            // P1 预处理：只硬校验订单号；渠道/供应商可由证据快照补齐
            String compressedPrompt = agent.compressChatHistory(userPrompt, chatId);
            log.info("用户原输入：{}\n压缩对话历史后输入：{}", userPrompt, compressedPrompt);
            PreprocessResult preResult = UserPromptPreprocess.extract(compressedPrompt);
            if (!preResult.isLiabilityRequest()){
                return complete(session, start,
                        "请补充订单号（MT开头+8位数字）后再次提问。");
            }

            OrderEvidence evidence = orderEvidenceTool.getOrderEvidence(preResult.getOrderNoHint());
            preResult.setCustomerHint(evidence.channel());
            preResult.setSupplierHint(evidence.supplier());
            // P2 LLM 结构化抽取：responseEntity 同时拿实体与 usage，token 落 trace
            long t2 = System.currentTimeMillis();
            ResponseEntity<ChatResponse, ExtractResult> extractResponse = agent.extract(preResult, chatId);
            ExtractResult extractResult = extractResponse.entity();
            saveLlmTrace(session, "P2_EXTRACT", extractResponse.response(), t2,
                    "dimension=" + extractResult.getDimension() + ", field=" + extractResult.getDisputedFieldList());

            // P2 后处理：争议字段去重/上限 2，承诺值/实际值与字段按位对齐，防 LLM 输出错位导致越界
            alignDisputedFields(extractResult);

            // P3 缺槽追问：订单号以正则 hint 为准；争议字段/实际值缺失才追问，
            // 承诺值缺失不追问（从渠道展示快照补齐）
            String orderNo = preResult.getOrderNoHint() != null
                    ? preResult.getOrderNoHint() : extractResult.getOrderNo();
            if (orderNo == null) {
                return complete(session, start, "请补充订单号（MT开头+8位数字）后再次提问。");
            }
            List<String> disputedFields = extractResult.getDisputedFieldList();
            if (CollUtil.isEmpty(disputedFields)) {
                return complete(session, start,
                        "已收到订单 " + orderNo + "，请补充：具体是哪个信息不一致（如床型、早餐、电话），以及客人实际遇到的情况。");
            }
            // 逐字段追问：每个争议点都有实际值才进入比对
            List<String> actualValues = extractResult.getActualValueList();
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < disputedFields.size(); i++) {
                if (!StringUtils.hasText(actualValues.get(i))) {
                    missing.add(disputedFields.get(i));
                }
            }
            if (!missing.isEmpty()) {
                return complete(session, start,
                        "已收到订单 " + orderNo + "，请补充以下争议点客人实际遇到的情况：" + String.join("、", missing));
            }

            // P4 获取证据：确定性调用 mock 工具拿四跳快照
            long t4 = System.currentTimeMillis();
            saveTrace(session, "P4_EVIDENCE", "getOrderEvidence",  t4, "orderNo=" + orderNo);

            // 客人实际体验值以本次投诉抽取为准，覆盖/补齐快照中的 customerActual
            Map<String, String> actual = new HashMap<>();
            for (int i = 0; i < disputedFields.size(); i++) {
                actual.put(disputedFields.get(i), actualValues.get(i));
            }

            evidence = new OrderEvidence(evidence.orderNo(), evidence.channel(), evidence.supplier(),
                    evidence.supplierSource(), evidence.ourTransfer(), evidence.channelDisplay(), actual);

            // P5 比对证据：每个争议字段逐跳 diff，整体 firstBadHop 取最靠前出错环节
            long t5 = System.currentTimeMillis();
            List<FieldDiff> diffList = new ArrayList<>();
            for (String disputedField : disputedFields) {
                diffList.add(EvidenceComparator.diff(disputedField, evidence));
            }
            String aggregatedHop = EvidenceComparator.aggregateHop(diffList);
            StringJoiner hopDetail = new StringJoiner(", ");
            for (FieldDiff d : diffList) {
                hopDetail.add(d.field() + ":" + d.firstBadHop());
            }
            saveTrace(session, "P5_DIFF", null, t5,
                    "firstBadHop=[" + hopDetail + "], aggregated=" + aggregatedHop);

            // P6 规则召回：查询变换 → 宽召回+重排（零 LLM）→ 按 rule_id 回查 MySQL 拿规则全文
            long t6 = System.currentTimeMillis();
            String retrievalQuery = agent.transformQuery(compressedPrompt);
            List<Document> candidates = agent.retrieveRules(retrievalQuery, extractResult.getDimension());
            List<String> ruleIds = candidates.stream()
                    .map(d -> Objects.toString(d.getMetadata().get("rule_id"), null))
                    .filter(Objects::nonNull)
                    .toList();
            saveTrace(session, "P6_RETRIEVAL", "pgvector",  t6, "候选规则=" + ruleIds);
            if (ruleIds.isEmpty()) {
                return complete(session, start, "未检索到相关规则，无法判责，请转人工处理。");
            }
            List<Rule> rules = ruleMapper.selectList(new LambdaQueryWrapper<Rule>()
                    .in(Rule::getRuleId, ruleIds)
                    .eq(Rule::getStatus, 1));

            // P7 LLM 判责：事实 + diff + 规则全文 → 结构化判决（同步主流程最后一次 LLM 调用；流式路径另有 P9 表达）
            long t7 = System.currentTimeMillis();
            ResponseEntity<ChatResponse, JudgmentResult> judgeResponse = agent.judge(extractResult.getComplaint(), diffList, rules, chatId);
            JudgmentResult judgment = judgeResponse.entity();
            saveLlmTrace(session, "P7_JUDGE", judgeResponse.response(), t7,
                    "ruleId=" + judgment.getRuleId() + ", liability=" + judgment.getLiability());

            // P8 守卫校验：引用合法性 + 分支一致性 + 置信度，纯代码
            long t8 = System.currentTimeMillis();
            JudgmentGuard.GuardReport guard = judgmentGuard.check(judgment, rules, diffList);
            saveTrace(session, "P8_GUARD", null, t8,
                    "needsReview=" + guard.needsReview() + ", issues=" + guard.issues());

            // 判责结论回写 t_session：result 面向用户（LLM 漏输出时代码兜底），reason 为判责依据
            String resp = userFacingResult(judgment, rules);
            sessionService.saveJudgment(session.getId(), resp, judgment.getReason());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stage", "P8_JUDGED");
            result.put("orderNo", orderNo);
            result.put("diffList", diffList);
            result.put("judgment", judgment);
            result.put("needsReview", guard.needsReview());
            result.put("guardIssues", guard.issues());
            result.put("response", resp);
            return complete(session, start, resp);
        } catch (BusinessException e) {
            // 失败回复也写 result，保证前端聊天历史能完整回放本轮对话
            log.error("判责流程出现系统异常：",e);
            sessionService.saveJudgment(session.getId(), e.getMessage(), null);
            sessionService.markFailed(session.getId(), e.getMessage());
            return ResultUtil.success(e.getMessage());
        } catch (Exception e) {
            log.error("判责流程出现系统异常：",e);
            sessionService.saveJudgment(session.getId(), "判责流程异常，请稍后重试或转人工处理。", null);
            sessionService.markFailed(session.getId(), e.getMessage());
            return ResultUtil.success("判责流程异常，请稍后重试或转人工处理。");
        }
    }

    /**
     * 会话完成：先按 need_token_record=1 汇总 LLM 步骤的 token 消耗回写 t_session，再标记 COMPLETED。
     * 以 trace 表为唯一事实源，后续新增 LLM 步骤只要落 trace 就自动纳入汇总。
     */
    private BaseResponse<String> complete(Session session, long start, String data) {
        // 面向用户的回复统一落 result（含 P1/P3 追问、无规则等非 P8 路径），供聊天历史展示；
        // P8 成功路径已带 reason 写过一次，这里 reason 传 null 不会覆盖（MyBatis-Plus 忽略 null 字段）
        sessionService.saveJudgment(session.getId(), data, null);
        List<RunTrace> llmTraces = runTraceMapper.selectList(new LambdaQueryWrapper<RunTrace>()
                .eq(RunTrace::getSessionId, session.getId())
                .eq(RunTrace::getNeedTokenRecord, 1)
                .orderByAsc(RunTrace::getId));
        long totalTokens = llmTraces.stream().mapToLong(RunTrace::getTotalTokens).sum();
        sessionService.markCompleted(session.getId(), totalTokens, null,
                (int) (System.currentTimeMillis() - start));
        return ResultUtil.success(data);
    }

    /** 写非 LLM 步骤轨迹（工具/检索/守卫）：无模型调用，token 记 0，needTokenRecord=0 */
    private void saveTrace(Session session, String step, String tool,
                           long startMs, String detail) {
        insertTrace(session, step, tool, null, 0L, 0L, 0L, startMs, detail);
    }

    /**
     * 写 LLM 步骤轨迹（P2 抽取 / P7 判责）：model 与 token 直接取自 ChatResponse 元数据，
     * 与 TokenUsageAdvisor 日志同源；provider 未上报 usage 时记 0（DDL 列 NOT NULL）。
     */
    private void saveLlmTrace(Session session, String step, ChatResponse response,
                              long startMs, String detail) {
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        String model = metadata == null ? null : metadata.getModel();
        long prompt = usage == null ? 0L : toLong(usage.getPromptTokens());
        long completion = usage == null ? 0L : toLong(usage.getCompletionTokens());
        long total = usage == null ? 0L : toLong(usage.getTotalTokens());
        if (total == 0L) {
            total = prompt + completion;
        }
        insertTrace(session, step, null, model, prompt, completion, total, startMs, detail);
    }

    private void insertTrace(Session session, String step, String tool, String model,
                             long promptTokens, long completionTokens, long totalTokens,
                             long startMs, String detail) {
        RunTrace trace = RunTrace.builder()
                .sessionId(session.getId())
                .step(step)
                .tool(tool)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .timeCost((int) (System.currentTimeMillis() - startMs))
                .detail(detail)
                .build();
        // 是否统计 token 以模型调用为准，复用实体的 isModelStep 判定
        trace.setNeedTokenRecord(trace.isModelStep() ? 1 : 0);
        runTraceMapper.insert(trace);
    }

    private static long toLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    /**
     * P2 输出归一化：争议字段去空白、去重、上限 2；
     * 承诺值/实际值列表与字段按位对齐（短缺补 null，超长截断）。包级可见供单测。
     */
    static void alignDisputedFields(ExtractResult r) {
        List<String> fields = r.getDisputedFieldList();
        if (CollUtil.isEmpty(fields)) {
            return;
        }
        List<String> cleaned = fields.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(2)
                .toList();
        r.setDisputedFieldList(cleaned);
        r.setPromisedValueList(alignValues(r.getPromisedValueList(), cleaned.size()));
        r.setActualValueList(alignValues(r.getActualValueList(), cleaned.size()));
    }

    private static List<String> alignValues(List<String> values, int size) {
        List<String> aligned = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            aligned.add(values != null && i < values.size() ? values.get(i) : null);
        }
        return aligned;
    }

    /**
     * 面向用户结论：优先用 LLM 的 result；缺失时按责任枚举中文名+引用规则确定性拼装，
     * 保证 t_session.result 非空。守卫已硬校验 ruleId/liability 非空。包级可见供单测。
     */
    static String userFacingResult(JudgmentResult judgment, List<Rule> rules) {
        if (StringUtils.hasText(judgment.getResult())) {
            return judgment.getResult();
        }
        String ruleName = rules.stream()
                .filter(r -> Objects.equals(r.getRuleId(), judgment.getRuleId()))
                .map(Rule::getName)
                .findFirst()
                .orElse("");
        return "我认为是" + judgment.getLiability().getDesc() + "。引用规则 "
                + judgment.getRuleId() + " " + ruleName + "。结论仅供参考，如有异议请人工复核。";
    }
}
