package com.twithu.orderliabilityagent.rag.postretrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优先级重排单测：加权排序、截断、去重、缺省值。
 */
class PriorityRerankPostProcessorTest {

    private final PriorityRerankPostProcessor processor = new PriorityRerankPostProcessor();

    private Document doc(String ruleId, double score, int priority) {
        return new Document("text-" + ruleId, Map.of("rule_id", ruleId, "priority", priority))
                .mutate().score(score).build();
    }

    @Test
    void contractRuleOutranksSlightlyMoreSimilarIssueRule() {
        // 合同规则：0.8*0.60 + 0.2*1.0 = 0.68；问题规则：0.8*0.62 + 0.2*0.1 = 0.516
        Document issue = doc("R-02", 0.62, 100);
        Document contract = doc("D-09", 0.60, 1000);
        List<Document> result = processor.process(new Query("q"), List.of(issue, contract));
        assertEquals("D-09", result.get(0).getMetadata().get("rule_id"));
        assertEquals("R-02", result.get(1).getMetadata().get("rule_id"));
        assertNotNull(result.get(0).getMetadata().get("rerank_score"));
    }

    @Test
    void truncatesToFinalTopK() {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            docs.add(doc("R-" + i, 0.9 - i * 0.05, 100));
        }
        assertEquals(5, processor.process(new Query("q"), docs).size());
    }

    @Test
    void deduplicatesByRuleIdKeepingHigherScore() {
        Document low = doc("R-02", 0.50, 100);
        Document high = doc("R-02", 0.80, 100);
        List<Document> result = processor.process(new Query("q"), List.of(low, high));
        assertEquals(1, result.size());
        assertEquals(0.80, result.get(0).getScore(), 1e-9);
    }

    @Test
    void missingPriorityAndScoreUseDefaults() {
        Document bare = new Document("text", Map.of("rule_id", "X-01"));
        List<Document> result = processor.process(new Query("q"), List.of(bare));
        // 缺省 priority=100、score=0 → 0.8*0 + 0.2*0.1 = 0.02
        assertEquals(0.02, (Double) result.get(0).getMetadata().get("rerank_score"), 1e-9);
    }

    @Test
    void customWeightChangesOrdering() {
        // 权重 0.5 时优先级影响放大：0.5*0.60+0.5*1.0=0.80 > 0.5*0.70+0.5*0.1=0.40
        PriorityRerankPostProcessor heavy = new PriorityRerankPostProcessor(5, 0.5);
        List<Document> result = heavy.process(new Query("q"),
                List.of(doc("R-02", 0.70, 100), doc("D-09", 0.60, 1000)));
        assertEquals("D-09", result.get(0).getMetadata().get("rule_id"));
    }
}
