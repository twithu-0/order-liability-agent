package com.twithu.orderliabilityagent.rag.preretrieval;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * 多轮对话查询压缩（pre-retrieval Query Transform）。
 * 原生实现单轮（空历史）也会调用 LLM，这里包一层短路：无历史直接透传，省一次调用。
 */
public class HistoryCompressionTransformer implements QueryTransformer {

    private static final PromptTemplate COMPRESSION_TEMPLATE = new PromptTemplate("""
            根据以下对话历史和最新问题，合成一个无需上下文即可理解的独立查询。
            要求：保留用户真实意图；把历史中的指代（如"那个订单""这家酒店"）补全为具体信息；只输出查询文本，不要解释。

            对话历史：
            {history}

            最新问题：
            {query}

            独立查询：
            """);

    private final CompressionQueryTransformer delegate;

    public HistoryCompressionTransformer(ChatClient.Builder chatClientBuilder) {
        this.delegate = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .promptTemplate(COMPRESSION_TEMPLATE)
                .build();
    }

    @Override
    public Query transform(Query query) {
        if (query.history() == null || query.history().isEmpty()) { //空历史不进行压缩
            return query;
        }
        return delegate.transform(query);
    }
}
