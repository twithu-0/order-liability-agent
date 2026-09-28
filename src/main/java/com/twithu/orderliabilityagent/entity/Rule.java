package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 判责规则实体。
 * <p>对应表 t_rule；数据来源 docs/rule.json（69 条模拟规则）。</p>
 * <p>字段与 DDL 严格一一对应。旧版这里有三个致命问题，已修：</p>
 * <ul>
 *   <li>{@code dimension} 因 {@code import java.awt.*} 被解析成 java.awt.Dimension，实体不可用；</li>
 *   <li>实体声明的 when_condition / alternatives / supplier_flow 等列在 t_rule 里不存在；</li>
 *   <li>时间列名 created_at / updated_at 与 DDL 的 created_time / updated_time 不匹配。</li>
 * </ul>
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

}
