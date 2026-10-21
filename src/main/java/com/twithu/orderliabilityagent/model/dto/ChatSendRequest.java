package com.twithu.orderliabilityagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送聊天消息请求。message 即用户投诉原文，内部转交判责编排器执行 P1-P8。
 */
@Data
public class ChatSendRequest {

    /** 对话编号 */
    @NotBlank(message = "chatId 不能为空")
    private String chatId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息最长 2000 字")
    private String message;
}
