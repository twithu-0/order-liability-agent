package com.twithu.orderliabilityagent.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

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

    /** 争议字段列表，最多 2 个且同维度，取值为提示词中约定的 camelCase 字段键，如 bedType / hotelName / breakfast */
    @JsonProperty("disputedFieldList")
    private List<String> disputedFieldList;

    /** 承诺值列表，与 disputedFieldList 按位对齐（编排器归一化保证），元素可为 null，后续从渠道展示快照补齐 */
    @JsonProperty("promisedValue")
    private List<String> promisedValueList;

    /** 实际情况列表，与 disputedFieldList 按位对齐，判责比对的必需输入 */
    @JsonProperty("actualValue")
    private List<String> actualValueList;

    /** 投诉一句话摘要，供判责 LLM 与追问话术使用 */
    @JsonProperty("complaint")
    private String complaint;
}
