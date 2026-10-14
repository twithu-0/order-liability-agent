package com.twithu.orderliabilityagent.rag.eval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 召回评测器：只评检索层，不调判责 LLM，便宜、快、可重复。
 * 检索配置（dimension 过滤 + topK + 阈值）与生产 Advisor 保持一致才有对比意义；
 * 传入 {@link DocumentPostProcessor} 时评测"检索+重排"的完整生产路径。
 */
public class RetrievalEvaluator {

    private final VectorStore vectorStore;

    public RetrievalEvaluator(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 纯检索评测 */
    public EvalReport evaluate(String config, List<EvalCase> cases, int topK, double threshold) {
        return evaluate(config, cases, topK, threshold, null);
    }

    /** 检索 + 后处理（重排）评测 */
    public EvalReport evaluate(String config, List<EvalCase> cases, int topK, double threshold,
                               DocumentPostProcessor postProcessor) {
        int hit = 0;
        double mrrSum = 0;
        double recallSum = 0;
        Map<String, int[]> byDimension = new LinkedHashMap<>();
        List<EvalReport.FailureDetail> failures = new ArrayList<>();

        for (EvalCase c : cases) {
            List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(c.query())
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .filterExpression(new FilterExpressionBuilder().eq("dimension", c.dimension()).build())
                    .build());
            if (postProcessor != null) {
                docs = postProcessor.process(new Query(c.query()), docs);
            }

            List<String> got = docs.stream()
                    .map(d -> Objects.toString(d.getMetadata().get("rule_id"), d.getId()))
                    .toList();

            boolean isHit = got.stream().anyMatch(c.expected()::contains);
            if (isHit) {
                hit++;
            } else {
                List<EvalReport.RetrievedRule> retrieved = docs.stream()
                        .map(d -> new EvalReport.RetrievedRule(
                                Objects.toString(d.getMetadata().get("rule_id"), d.getId()),
                                d.getScore() == null ? 0.0 : d.getScore()))
                        .toList();
                failures.add(new EvalReport.FailureDetail(c.query(), c.dimension(), c.expected(), retrieved));
            }

            for (int i = 0; i < got.size(); i++) {
                if (c.expected().contains(got.get(i))) {
                    mrrSum += 1.0 / (i + 1);
                    break;
                }
            }
            long found = c.expected().stream().filter(got::contains).count();
            recallSum += (double) found / c.expected().size();

            int[] dimStat = byDimension.computeIfAbsent(c.dimension(), k -> new int[2]);
            dimStat[0] += isHit ? 1 : 0;
            dimStat[1]++;
        }

        int n = cases.size();
        Map<String, Double> hitByDimension = new LinkedHashMap<>();
        byDimension.forEach((dim, stat) -> hitByDimension.put(dim, stat[0] * 1.0 / stat[1]));

        return new EvalReport(config, topK, threshold, postProcessor != null, n,
                hit * 1.0 / n, mrrSum / n, recallSum / n, hitByDimension, failures);
    }

    /** 参数网格搜索：topK × threshold 全组合，返回每组报告供对比选参 */
    public List<EvalReport> gridSearch(List<EvalCase> cases, int[] topKs, double[] thresholds,
                                       DocumentPostProcessor postProcessor) {
        List<EvalReport> reports = new ArrayList<>();
        for (int k : topKs) {
            for (double t : thresholds) {
                String config = (postProcessor != null ? "rerank" : "raw") + "(k" + k + ",t" + t + ")";
                reports.add(evaluate(config, cases, k, t, postProcessor));
            }
        }
        return reports;
    }

    /** 打印对比表 */
    public static void printTable(List<EvalReport> reports) {
        System.out.printf("%-22s %8s %8s %8s %8s%n", "config", "Hit@K", "MRR", "Recall", "failures");
        for (EvalReport r : reports) {
            System.out.printf("%-22s %8.3f %8.3f %8.3f %8d%n",
                    r.config(), r.hitAtK(), r.mrr(), r.recallAtK(), r.failures().size());
        }
    }

    /** 打印失败样本明细：优化检索的直接线索 */
    public static void printFailures(EvalReport report) {
        System.out.println("==== 未命中样本 [" + report.config() + "] ====");
        for (EvalReport.FailureDetail f : report.failures()) {
            System.out.printf("[%s] %s%n  期望: %s%n  实际: %s%n",
                    f.dimension(), f.query(), f.expected(),
                    f.retrieved().stream()
                            .map(r -> r.ruleId() + String.format("(%.3f)", r.score()))
                            .toList());
        }
    }
}
