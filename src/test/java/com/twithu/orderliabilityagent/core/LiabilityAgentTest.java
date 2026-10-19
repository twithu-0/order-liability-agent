package com.twithu.orderliabilityagent.core;

import cn.hutool.core.util.RandomUtil;
import com.twithu.orderliabilityagent.MyTest;
import com.twithu.orderliabilityagent.rag.AdvisorFactory;
import com.twithu.orderliabilityagent.rag.preretrieval.HistoryCompressionTransformer;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MyTest
class LiabilityAgentTest {

    @Resource
    LiabilityAgent liabilityAgent;

    @Resource
    ChatModel deepSeekChatModel;

    @Resource
    ChatModel ollamaChatModel;

    @Test
    void chatClient() {
        String chatId = "chat" + RandomUtil.randomNumbers(4);
        liabilityAgent.chatOutputStructure("小明今天想休息",chatId);
        // liabilityAgent.chatOutputStructure("妈妈今天在家做饭",chatId);
    }

    @Test
    void testChatWithRag(){
        String chatId = "chat" + RandomUtil.randomNumbers(4);
        String prompt = "你知道酒店在哪儿吗啊啊啊啊";
        String dimension = "hotel";
        String s = liabilityAgent.chatWithRag(prompt, chatId, dimension);
        System.out.println(s);
        System.out.println(liabilityAgent.chatWithRag("我找不到酒店在哪儿，好烦呐", chatId, dimension));
    }

    /**
     * 测试压缩历史对话 和 改写查询
     */
    @Test
    void queryTransformWithHistory() {
        List<QueryTransformer> transformers = AdvisorFactory.createQueryTransformers(ollamaChatModel);
        QueryTransformer compression = transformers.get(0);
        QueryTransformer rewrite = transformers.get(1);

        Query query = Query.builder()
                .text("那这个问题算谁的责任啊啊啊，太气人了")
                .history(List.of(
                        new UserMessage("订单20261120001，在飞猪订的豪华大床房含双早，到店酒店说只有双床房，早餐也不给"),
                        new AssistantMessage("已了解情况：床型与订单不符，且承诺的早餐未提供。")))
                .build();

        Query compressed = compression.transform(query);
        System.out.println("压缩后独立查询: " + compressed.text());
        assertNotEquals(query.text(), compressed.text());

        Query rewritten = rewrite.transform(compressed);
        System.out.println("改写后检索查询: " + rewritten.text());
        assertFalse(rewritten.text().contains("20261120001"));
    }

    @Test
    void compressionShortCircuitOnEmptyHistory() {
        HistoryCompressionTransformer compression = (HistoryCompressionTransformer) AdvisorFactory.createQueryTransformers(deepSeekChatModel).get(0);
        Query query = Query.builder().text("酒店电话是空号").build();
        assertSame(query, compression.transform(query));
    }

    @Resource
    PgVectorStore pgVectorStore;
    @Test
    void similaritySearch() {
        AdvisorFactory.SimilaritySearch(pgVectorStore, "我找不到酒店在哪儿，好烦呐");
    }
}