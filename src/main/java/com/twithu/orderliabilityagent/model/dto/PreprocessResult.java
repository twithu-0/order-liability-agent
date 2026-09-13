package com.twithu.orderliabilityagent.model.dto;

import lombok.Data;

@Data
public class PreprocessResult {
    private boolean isLiabilityRequest; //false 表明用户输入的订单号或渠道或供应商有误
    private String normalizedInput;
    private String orderNoHint;
    private String customerHint;
    private String supplierHint;
}
