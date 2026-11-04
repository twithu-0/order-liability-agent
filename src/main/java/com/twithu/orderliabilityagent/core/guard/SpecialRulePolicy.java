package com.twithu.orderliabilityagent.core.guard;

import com.twithu.orderliabilityagent.core.evidence.EvidenceComparator;
import com.twithu.orderliabilityagent.model.enums.Liability;

import java.util.Set;

/**
 * 特殊规则豁免策略：纯代码，守卫与打分器共用，避免两处逻辑漂移。
 * 业务规则：渠道为"去哪儿"/"大众点评"时，我司与渠道的数据不一致（firstBadHop=CHANNEL）一律我司主责。
 * 该规则以 dimension=special 存于 t_rule（S-01），不进向量库，由编排器在判责前追加进候选；
 * prompt 强制 ruleId 选非特殊规则，故特殊渠道 CHANNEL 跳案件的分支裁决必然与通用规则期望（CHANNEL_PRIMARY）冲突。
 * 豁免仅作用于"分支一致性"这一项：满足渠道+CHANNEL跳+OURS_PRIMARY 三条件时，不判分支不一致。
 * 边界收紧：SUPPLIER/NONE/EVIDENCE_GAP 跳不豁免（特殊规则只管我司与渠道不一致），
 * 非特殊渠道不豁免，判成 CHANNEL_PRIMARY 也不豁免——防止"去哪儿一律我司"的过度泛化被放行。
 */
public final class SpecialRulePolicy {

    /** 触发特殊规则的渠道集合 */
    public static final Set<String> SPECIAL_CHANNELS = Set.of("去哪儿", "大众点评");

    private SpecialRulePolicy() {
    }

    /** 是否特殊渠道（去首尾空白后匹配） */
    public static boolean isSpecialChannel(String channel) {
        return channel != null && SPECIAL_CHANNELS.contains(channel.trim());
    }

    /**
     * 特殊规则覆盖是否成立：特殊渠道 + 聚合 firstBadHop=CHANNEL + 判决 OURS_PRIMARY，三者同时满足。
     * 成立时分支一致性豁免（通用规则期望 CHANNEL_PRIMARY，但特殊规则改判我司）。
     */
    public static boolean isOverrideApplicable(String channel, String aggregateHop, Liability liability) {
        return isSpecialChannel(channel)
                && EvidenceComparator.HOP_CHANNEL.equals(aggregateHop)
                && liability == Liability.OURS_PRIMARY;
    }
}
