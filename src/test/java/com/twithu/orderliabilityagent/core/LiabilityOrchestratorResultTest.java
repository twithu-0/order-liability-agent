package com.twithu.orderliabilityagent.core;

import com.twithu.orderliabilityagent.entity.Rule;
import com.twithu.orderliabilityagent.model.dto.JudgmentResult;
import com.twithu.orderliabilityagent.model.enums.Liability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面向用户结论单测：LLM result 优先，缺失时按枚举中文名+引用规则确定性拼装。
 * 纯单元测试，不启动 Spring 上下文。
 */
class LiabilityOrchestratorResultTest {

    private JudgmentResult judgment(String result) {
        JudgmentResult j = new JudgmentResult();
        j.setRuleId("R-02");
        j.setLiability(Liability.CHANNEL_PRIMARY);
        j.setResult(result);
        return j;
    }

    private List<Rule> rules() {
        return List.of(Rule.builder().ruleId("R-02").name("床型不一致（大床/双床）").build());
    }

    @Test
    void llmResultPreferred() {
        String text = "我认为是渠道责任。引用规则 R-02 床型不一致。结论仅供参考，如有异议请人工复核。";
        assertEquals(text, LiabilityOrchestrator.userFacingResult(judgment(text), rules()));
    }

    @Test
    void fallbackAssembledWhenMissing() {
        String fallback = LiabilityOrchestrator.userFacingResult(judgment(null), rules());
        assertTrue(fallback.contains("渠道主责"));
        assertTrue(fallback.contains("R-02 床型不一致（大床/双床）"));
        assertTrue(fallback.contains("结论仅供参考，如有异议请人工复核。"));
    }

    @Test
    void blankResultAlsoFallsBack() {
        String fallback = LiabilityOrchestrator.userFacingResult(judgment("  "), rules());
        assertTrue(fallback.contains("引用规则 R-02"));
    }
}
