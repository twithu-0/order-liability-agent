package com.twithu.orderliabilityagent.core.guard;

import com.twithu.orderliabilityagent.common.exception.BusinessException;
import com.twithu.orderliabilityagent.entity.Rule;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.JudgmentResult;
import com.twithu.orderliabilityagent.model.enums.Liability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P8 守卫单测：硬违规抛异常，软风险标记复核。纯单元测试，不启动 Spring 上下文。
 */
class JudgmentGuardTest {

    private final JudgmentGuard guard = new JudgmentGuard();

    private static final String BRANCHES = """
            [{"when":{"firstBadHop":"OURS"},"liability":"OURS_PRIMARY"},
             {"when":{"firstBadHop":"CHANNEL"},"liability":"CHANNEL_PRIMARY"},
             {"when":{"firstBadHop":"SUPPLIER"},"liability":"SUPPLIER_PRIMARY"},
             {"when":{"firstBadHop":"NONE"},"liability":"UNDETERMINED"},
             {"when":{"firstBadHop":"EVIDENCE_GAP"},"liability":"UNDETERMINED"}]
            """;

    private Rule rule() {
        return Rule.builder().ruleId("R-02").branchesJson(BRANCHES).status(1).build();
    }

    private JudgmentResult result(String ruleId, Liability liability, double confidence) {
        JudgmentResult r = new JudgmentResult();
        r.setRuleId(ruleId);
        r.setLiability(liability);
        r.setSecondaryLiability(Liability.NONE);
        r.setReason("渠道展示偏离我司下发数据");
        r.setConfidence(confidence);
        return r;
    }

    private List<FieldDiff> diffs(String hop) {
        return List.of(new FieldDiff("bedType", "大床", "大床", "双床", "双床", hop));
    }

    @Test
    void consistentJudgmentPasses() {
        JudgmentGuard.GuardReport report = guard.check(
                result("R-02", Liability.CHANNEL_PRIMARY, 0.9), List.of(rule()), diffs("CHANNEL"));
        assertFalse(report.needsReview());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void hallucinatedRuleIdThrows() {
        BusinessException e = assertThrows(BusinessException.class, () -> guard.check(
                result("R-99", Liability.CHANNEL_PRIMARY, 0.9), List.of(rule()), diffs("CHANNEL")));
        assertTrue(e.getMessage().contains("未召回"));
    }

    @Test
    void deviationWithReasonFlagsReview() {
        JudgmentGuard.GuardReport report = guard.check(
                result("R-02", Liability.SUPPLIER_PRIMARY, 0.9), List.of(rule()), diffs("CHANNEL"));
        assertTrue(report.needsReview());
        assertTrue(report.issues().stream().anyMatch(s -> s.contains("不一致")));
    }

    @Test
    void deviationWithoutReasonThrows() {
        JudgmentResult r = result("R-02", Liability.SUPPLIER_PRIMARY, 0.9);
        r.setReason(null);
        assertThrows(BusinessException.class, () -> guard.check(r, List.of(rule()), diffs("CHANNEL")));
    }

    @Test
    void lowConfidenceFlagsReview() {
        JudgmentGuard.GuardReport report = guard.check(
                result("R-02", Liability.CHANNEL_PRIMARY, 0.4), List.of(rule()), diffs("CHANNEL"));
        assertTrue(report.needsReview());
    }

    @Test
    void evidenceGapFlagsReview() {
        JudgmentGuard.GuardReport report = guard.check(
                result("R-02", Liability.UNDETERMINED, 0.9), List.of(rule()), diffs("EVIDENCE_GAP"));
        assertTrue(report.needsReview());
    }

    @Test
    void missingLiabilityThrows() {
        JudgmentResult r = result("R-02", null, 0.9);
        assertThrows(BusinessException.class, () -> guard.check(r, List.of(rule()), diffs("CHANNEL")));
    }

    @Test
    void multiFieldAggregatedHopDrivesBranch() {
        // bedType=CHANNEL、breakfast=OURS → 聚合 OURS，分支期望 OURS_PRIMARY
        FieldDiff bed = new FieldDiff("bedType", "大床", "大床", "双床", "双床", "CHANNEL");
        FieldDiff breakfast = new FieldDiff("breakfast", "含早", "无早", "无早", "无早餐", "OURS");
        JudgmentGuard.GuardReport report = guard.check(
                result("R-02", Liability.OURS_PRIMARY, 0.9), List.of(rule()), List.of(bed, breakfast));
        assertFalse(report.needsReview());
        assertTrue(report.issues().isEmpty());
    }
}
