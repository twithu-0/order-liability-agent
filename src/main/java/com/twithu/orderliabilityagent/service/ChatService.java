package com.twithu.orderliabilityagent.service;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.model.dto.ChatSendRequest;
import com.twithu.orderliabilityagent.model.vo.ChatMessageVO;
import com.twithu.orderliabilityagent.model.vo.ChatVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 前端聊天服务。
 * 对话容器复用 t_session：一个 chatId 下多轮判责对应多条 session 记录，
 * content 是用户消息、result 是 Agent 回复；t_chat_memory 只做 LLM 内部记忆，不直接展示。
 * 所有方法都要求登录态，且只能操作当前用户自己的对话。
 */
public interface ChatService {

    /**
     * 创建新对话：生成全局唯一 chatId 返回前端。
     * 此时不落库，首条消息发送时才产生 t_session 记录（避免空对话占库）。
     */
    ChatVO createChat();

    /**
     * 当前用户的对话列表，按最近活跃时间倒序，供侧边栏渲染。
     */
    List<ChatVO> listChats();

    /**
     * 对话历史消息：按时间正序返回 USER/ASSISTANT 交替的展示级消息。
     */
    List<ChatMessageVO> history(String chatId);

    /**
     * 发送消息：校验归属后转交判责编排器执行 P1-P8，返回面向用户的回复。
     */
    BaseResponse<String> send(ChatSendRequest request);

    /**
     * 发送消息（流式）：P1-P8 同步执行保证事实可靠，守卫通过后由 P9 表达层流式输出。
     * SSE 事件约定：
     * - delta：增量文本，前端逐段追加渲染
     * - done：最终全文（权威值），前端用它整体替换气泡内容，兜底降级/守卫替换都靠它收敛
     * 非判责路径（追问/失败）与表达层异常均降级为原文输出，接口不会因本地模型不可用而失败。
     */
    Flux<ServerSentEvent<String>> streamSend(ChatSendRequest request);

    /**
     * 删除对话：清理该 chatId 的 session 记录与 LLM 记忆；运行轨迹 t_run_trace 保留供审计回放。
     */
    boolean deleteChat(String chatId);
}
