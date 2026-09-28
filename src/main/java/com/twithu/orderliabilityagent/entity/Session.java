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

    /** 对话编号 */
    private String sessionNo;

    /** 订单编号 */
    private String orderNo;

    private Long userId;

    /** 渠道 */
    private String channel;

    /** 供应商 */
    private String supplier;

    /** 问题维度，P2 事实抽取回填；评测时按维度分组统计一致率 */
    private String dimension;

    /** 会话内容 */
    private String content;

    /** 会话状态 */
    private String status;

    /** 使用的模型 */
    private String modelName;

    /** 消耗 token 总数；未统计时为 null，不用基本类型避免写出 0 */
    private Long totalTokens;

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
