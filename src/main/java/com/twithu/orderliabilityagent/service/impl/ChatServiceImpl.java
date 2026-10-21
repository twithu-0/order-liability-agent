package com.twithu.orderliabilityagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.common.exception.ThrowUtil;
import com.twithu.orderliabilityagent.core.LiabilityAgent;
import com.twithu.orderliabilityagent.core.LiabilityOrchestrator;
import com.twithu.orderliabilityagent.entity.ChatMemory;
import com.twithu.orderliabilityagent.entity.RunTrace;
import com.twithu.orderliabilityagent.entity.Session;
import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.mapper.ChatMemoryMapper;
import com.twithu.orderliabilityagent.mapper.RunTraceMapper;
import com.twithu.orderliabilityagent.mapper.SessionMapper;
import com.twithu.orderliabilityagent.model.dto.ChatSendRequest;
import com.twithu.orderliabilityagent.model.vo.ChatMessageVO;
import com.twithu.orderliabilityagent.model.vo.ChatVO;
import com.twithu.orderliabilityagent.service.ChatService;
import com.twithu.orderliabilityagent.service.SessionService;
import com.twithu.orderliabilityagent.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端聊天服务实现。
 * 设计要点：
 * - 不新建对话表：t_session 一行 = 一轮问答（content 用户消息 / result Agent 回复），
 *   chat_id 相同的多行构成一个对话，列表按 chat_id 分组得到
 * - chatId 同时是 Spring AI conversationId，t_chat_memory.chat_id 与之相等，
 *   展示历史（t_session）与 LLM 记忆（t_chat_memory）各管各的，互不污染
 * - 归属校验统一走 userId + chatId 计数，防止越权读/删他人对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 新对话默认标题，首条消息发送后由列表接口按内容自动截取 */
    private static final String DEFAULT_TITLE = "新对话";

    /** 自动标题最大长度 */
    private static final int TITLE_MAX_LEN = 20;

    /** 非 LLM 路径固定话术的切片大小（字符数），模拟打字机效果 */
    private static final int CHUNK_SIZE = 12;

    /** P9 表达层整体超时：本地模型冷加载/排队时降级回原结论，不让前端无限等待 */
    private static final Duration PRESENT_TIMEOUT = Duration.ofSeconds(60);

    /** 规则编号格式，如 H-01 / D-08，用于流式表达守卫 */
    private static final Pattern RULE_ID_PATTERN = Pattern.compile("[A-Z]-\\d{2}");

    private final SessionMapper sessionMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final RunTraceMapper runTraceMapper;
    private final UserService userService;
    private final SessionService sessionService;
    private final LiabilityOrchestrator orchestrator;
    private final LiabilityAgent agent;

    @Override
    public ChatVO createChat() {
        User user = userService.getLoginUser();
        String chatId = "chat_" + user.getId() + "_" + RandomUtil.randomString(6);
        return ChatVO.builder()
                .chatId(chatId)
                .title(DEFAULT_TITLE)
                .build();
    }

    @Override
    public List<ChatVO> listChats() {
        User user = userService.getLoginUser();
        List<Session> sessions = sessionMapper.selectList(new LambdaQueryWrapper<Session>()
                .eq(Session::getUserId, user.getId())
                .isNotNull(Session::getChatId)
                .orderByAsc(Session::getId));

        // 按 chatId 分组（保持首次出现顺序），每组首条 content 生成标题、末条时间作为最近活跃时间
        Map<String, List<Session>> grouped = new LinkedHashMap<>();
        for (Session session : sessions) {
            grouped.computeIfAbsent(session.getChatId(), k -> new ArrayList<>()).add(session);
        }
        List<ChatVO> chats = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<Session>> entry : grouped.entrySet()) {
            List<Session> rounds = entry.getValue();
            Session first = rounds.get(0);
            Session last = rounds.get(rounds.size() - 1);
            chats.add(ChatVO.builder()
                    .chatId(entry.getKey())
                    .title(deriveTitle(first.getContent()))
                    .createTime(first.getCreateTime())
                    .updateTime(last.getUpdateTime() != null ? last.getUpdateTime() : last.getCreateTime())
                    .build());
        }
        // 最近活跃的对话排最前
        chats.sort((a, b) -> {
            LocalDateTime ta = a.getUpdateTime() != null ? a.getUpdateTime() : a.getCreateTime();
            LocalDateTime tb = b.getUpdateTime() != null ? b.getUpdateTime() : b.getCreateTime();
            if (ta == null || tb == null) {
                return 0;
            }
            return tb.compareTo(ta);
        });
        return chats;
    }

    @Override
    public List<ChatMessageVO> history(String chatId) {
        User user = userService.getLoginUser();
        assertOwnership(chatId, user.getId());
        List<Session> rounds = sessionMapper.selectList(new LambdaQueryWrapper<Session>()
                .eq(Session::getChatId, chatId)
                .eq(Session::getUserId, user.getId())
                .orderByAsc(Session::getId));
        List<ChatMessageVO> messages = new ArrayList<>(rounds.size() * 2);
        for (Session round : rounds) {
            messages.add(ChatMessageVO.builder()
                    .role("USER")
                    .content(round.getContent())
                    .createTime(round.getCreateTime())
                    .build());
            // 失败/历史数据可能没有 result，只渲染用户消息，避免空气泡
            if (StringUtils.hasText(round.getResult())) {
                messages.add(ChatMessageVO.builder()
                        .role("ASSISTANT")
                        .content(round.getResult())
                        .createTime(round.getUpdateTime() != null ? round.getUpdateTime() : round.getCreateTime())
                        .build());
            }
        }
        return messages;
    }

    @Override
    public BaseResponse<String> send(ChatSendRequest request) {
        User user = userService.getLoginUser();
        String chatId = request.getChatId();
        // 已有记录的 chatId 必须归属当前用户；全新 chatId 放行（首条消息才落库）
        long total = sessionMapper.selectCount(new LambdaQueryWrapper<Session>()
                .eq(Session::getChatId, chatId));
        if (total > 0) { //如果chatId之前存在session，session的用户归属必须有当前登录用户
            assertOwnership(chatId, user.getId());
        }
        return orchestrator.execute(request.getMessage(), chatId);
    }

    @Override
    public Flux<ServerSentEvent<String>> streamSend(ChatSendRequest request) {
        User user = userService.getLoginUser();
        String chatId = request.getChatId();
        long total = sessionMapper.selectCount(new LambdaQueryWrapper<Session>()
                .eq(Session::getChatId, chatId));
        if (total > 0) {
            assertOwnership(chatId, user.getId());
        }
        // P1-P8 在请求线程同步执行：getLoginUser 依赖 RequestContextHolder，不能挪到 Reactor 线程；
        // 结构化判决与守卫必须拿完整结果，可靠性阶段不流式
        BaseResponse<String> resp = orchestrator.execute(request.getMessage(), chatId);
        String original = resp.getData();
        Session session = latestSession(chatId, user.getId());
        // 非判责路径（P1/P3 追问、无规则、失败）：reason 为空，固定话术无需 LLM 润色，切片流式发送
        if (session == null || !StringUtils.hasText(session.getReason())) {
            return chunkStream(original);
        }
        return presentStream(request.getMessage(), original, session);
    }

    @Override
    public boolean deleteChat(String chatId) {
        User user = userService.getLoginUser();
        assertOwnership(chatId, user.getId());
        sessionMapper.delete(new LambdaQueryWrapper<Session>()
                .eq(Session::getChatId, chatId)
                .eq(Session::getUserId, user.getId()));
        // 同步清理 LLM 记忆，避免残留上下文。注意：不走 ChatMemoryRepository.deleteByConversationId，
        // 那里为保持记忆追加契约刻意空实现；这里是业务级删除，直接清表
        chatMemoryMapper.delete(new LambdaQueryWrapper<ChatMemory>()
                .eq(ChatMemory::getChatId, chatId));
        log.info("删除对话：userId={}, chatId={}", user.getId(), chatId);
        return true;
    }

    // ==================== 私有方法 ====================

    /**
     * P9 流式表达：本地轻量模型润色已验证结论，delta 逐段推送，done 携带最终全文。
     * 聚合完成后过一道表达守卫（规则编号不得丢失），再落 result/轨迹/token 增量。
     * 超时或异常降级为原结论：done 事件保证前端总能拿到权威文本。
     */
    private Flux<ServerSentEvent<String>> presentStream(String userPrompt, String original, Session session) {
        StringBuilder aggregated = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>(); //获取token usage
        AtomicReference<String> modelRef = new AtomicReference<>(); //获取流式输出的模型
        long start = System.currentTimeMillis();

        Flux<ServerSentEvent<String>> deltas = agent
                .streamPresentation(userPrompt, original, session.getReason())// 润色result+reason，返回总的流式输出
                .doOnNext(cr -> captureUsage(cr, usageRef, modelRef))// 收集 token usage 与模型名称，只取最后一个chunk的信息（每个chuck的modelName都是一样的，但是usage会累加）
                .map(cr -> cr.getResult() == null || cr.getResult().getOutput() == null
                        ? null : cr.getResult().getOutput().getText()) // Flux<ChatResponse> 转换为 Flux<String> （润色后的输出）
                .filter(StringUtils::hasText)
                .doOnNext(aggregated::append)
                .map(text -> sse("delta", text)); // 转换为Flux<ServerSentEvent<String>>，跟前端对接。

        // defer 保证收尾逻辑在全部 delta 之后执行：守卫 → 落库 → done
        Flux<ServerSentEvent<String>> finish = Flux.defer(() -> { //
            String presented = aggregated.toString();
            String finalText = presentGuard(original, presented);
            boolean replaced = !finalText.equals(presented);
            sessionService.saveJudgment(session.getId(), finalText, null);
            savePresentTrace(session, modelRef.get(), usageRef.get(), start, replaced);
            return Flux.just(sse("done", finalText)); // 这里 .just 是什么含义
        });

        return Flux.concat(deltas, finish)
                .timeout(PRESENT_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("P9 流式表达失败，降级输出原结论：chatId={}, reason={}",
                            session.getChatId(), e.getMessage());
                    return Flux.just(sse("done", original));
                });
    }

    /** 固定话术切片流式：不走 LLM，纯代码模拟打字机效果 */
    private Flux<ServerSentEvent<String>> chunkStream(String text) {
        return Flux.fromIterable(splitChunks(text, CHUNK_SIZE))
                .delayElements(Duration.ofMillis(20))
                .map(chunk -> sse("delta", chunk))
                .concatWith(Flux.just(sse("done", text)));
    }

    /** 流式 chunk 中捕获 usage 与 model：usage 通常只在末 chunk 非零，取最后一个有效值 */
    private void captureUsage(ChatResponse cr, AtomicReference<Usage> usageRef, AtomicReference<String> modelRef) {
        if (cr == null || cr.getMetadata() == null) {
            return;
        }
        Usage usage = cr.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
            usageRef.set(usage);
        }
        if (StringUtils.hasText(cr.getMetadata().getModel())) {
            modelRef.set(cr.getMetadata().getModel());
        }
    }

    /** P9 轨迹落库 + token 增量并入会话汇总（会话此时已 COMPLETED，故用 appendTokens） */
    private void savePresentTrace(Session session, String model, Usage usage, long startMs, boolean replaced) {
        long prompt = usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens();
        long completion = usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens();
        long total = usage == null || usage.getTotalTokens() == null ? 0L : usage.getTotalTokens();
        if (total == 0L) {
            total = prompt + completion;
        }
        RunTrace trace = RunTrace.builder()
                .sessionId(session.getId())
                .step("P9_PRESENT")
                .model(StringUtils.hasText(model) ? model : "ollama")
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(total)
                .timeCost((int) (System.currentTimeMillis() - startMs))
                .detail("流式表达, guardReplaced=" + replaced)
                .build();
        trace.setNeedTokenRecord(trace.isModelStep() ? 1 : 0);
        runTraceMapper.insert(trace);
        sessionService.appendTokens(session.getId(), total);
    }

    /** 取该对话最新一轮会话记录（本轮 execute 刚创建） */
    private Session latestSession(String chatId, Long userId) {
        List<Session> list = sessionMapper.selectList(new LambdaQueryWrapper<Session>()
                .eq(Session::getChatId, chatId)
                .eq(Session::getUserId, userId)
                .orderByDesc(Session::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    /**
     * 流式表达守卫：润色不得丢规则编号。原结论含规则编号而润色结果缺失时，整体替换回原结论。
     * 包级可见供单测。
     */
    static String presentGuard(String original, String presented) {
        if (!StringUtils.hasText(presented)) {
            return original;
        }
        Matcher matcher = RULE_ID_PATTERN.matcher(original);
        if (matcher.find() && !presented.contains(matcher.group())) {
            return original;
        }
        return presented;
    }

    /** 按固定字符数切片，包级可见供单测 */
    static List<String> splitChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }

    /**
     * 归属校验：chatId 下必须存在当前用户的记录，否则视为不存在（不暴露他人对话是否存在）。
     */
    private void assertOwnership(String chatId, Long userId) {
        ThrowUtil.throwIf(!StringUtils.hasText(chatId), ErrorCode.PARAMS_ERROR, "chatId 不能为空");
        long mine = sessionMapper.selectCount(new LambdaQueryWrapper<Session>()
                .eq(Session::getChatId, chatId)
                .eq(Session::getUserId, userId));
        ThrowUtil.throwIf(mine == 0, ErrorCode.NOT_FOUND_ERROR, "对话不存在或已删除");
    }

    /**
     * 由首条用户消息生成对话标题：压缩空白、截取前 20 字。包级可见供单测。
     */
    static String deriveTitle(String content) {
        if (!StringUtils.hasText(content)) {
            return DEFAULT_TITLE;
        }
        String normalized = content.strip().replaceAll("\\s+", " ");
        return normalized.length() <= TITLE_MAX_LEN
                ? normalized
                : normalized.substring(0, TITLE_MAX_LEN) + "...";
    }
}
