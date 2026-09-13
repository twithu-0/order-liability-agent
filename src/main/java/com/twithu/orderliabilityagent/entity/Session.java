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
 * 判责会话实体。
 * <p>对应表 t_liability_session；一次客服对话 = 一次判责请求。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_session")
public class Session implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话编号，如 SES-20261116-000001 */
    private String sessionNo;

    /** 订单编号 */
    private String orderNo;

    private Long userId;

    /** 渠道 */
    private String channel;

    /** 供应商 */
    private String supplier;

    /** 会话内容 */
    private String content;

    /** 会话状态 */
    private String status;

    /** 使用的模型 */
    private String modelName;

    /** 消耗 token 总数 */
    private long totalTokens;

    /** 总耗时（毫秒） */
    private Integer timeCost;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    private String updateBy;
}
