package com.twithu.orderliabilityagent.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 将用户问题转换为实体类，方便后续调用LLM判责
 */
@Data
public class ExtractResult {

    @JsonProperty("originalUserPrompt")
    private String userPrompt;
    @JsonProperty("dimension")
    private String dimension;
    @JsonProperty("orderNO")
    @NotBlank
    private String orderNo;
    @JsonProperty("channelName")
    private String customer;
    @JsonProperty("supplierName")
    private String supplier;
    @JsonProperty("hotelName")
    private String hotelName;
    @JsonProperty("hotelAddress")
    private String hotelAddress;
    @JsonProperty("phone")
    private String phone;

    /** 争议字段，取值为提示词中约定的 camelCase 字段键，如 bedType / hotelName / breakfast */
    @JsonProperty("disputedField")
    private String disputedField;

    /** 客人声称的承诺值（页面/订单 promised），可为 null，后续从渠道展示快照补齐 */
    @JsonProperty("promisedValue")
    private String promisedValue;

    /** 客人遇到的实际情况，判责比对的必需输入 */
    @JsonProperty("actualValue")
    private String actualValue;

    /** 投诉一句话摘要，供判责 LLM 与追问话术使用 */
    @JsonProperty("complaint")
    private String complaint;
}
