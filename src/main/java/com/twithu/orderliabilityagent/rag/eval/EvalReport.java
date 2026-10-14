package com.twithu.orderliabilityagent.rag.eval;

import java.util.List;
import java.util.Map;

/**
 * 一组配置下的召回评测报告。
 * <p>指标定义（K = topK，重排模式下以重排截断后的实际条数为准）：</p>
 * <ul>
 *   <li>hitAtK：期望规则出现在前 K 条里的样本占比，判责最终只选 1 条规则，这是核心指标</li>
 *   <li>mrr：每条样本取第一个正确结果的排名倒数（第1名=1，第3名=1/3）再平均，衡量正确规则是否靠前</li>
 *   <li>recallAtK：期望规则集合被召回的比例再平均，多期望样本时有意义</li>
 * </ul>
 *
 * @param config         配置标签，如 "raw(k5,t0.3)" / "rerank(k10,t0.5)"
 * @param hitByDimension 分维度命中率，定位哪个维度的规则文本需要优化
 * @param failures       未命中样本明细（含实际召回与分数），是优化检索的直接线索
 */
public record EvalReport(
        String config,
        int topK,
        double threshold,
        boolean reranked,
        int total,
        double hitAtK,
        double mrr,
        double recallAtK,
        Map<String, Double> hitByDimension,
        List<FailureDetail> failures) {

    /** 实际召回的规则与相似度分数 */
    public record RetrievedRule(String ruleId, double score) {
    }

    /** 未命中样本：期望 vs 实际，附分数便于判断是"没召回"还是"被阈值卡掉" */
    public record FailureDetail(String query, String dimension, List<String> expected,
                                List<RetrievedRule> retrieved) {
    }
}
