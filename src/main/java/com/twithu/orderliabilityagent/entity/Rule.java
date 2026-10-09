package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.twithu.orderliabilityagent.model.enums.Liability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 判责规则实体。
 * <p>对应表 t_rule；数据来源 docs/rule.json（69 条，v2 格式：枚举责任 + firstBadHop 分支）。</p>
 * <p>字段与 DDL 一一对应；branches_json 为 v2 新增列，见 db/migration_v2.sql。</p>
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
    private String dimension;

    /** 问题类型，如 BED_TYPE_MISMATCH */
    private String issueType;

    /** 优先级：合同1000 / 专属500 / 问题100 / 数据流80 / 默认10 */
    private Integer priority;

    /** 所需证据，JSON 数组字符串 */
    private String evidenceRequired;

    /** 默认主责 */
    private Liability defaultLiability;

    /** 默认次责 */
    private Liability secondaryLiability;

    /** firstBadHop 分支裁决，JSON 数组字符串，如 [{"when":{"firstBadHop":"CHANNEL"},"liability":"CHANNEL_PRIMARY"}] */
    private String branchesJson;

    /** 规则全文（Markdown），用于 RAG 切分与向量化 */
    private String contentMd;

    /** 备注 */
    private String remark;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间，由 DB 默认值维护 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdTime;

    private String createBy;

    /** 更新时间，由 DB ON UPDATE 维护 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedTime;

    private String updateBy;
}
