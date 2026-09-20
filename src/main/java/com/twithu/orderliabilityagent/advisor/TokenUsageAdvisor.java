package com.twithu.orderliabilityagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Token 消耗计量 Advisor：打印 model / promptTokens / completionTokens / totalTokens / 耗时，
 * 并把累计用量写入 advisor context，供业务层落库。
 */
@Slf4j
public class TokenUsageAdvisor implements CallAdvisor, StreamAdvisor {

    /** context 中累计 prompt token 的 key，业务层可从 ChatClientResponse.context() 取 */
    public static final String CTX_PROMPT_TOKENS = "token_usage_prompt";
    public static final String CTX_COMPLETION_TOKENS = "token_usage_completion";
    public static final String CTX_MODEL = "token_usage_model";

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        // 放在链路上层，响应阶段最后执行，保证 Memory / RAG 补进上下文后才统计
        return Integer.MIN_VALUE + 100;
    }

    private Usage extractUsage(ChatClientResponse response) {
        if (response == null) {
            return null;
        }
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return null;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        return metadata == null ? null : metadata.getUsage();
    }

    private String extractModel(ChatClientResponse response) {
        return response != null && response.chatResponse() != null
                && response.chatResponse().getMetadata() != null
                ? response.chatResponse().getMetadata().getModel() : "unknown";
    }

    private void logUsage(ChatClientResponse response, long costMs) {
        Usage usage = extractUsage(response);
        if (usage == null) {
            log.warn("[TokenUsage] 响应中没有 usage 元数据，model={}", extractModel(response));
            return;
        }
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        Integer totalTokens = usage.getTotalTokens();

        // 回填 context，业务层（Orchestrator）可直接读取用于 markCompleted
        if (response.context() != null) {
            response.context().put(CTX_PROMPT_TOKENS, promptTokens);
            response.context().put(CTX_COMPLETION_TOKENS, completionTokens);
            response.context().put(CTX_MODEL, extractModel(response));
        }

        log.info("""
                
                
                -------------------- token usage ------------------------------
                model          : {}
                promptTokens   : {}
                completionTokens: {}
                totalTokens    : {}
                latency        : {} ms
                ------------------------------------------------------------
                
                """, extractModel(response), promptTokens, completionTokens, totalTokens, costMs);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        logUsage(response, System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        // 流式场景 usage 只在最后一个 chunk 返回，中间 chunk 通常是 0 / null
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        AtomicReference<String> model = new AtomicReference<>("unknown");
        AtomicReference<ChatClientResponse> terminal = new AtomicReference<>();

        return chain.nextStream(request)
                .doOnNext(resp -> {
                    Usage u = extractUsage(resp);
                    if (u != null && u.getTotalTokens() != null && u.getTotalTokens() > 0) {
                        lastUsage.set(u);
                        terminal.set(resp);
                    }
                    if (resp != null && resp.chatResponse() != null
                            && resp.chatResponse().getMetadata().getModel() != null) {
                        model.set(resp.chatResponse().getMetadata().getModel());
                    }
                })
                .doOnComplete(() -> {
                    Usage u = lastUsage.get();
                    if (u == null) {
                        log.warn("[TokenUsage] 流式响应未拿到 usage，model={}", model.get());
                        return;
                    }
                    log.info("""
                            -------------------- token usage (stream) -------------------
                            model          : {}
                            promptTokens   : {}
                            completionTokens: {}
                            totalTokens    : {}
                            latency        : {} ms
                            ------------------------------------------------------------
                            """, model.get(), u.getPromptTokens(), u.getCompletionTokens(),
                            u.getTotalTokens(), System.currentTimeMillis() - start);
                });
    }
}
