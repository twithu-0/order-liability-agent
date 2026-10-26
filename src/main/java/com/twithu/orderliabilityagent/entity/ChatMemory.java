package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.*;
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
@TableName("t_chat_memory")
public class ChatMemory implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对话页全局 id，与 t_session.chat_id 一致，即 Spring AI conversationId */
    private String chatId;


    /** 会话内容 */
    private String content;

    /** 会话消息的类型 */
    private String type;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 实际对话顺序 */
    private Integer sequenceId;

    /** 完结标记：1=该消息属于已完结的订单问题（USER/ASSISTANT 成对打标），0=活跃。压缩只读活跃消息 */
    private Integer completed;

}
