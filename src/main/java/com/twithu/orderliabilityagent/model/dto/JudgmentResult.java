package com.twithu.orderliabilityagent.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.twithu.orderliabilityagent.model.enums.Liability;
import lombok.Data;

import java.util.List;

/**
 * P7 判责 LLM 的结构化输出。
 * - ruleId 必须来自候选规则集，P8 守卫校验成员身份防幻觉引用
 * - liability/secondaryLiability 用 Liability 枚举，JSON Schema 会生成 enum 约束，
 *   反序列化走 Liability.of()，兼容枚举名与中文描述
 * - confidence 低于阈值或与规则分支裁决不一致时，P8 标记人工复核
 * - result 是面向用户的最终结论（结论+引用规则+友情提示），落 t_session.result；LLM 漏输出时编排器兜底拼装
 */
@Data
public class JudgmentResult {

    @JsonProperty("ruleId")
    private String ruleId;

    @JsonProperty("liability")
    private Liability liability;

    @JsonProperty("secondaryLiability")
    private Liability secondaryLiability;

    @JsonProperty("reason")
    private String reason;

    /** 面向用户的最终判责结论：责任结论+引用规则编号/名称+友情提示，落 t_session.result 供前端展示 */
    @JsonProperty("result")
    private String result;

    @JsonProperty("evidenceCited")
    private List<String> evidenceCited;

    @JsonProperty("confidence")
    private Double confidence;
}
