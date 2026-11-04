package com.twithu.orderliabilityagent.core.guard;

import com.twithu.orderliabilityagent.common.exception.BusinessException;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.common.exception.ThrowUtil;
import com.twithu.orderliabilityagent.core.evidence.EvidenceComparator;
import com.twithu.orderliabilityagent.entity.Rule;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.JudgmentResult;
import com.twithu.orderliabilityagent.model.enums.Liability;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P8 守卫校验：纯代码兜底，不调 LLM。
 * 硬违规（抛 BusinessException，流程终止）：
 * - 判决为空 / ruleId 为空 / liability 为空
 * - ruleId 不在本次召回候选集内（防幻觉引用）
 * - 与分支裁决不一致且未给出理由
 * 软风险（标记 needsReview，结论仍可输出但转人工复核）：
 * - 与分支裁决不一致但给出了理由
 * - confidence 低于 0.6
 * - firstBadHop 为 NONE / EVIDENCE_GAP
 * 多字段争议（最多 2 个）：分支一致性与复核判定均按聚合 firstBadHop（最靠前出错环节）。
 * 特殊规则豁免：去哪儿/大众点评 CHANNEL 跳改判我司时，分支不一致不判违规（SpecialRulePolicy）。
 * 置信度来源：传入 ConfidenceReport 时用代码分，否则回退 LLM 自报值（兼容旧调用）。
 */
@Component
public class JudgmentGuard {

    private static final double CONFIDENCE_THRESHOLD = 0.6;

    /** 守卫报告：needsReview 为 true 时结论附人工复核标记 */
    public record GuardReport(boolean needsReview, List<String> issues) {
    }

    /** 兼容旧调用：无渠道（不豁免）、无代码分（回退 LLM 自报置信度） */
    public GuardReport check(JudgmentResult result, List<Rule> candidates, List<FieldDiff> diffList) {
        return check(result, candidates, diffList, null, null);
    }

    /**
     * 完整校验。
     * - channel：订单 DB 渠道，用于特殊规则豁免判定
     * - confidence：P8 代码可信度报告；非 null 时置信度软风险按代码分判定，null 回退 LLM 自报值
     */
    public GuardReport check(JudgmentResult result, List<Rule> candidates, List<FieldDiff> diffList,
                             String channel, ConfidenceScorer.ConfidenceReport confidence) {
        ThrowUtil.throwIf(result == null, ErrorCode.OPERATION_ERROR, "判责结果为空");
        ThrowUtil.throwIf(result.getRuleId() == null || result.getRuleId().isBlank(),
                ErrorCode.OPERATION_ERROR, "判责结果缺少 ruleId");
        ThrowUtil.throwIf(result.getLiability() == null,
                ErrorCode.OPERATION_ERROR, "判责结果缺少责任方");

        Rule hit = candidates.stream()
                .filter(r -> Objects.equals(r.getRuleId(), result.getRuleId()))
                .findFirst()
                .orElse(null);
        ThrowUtil.throwIf(hit == null, ErrorCode.OPERATION_ERROR,
                "引用了未召回的规则 " + result.getRuleId() + "，疑似幻觉，转人工处理");

        List<String> issues = new ArrayList<>();
        boolean needsReview = false;

        // 分支一致性：多字段取聚合 firstBadHop（最靠前出错环节），查规则分支的期望责任方
        String hop = EvidenceComparator.aggregateHop(diffList);
        Liability expected = ConfidenceScorer.branchExpectation(hit, hop);
        // 特殊规则豁免：特殊渠道 CHANNEL 跳改判我司，分支不一致属预期，不判违规
        boolean specialOverride = SpecialRulePolicy.isOverrideApplicable(channel, hop, result.getLiability());
        if (expected != null && expected != result.getLiability() && !specialOverride) {
            ThrowUtil.throwIf(result.getReason() == null || result.getReason().isBlank(),
                    ErrorCode.OPERATION_ERROR,
                    "判决与规则分支不一致（期望 " + expected + "）且未给出理由");
            issues.add("判决 " + result.getLiability() + " 与分支期望 " + expected + " 不一致，已附理由");
            needsReview = true;
        }

        // 置信度：优先代码分，回退 LLM 自报值
        double effectiveConfidence = confidence != null ? confidence.score()
                : (result.getConfidence() == null ? 0.0 : result.getConfidence());
        if (effectiveConfidence < CONFIDENCE_THRESHOLD) {
            issues.add("置信度不足 " + CONFIDENCE_THRESHOLD);
            needsReview = true;
        }
        if (EvidenceComparator.HOP_NONE.equals(hop) || EvidenceComparator.HOP_EVIDENCE_GAP.equals(hop)) {
            issues.add("证据链路 " + hop + "，结论需人工复核");
            needsReview = true;
        }
        return new GuardReport(needsReview, issues);
    }
}
