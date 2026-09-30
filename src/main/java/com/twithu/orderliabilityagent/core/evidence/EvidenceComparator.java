package com.twithu.orderliabilityagent.core.evidence;

import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;

import java.util.Locale;
import java.util.Objects;

/**
 * P5 比对证据：对争议字段沿数据链路逐跳 diff，定位第一个出错环节 firstBadHop。
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

    /** 归一化等价：去首尾空白、压缩内部空白、忽略大小写 */
    static boolean eq(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    private static String normalize(String s) {
        return s == null ? null : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
