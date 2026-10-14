package com.twithu.orderliabilityagent.rag.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 领域 enricher 单测：别名扩充与元数据保真。纯单元测试，不启动 Spring 上下文。
 */
class RuleMetadataEnricherTest {

    private final RuleMetadataEnricher enricher = new RuleMetadataEnricher(JsonMapper.builder().build());

    @Test
    void appendsAliasesForKnownIssueType() {
        Document doc = new Document("## R-02 床型不一致",
                Map.of("rule_id", "R-02", "issue_type", "BED_TYPE_MISMATCH", "dimension", "room_type"));
        List<Document> result = enricher.apply(List.of(doc));
        assertEquals(1, result.size());
        assertTrue(result.get(0).getText().contains("口语表达"));
        assertTrue(result.get(0).getText().contains("twin"));
        assertTrue(result.get(0).getText().startsWith("## R-02"));
        assertEquals("R-02", result.get(0).getMetadata().get("rule_id"));
        assertEquals("room_type", result.get(0).getMetadata().get("dimension"));
    }

    @Test
    void unknownIssueTypePassesThroughUnchanged() {
        Document doc = new Document("原文", Map.of("issue_type", "NOT_IN_MAP"));
        List<Document> result = enricher.apply(List.of(doc));
        assertEquals("原文", result.get(0).getText());
    }

    @Test
    void missingIssueTypePassesThroughUnchanged() {
        Document doc = new Document("原文", Map.of("rule_id", "X-01"));
        assertEquals("原文", enricher.apply(List.of(doc)).get(0).getText());
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertTrue(enricher.apply(List.of()).isEmpty());
    }
}
