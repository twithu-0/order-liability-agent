package com.twithu.orderliabilityagent.model.dto;

import java.util.Map;

/**
 * 订单四跳证据快照（P4 获取证据的产物）。
 * <p>数据链路：供应商源数据 → 我司映射/传输 → 渠道页面展示 → 客人实际体验。
 * 四个 Map 的 key 统一为 {@code ExtractResult.disputedField} 约定的 camelCase 字段键，
 * value 统一为字符串，便于 {@code EvidenceComparator} 做通用逐跳比对。</p>
 *
 * @param supplierSource 供应商源数据/确认单
 * @param ourTransfer    我司映射后下发给渠道的数据
 * @param channelDisplay 渠道页面实际展示的数据
 * @param customerActual 客人实际体验值，来自 P2 抽取的 actualValue
 */
public record OrderEvidence(
        String orderNo,
        String channel,
        String supplier,
        Map<String, String> supplierSource,
        Map<String, String> ourTransfer,
        Map<String, String> channelDisplay,
        Map<String, String> customerActual) {
}
