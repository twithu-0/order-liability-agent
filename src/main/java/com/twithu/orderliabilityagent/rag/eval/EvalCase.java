package com.twithu.orderliabilityagent.rag.eval;

import java.util.List;

/**
 * 召回评测的一条标准样本（golden case）。
 *
 * @param query     模拟用户的口语化查询
 * @param dimension 问题维度，检索时作为 metadata 过滤条件，与生产配置一致
 * @param expected  期望命中的规则编号列表（人工标注的标准答案，可多条）
 * @param note      标注说明，便于复盘失败样本，可为 null
 */
public record EvalCase(String query, String dimension, List<String> expected, String note) {
}
