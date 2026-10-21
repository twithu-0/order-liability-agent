package com.twithu.orderliabilityagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天历史消息返回体。role=USER 渲染用户气泡，role=ASSISTANT 渲染 Agent 气泡。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    /** 消息角色：USER / ASSISTANT */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息时间 */
    private LocalDateTime createTime;
}
