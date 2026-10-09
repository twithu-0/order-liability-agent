package com.twithu.orderliabilityagent.core.evidence;

import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P5 比对器单测：用 mock/orders.json 真实 fixture 覆盖五种 firstBadHop。
 * 纯单元测试，不启动 Spring 上下文。
 */
class EvidenceComparatorTest {

    private static MockOrderEvidenceRepository repository;

    @BeforeAll
    static void setUp() throws IOException {
        repository = new MockOrderEvidenceRepository(JsonMapper.builder().build());
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

    /** 模拟编排器行为：用本次投诉抽取的 actualValue 覆盖快照中的客人实际值 */
    private OrderEvidence withActual(String orderNo, String field, String actualValue) {
        OrderEvidence base = repository.find(orderNo);
        java.util.Map<String, String> actual = new java.util.HashMap<>(
                base.customerActual() == null ? java.util.Map.of() : base.customerActual());
        actual.put(field, actualValue);
        return new OrderEvidence(base.orderNo(), base.channel(), base.supplier(),
                base.supplierSource(), base.ourTransfer(), base.channelDisplay(), actual);
    }
}
