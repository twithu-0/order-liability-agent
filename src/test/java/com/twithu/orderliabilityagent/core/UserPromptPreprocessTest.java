package com.twithu.orderliabilityagent.core;

import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 闸门单测：只硬校验订单号，渠道/供应商缺失不再拒答。
 */
class UserPromptPreprocessTest {

    @Test
    void orderNoOnlyPassesGate() {
        PreprocessResult r = UserPromptPreprocess.extract("MT12345222 页面写的大床房，到店给的双床");
        assertTrue(r.isLiabilityRequest());
        assertEquals("MT12345222", r.getOrderNoHint());
        assertTrue(r.safeMissingSlots().contains("channel"));
        assertTrue(r.safeMissingSlots().contains("supplier"));
        assertFalse(r.safeMissingSlots().contains("orderNo"));
    }

    @Test
    void missingOrderNoFailsGate() {
        PreprocessResult r = UserPromptPreprocess.extract("渠道去哪儿 供应商美团，床型不对");
        assertFalse(r.isLiabilityRequest());
        assertTrue(r.safeMissingSlots().contains("orderNo"));
    }

    @Test
    void fullInputExtractsHints() {
        PreprocessResult r = UserPromptPreprocess.extract(
                "MT12345222 渠道去哪儿 供应商美团，床型不一致");
        assertTrue(r.isLiabilityRequest());
        assertEquals("去哪儿", r.getCustomerHint());
        assertEquals("美团", r.getSupplierHint());
        assertTrue(r.safeMissingSlots().isEmpty());
    }

    @Test
    void orderNoNotExtractedFromLongerString() {
        PreprocessResult r = UserPromptPreprocess.extract("XMT123452229 床型不对");
        assertFalse(r.isLiabilityRequest());
    }
}
