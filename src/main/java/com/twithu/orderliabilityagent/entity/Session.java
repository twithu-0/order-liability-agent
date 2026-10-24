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

    /**
     * 对话页全局 id。同一对话的多轮判责共享一个 chatId，单轮由自增主键 id 区分；
     * 与 t_chat_memory.chat_id（Spring AI conversationId）一致，据此串联展示历史与 LLM 记忆。
     */
    private String chatId;

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

    /** 消耗 token 总数；会话完成时由编排器汇总 need_token_record=1 的 LLM 步骤回写，null 表示未统计（历史数据/失败会话） */
    private Long totalTokens;

    /** 总耗时（毫秒） */
    private Integer timeCost;

    /** 最终判责结论（面向用户），P8 守卫通过后回写 */
    private String result;

    /** 判责原因（LLM reason，内部依据），P8 守卫通过后回写 */
    private String reason;

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
