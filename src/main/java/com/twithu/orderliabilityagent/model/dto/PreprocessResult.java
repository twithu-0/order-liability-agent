package com.twithu.orderliabilityagent.model.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Data
public class PreprocessResult {

    /** false 表明订单号/渠道/供应商没抽全。产出 missingSlots 走追问 */
    private boolean isLiabilityRequest;

    private String normalizedInput;
    private String orderNoHint;
    private String customerHint;
    private String supplierHint;

    /**
     * 缺哪些槽位，元素用 CaseFacts 的字段名（orderNo / channel / supplier）。
     * 把缺口交给 P3 生成追问话术。
     */
    @Nullable
    private List<String> missingSlots = new ArrayList<>();

    public List<String> safeMissingSlots() {
        return missingSlots == null ? List.of() : missingSlots;
    }

    public void addMissingSlot(String slot) {
        if (missingSlots == null) {
            missingSlots = new ArrayList<>();
        }
        if (!missingSlots.contains(slot)) {
            missingSlots.add(slot);
        }
    }
}
