package com.twithu.orderliabilityagent.model.dto;

/**
 * 单字段逐跳比对结果（P5 比对证据的产物）。
 * firstBadHop 取值：
 *   OURS：我司映射/传输偏离供应商源数据 → 我司主责
 *   CHANNEL：渠道展示偏离我司下发数据 → 渠道主责
 *   SUPPLIER：链路一致但与实际不符 → 供应商（酒店执行侧）主责
 *   NONE：四跳全一致，投诉不成立或需人工复核
 *   EVIDENCE_GAP：任一跳值缺失，按 D-08 转人工
 */
public record FieldDiff(
        String field,
        String supplierValue,
        String ourValue,
        String channelValue,
        String actualValue,
        String firstBadHop) {
}
