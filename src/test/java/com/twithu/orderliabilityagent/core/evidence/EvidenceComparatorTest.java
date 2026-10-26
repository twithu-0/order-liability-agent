package com.twithu.orderliabilityagent.core.evidence;

import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P5 比对器单测：用 src/test/resources/mock/orders.json fixture 覆盖五种 firstBadHop。
 * fixture 与 t_order 初始数据同源（原 mock 订单已迁入 DB），此处直接加载 JSON 不依赖数据库。
 * 纯单元测试，不启动 Spring 上下文。
 */
class EvidenceComparatorTest {

    private static Map<String, OrderEvidence> orders;

    @BeforeAll
    static void setUp() throws IOException {
        try (InputStream in = new ClassPathResource("mock/orders.json").getInputStream()) {
            orders = JsonMapper.builder().build()
                    .readValue(in, new TypeReference<Map<String, OrderEvidence>>() {});
        }
    }

    @Test
    void channelHop() {
        // MT12345222：源/我司=大床，渠道展示=双床 → CHANNEL
        OrderEvidence e = withActual("MT12345222", "bedType", "双床");
        FieldDiff diff = EvidenceComparator.diff("bedType", e);
        assertEquals(EvidenceComparator.HOP_CHANNEL, diff.firstBadHop());
        assertEquals("大床", diff.supplierValue());
        assertEquals("双床", diff.channelValue());
    }

    @Test
    void oursHop() {
        // MT20000001：供应商=京州大酒店，我司映射=京州大洒店 → OURS
        OrderEvidence e = withActual("MT20000001", "hotelName", "京州大酒店");
        assertEquals(EvidenceComparator.HOP_OURS,
                EvidenceComparator.diff("hotelName", e).firstBadHop());
    }

    @Test
    void supplierHop() {
        // MT20000002：链路全=含双早，实际=无早餐 → SUPPLIER（酒店执行侧）
        OrderEvidence e = withActual("MT20000002", "breakfast", "无早餐");
        assertEquals(EvidenceComparator.HOP_SUPPLIER,
                EvidenceComparator.diff("breakfast", e).firstBadHop());
    }

    @Test
    void noneHop() {
        // MT20000003：四跳全一致 → NONE
        OrderEvidence e = withActual("MT20000003", "bedType", "大床");
        assertEquals(EvidenceComparator.HOP_NONE,
                EvidenceComparator.diff("bedType", e).firstBadHop());
    }

    @Test
    void evidenceGapHop() {
        // MT20000004：channelDisplay 整跳缺失 → EVIDENCE_GAP
        OrderEvidence e = withActual("MT20000004", "refundPolicy", "可退");
        assertEquals(EvidenceComparator.HOP_EVIDENCE_GAP,
                EvidenceComparator.diff("refundPolicy", e).firstBadHop());
    }

    @Test
    void unknownFieldIsGap() {
        OrderEvidence e = withActual("MT12345222", "pool", "关闭");
        assertEquals(EvidenceComparator.HOP_EVIDENCE_GAP,
                EvidenceComparator.diff("pool", e).firstBadHop());
    }

    @Test
    void normalizationIgnoresCaseAndWhitespace() {
        OrderEvidence e = withActual("MT20000003", "bedType", " 大床 ");
        assertEquals(EvidenceComparator.HOP_NONE,
                EvidenceComparator.diff("bedType", e).firstBadHop());
    }

    @Test
    void aggregateHopPrefersEarliestError() {
        FieldDiff channel = new FieldDiff("bedType", "大床", "大床", "双床", "双床", EvidenceComparator.HOP_CHANNEL);
        FieldDiff ours = new FieldDiff("breakfast", "含早", "无早", "无早", "无早餐", EvidenceComparator.HOP_OURS);
        assertEquals(EvidenceComparator.HOP_OURS, EvidenceComparator.aggregateHop(List.of(channel, ours)));
        assertEquals(EvidenceComparator.HOP_CHANNEL, EvidenceComparator.aggregateHop(List.of(channel)));
    }

    @Test
    void aggregateHopGapBeatsNoneAndEmptyIsGap() {
        FieldDiff none = new FieldDiff("bedType", "大床", "大床", "大床", "大床", EvidenceComparator.HOP_NONE);
        FieldDiff gap = new FieldDiff("breakfast", null, "含早", "含早", "无早餐", EvidenceComparator.HOP_EVIDENCE_GAP);
        assertEquals(EvidenceComparator.HOP_EVIDENCE_GAP, EvidenceComparator.aggregateHop(List.of(none, gap)));
        assertEquals(EvidenceComparator.HOP_NONE, EvidenceComparator.aggregateHop(List.of(none)));
        assertEquals(EvidenceComparator.HOP_EVIDENCE_GAP, EvidenceComparator.aggregateHop(List.of()));
    }

    /** 模拟编排器行为：用本次投诉抽取的 actualValue 覆盖快照中的客人实际值 */
    private OrderEvidence withActual(String orderNo, String field, String actualValue) {
        OrderEvidence base = orders.get(orderNo);
        java.util.Map<String, String> actual = new java.util.HashMap<>(
                base.customerActual() == null ? java.util.Map.of() : base.customerActual());
        actual.put(field, actualValue);
        return new OrderEvidence(base.orderNo(), base.channel(), base.supplier(),
                base.supplierSource(), base.ourTransfer(), base.channelDisplay(), actual);
    }
}
