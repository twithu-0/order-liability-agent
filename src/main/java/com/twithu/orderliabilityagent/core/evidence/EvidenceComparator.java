package com.twithu.orderliabilityagent.core.evidence;

import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * P5 比对证据：对每个争议字段（最多 2 个）沿数据链路逐跳 diff，定位第一个出错环节 firstBadHop；
 * 多字段时 aggregateHop 取最靠前的出错环节作为整体结论。
 * 枚举型字段（床型/早餐等）直接归一化比对；自由文本字段（酒店名别名/地址）
 * 的语义等价判断留给 P7 判责 LLM 看着 diff 表裁决，本类只做确定性比对。
 */
public final class EvidenceComparator {

    public static final String HOP_OURS = "OURS";
    public static final String HOP_CHANNEL = "CHANNEL";
    public static final String HOP_SUPPLIER = "SUPPLIER";
    public static final String HOP_NONE = "NONE";
    public static final String HOP_EVIDENCE_GAP = "EVIDENCE_GAP";

    private EvidenceComparator() {
    }

    public static FieldDiff diff(String field, OrderEvidence e) {
        String src = e.supplierSource() == null ? null : e.supplierSource().get(field);
        String our = e.ourTransfer() == null ? null : e.ourTransfer().get(field);
        String ch = e.channelDisplay() == null ? null : e.channelDisplay().get(field);
        String act = e.customerActual() == null ? null : e.customerActual().get(field);

        String hop;
        if (src == null || our == null || ch == null || act == null) {
            hop = HOP_EVIDENCE_GAP;
        } else if (!eq(our, src)) {
            hop = HOP_OURS;
        } else if (!eq(ch, our)) {
            hop = HOP_CHANNEL;
        } else if (!eq(act, ch)) {
            hop = HOP_SUPPLIER;
        } else {
            hop = HOP_NONE;
        }
        return new FieldDiff(field, src, our, ch, act, hop);
    }

    /**
     * 多字段聚合 firstBadHop：取链路最靠前的出错环节。
     * 优先级 OURS < CHANNEL < SUPPLIER < EVIDENCE_GAP < NONE：
     * 确定性错误优先于证据缺失，证据缺失优先于"没问题"；全字段 NONE 则投诉整体不成立。
     * 空列表按 EVIDENCE_GAP 处理。
     */
    public static String aggregateHop(List<FieldDiff> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return HOP_EVIDENCE_GAP;
        }
        String best = HOP_NONE;
        for (FieldDiff d : diffs) {
            if (hopRank(d.firstBadHop()) < hopRank(best)) {
                best = d.firstBadHop();
            }
        }
        return best;
    }

    private static int hopRank(String hop) {
        if (hop == null) {
            return 4;
        }
        return switch (hop) {
            case HOP_OURS -> 0;
            case HOP_CHANNEL -> 1;
            case HOP_SUPPLIER -> 2;
            case HOP_EVIDENCE_GAP -> 3;
            default -> 4;
        };
    }

    /** 归一化等价：去首尾空白、压缩内部空白、忽略大小写 */
    static boolean eq(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static String normalize(String s) {
        return s == null ? null : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
