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
            你是酒店订单判责系统的对话压缩模块。根据对话历史和最新问题，合成一个无需上下文即可理解的独立查询。

            核心规则：只处理"当前正在进行的订单问题"，严禁混入其他订单的信息。
            1. 一次对话可能先后咨询多个订单。凡助手已给出判责结论（如"我认为是xx责任"）的订单视为已完结，
               其订单号、争议字段、承诺值、实际情况等一律不得出现在压缩结果中。
            2. 判断最新问题归属哪个订单：
               - 最新问题带订单号：只合并该订单的历史（通常没有），其余订单信息全部忽略。
               - 最新问题不带订单号：视为对最近一个"尚未完结"问题的补充（补订单号/争议字段/实际情况），只合并该问题的历史。
               - 助手最后一条回复是追问（如"请补充订单号""请补充实际情况"）时，最新问题就是对该追问的回答。
            3. 指代补全（如"那个订单""这家酒店"）只允许指向当前问题的订单，不得指向已完结订单。
            4. 当前问题的订单号（MT开头+8位数字）必须原样保留在查询中。
            5. 保留用户真实意图与核心事实（争议字段、承诺值、实际情况），不要添加用户未提供的信息。
            6. 只输出压缩后的查询文本，不要解释，不要复述规则。

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
