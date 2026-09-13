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
}
