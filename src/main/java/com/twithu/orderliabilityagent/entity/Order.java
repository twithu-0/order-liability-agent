package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单四跳证据实体，对应 t_order。
 * 一行一订单，四个快照列存 JSON（{字段键: 值}），与 OrderEvidence 的四个 Map 直接对应。
 * 字段键沿用 ExtractResult.disputedFieldList 的 camelCase 词表（bedType/breakfast/hotelName...）。
 * channel_display 为 null 表示该跳证据缺失（EVIDENCE_GAP 场景）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号，MT开头+8位数字，唯一 */
    private String orderNo;

    /** 渠道，如 携程/去哪儿/飞猪 */
    private String channel;

    /** 供应商，如 美团/艺龙/Agoda */
    private String supplier;

    /** 供应商源数据/确认单快照 JSON */
    private String supplierSource;

    /** 我司映射后下发给渠道的数据快照 JSON */
    private String ourTransfer;

    /** 渠道页面实际展示快照 JSON；null=整跳证据缺失 */
    private String channelDisplay;

    /** 客人实际体验基线快照 JSON；判责时以投诉抽取值覆盖 */
    private String customerActual;

    /** 创建时间，数据库默认值填充 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
