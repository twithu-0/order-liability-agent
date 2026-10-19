package com.twithu.orderliabilityagent.core;

import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P2 输出归一化单测：争议字段上限/去重/去空白，承诺值/实际值三列表按位对齐。
 * 纯单元测试，不启动 Spring 上下文。
 */
class LiabilityOrchestratorAlignTest {

    private ExtractResult result(List<String> fields, List<String> promised, List<String> actual) {
        ExtractResult r = new ExtractResult();
        r.setDisputedFieldList(fields);
        r.setPromisedValueList(promised);
        r.setActualValueList(actual);
        return r;
    }

    @Test
    void capsAtTwoAndAligns() {
        ExtractResult r = result(
                List.of("bedType", "breakfast", "wifi"),
                List.of("大床"),
                List.of("双床", "无早餐", "连不上网"));
        LiabilityOrchestrator.alignDisputedFields(r);
        assertEquals(List.of("bedType", "breakfast"), r.getDisputedFieldList());
        assertEquals(Arrays.asList("大床", null), r.getPromisedValueList());
        assertEquals(List.of("双床", "无早餐"), r.getActualValueList());
    }

    @Test
    void dedupesAndFiltersBlank() {
        ExtractResult r = result(
                Arrays.asList("bedType", "bedType", " ", "breakfast"),
                null,
                List.of("双床"));
        LiabilityOrchestrator.alignDisputedFields(r);
        assertEquals(List.of("bedType", "breakfast"), r.getDisputedFieldList());
        assertEquals(Arrays.asList(null, null), r.getPromisedValueList());
        assertEquals(Arrays.asList("双床", null), r.getActualValueList());
    }

    @Test
    void emptyFieldsUntouched() {
        ExtractResult r = result(null, null, null);
        LiabilityOrchestrator.alignDisputedFields(r);
        assertNull(r.getDisputedFieldList());
    }
}
