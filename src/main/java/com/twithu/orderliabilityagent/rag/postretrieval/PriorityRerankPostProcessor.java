package com.twithu.orderliabilityagent.rag.postretrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 检索后重排
 * 综合分 = similarityWeight × 向量相似度 + (1 - similarityWeight) × 归一化规则优先级。
 * 优先级来自 t_rule 分层：合同1000 / 专属500 / 问题100 / 数据流80 / 默认10，
 * 使合同/SLA 类规则在语义相近时优先浮出。重排后按 rule_id 去重并截断到 finalTopK。
 * 权重与 finalTopK 为初始值，待召回评测（Hit@K/MRR）调参。
 */
public class PriorityRerankPostProcessor implements DocumentPostProcessor {

    public static final double DEFAULT_SIMILARITY_WEIGHT = 0.8;
    public static final int DEFAULT_FINAL_TOP_K = 5;
    private static final double MAX_PRIORITY = 1000.0;

    private final int finalTopK;
    private final double similarityWeight;

    public PriorityRerankPostProcessor() {
        this(DEFAULT_FINAL_TOP_K, DEFAULT_SIMILARITY_WEIGHT);
    }

    public PriorityRerankPostProcessor(int finalTopK, double similarityWeight) {
        this.finalTopK = finalTopK;
        this.similarityWeight = similarityWeight;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        List<Document> scored = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            double similarity = doc.getScore() == null ? 0.0 : doc.getScore();
            int priority = doc.getMetadata().get("priority") instanceof Number n ? n.intValue() : 100;
            double rerankScore = similarityWeight * similarity
                    + (1 - similarityWeight) * Math.min(priority, MAX_PRIORITY) / MAX_PRIORITY;
            scored.add(doc.mutate().metadata("rerank_score", rerankScore).build());
        }
        scored.sort(Comparator.comparingDouble(
                (Document d) -> (Double) d.getMetadata().get("rerank_score")).reversed());

        Set<String> seenRuleIds = new HashSet<>();
        List<Document> result = new ArrayList<>();
        for (Document doc : scored) {
            String ruleId = Objects.toString(doc.getMetadata().get("rule_id"), doc.getId());
            if (seenRuleIds.add(ruleId)) {
                result.add(doc);
            }
            if (result.size() >= finalTopK) {
                break;
            }
        }
        return result;
    }
}
