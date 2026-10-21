package com.twithu.orderliabilityagent.controller;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import com.twithu.orderliabilityagent.model.dto.ChatSendRequest;
import com.twithu.orderliabilityagent.model.vo.ChatMessageVO;
import com.twithu.orderliabilityagent.model.vo.ChatVO;
import com.twithu.orderliabilityagent.service.ChatService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 前端聊天接口。
 * 登录态走 HttpSession（Cookie 自动携带），跨域调用需 withCredentials。
 * 典型前端流程：
 * - 进入页面 GET /chat/list 渲染侧边栏；点"新对话" POST /chat/create 拿 chatId
 * - 点开对话 GET /chat/history?chatId= 渲染历史气泡
 * - 发消息 POST /chat/send，data 即 Agent 回复文本，直接追加气泡
 * - 删除对话 POST /chat/delete?chatId=
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    /**
     * 创建新对话：POST /api/chat/create，返回 chatId（首条消息发送时才落库）
     */
    @PostMapping("/create")
    public BaseResponse<ChatVO> createChat() {
        return ResultUtil.success(chatService.createChat());
    }

    /**
     * 对话列表：GET /api/chat/list，按最近活跃倒序
     */
    @GetMapping("/list")
    public BaseResponse<List<ChatVO>> listChats() {
        return ResultUtil.success(chatService.listChats());
    }

    /**
     * 对话历史：GET /api/chat/history?chatId=，USER/ASSISTANT 交替、时间正序
     */
    @GetMapping("/history")
    public BaseResponse<List<ChatMessageVO>> history(@RequestParam String chatId) {
        return ResultUtil.success(chatService.history(chatId));
    }

    /**
     * 发送消息：POST /api/chat/send，内部执行 P1-P8 判责流程，data 为面向用户的回复
     */
    @PostMapping("/send")
    public BaseResponse<String> send(@RequestBody @Valid ChatSendRequest request) {
        return chatService.send(request);
    }

    /**
     * 发送消息（流式）：POST /api/chat/send/stream，响应为 SSE 长连接。
     * 事件：delta 增量文本 / done 最终全文（前端以 done 为准整体替换气泡）。
     * POST + SSE 需用 fetch + ReadableStream 读取（EventSource 只支持 GET）。
     */
    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendStream(@RequestBody @Valid ChatSendRequest request) {
        return chatService.streamSend(request);
    }

    /**
     * 删除对话：POST /api/chat/delete?chatId=，清理会话记录与 LLM 记忆
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChat(@RequestParam String chatId) {
        return ResultUtil.success(chatService.deleteChat(chatId));
    }
}
