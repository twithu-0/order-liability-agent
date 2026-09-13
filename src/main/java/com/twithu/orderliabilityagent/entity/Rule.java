package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.twithu.orderliabilityagent.model.enums.Liability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 判责规则实体。
 * <p>对应表 t_rule；数据来源 rules/rules.json（69 条模拟规则）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_rule")
public class Rule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编号，如 H-01 / R-02 / D-01 */
    private String ruleId;

    /** 规则名称/场景摘要 */
    private String name;

    /** 问题维度 */
    private Dimension dimension;

    /** 问题类型，如 BED_TYPE_MISMATCH */
    private String issueType;

    /** 优先级，越大越高 */
    private Integer priority;

    /** 规则版本 */
    private Integer version;

    /** 生效日期 */
    private LocalDate effectiveFrom;

    /** 失效日期，null 表示长期有效 */
    private LocalDate effectiveTo;

    /** 适用供应商链路：push / pull（逗号分隔） */
    private String supplierFlow;

    /** 适用渠道链路：push / pull（逗号分隔） */
    private String channelFlow;

    /** 触发条件（JSON 数组，自然语言） */
    private String whenCondition;

    /** 所需证据（JSON 数组） */
    private String evidenceRequired;

    /** 默认主责 */
    private Liability defaultLiability;

    /** 默认次责 */
    private Liability secondaryLiability;

    /** 例外情况（JSON 数组：when + primary） */
    private String alternatives;

    /** 调整标签（逗号分隔，对应 adjustments.yaml） */
    private String adjustmentTags;

    /** 规则全文（JSON），用于 RAG 切分 */
    private String contentMd;

    /** 备注 */
    private String notes;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

}
