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

    @Test
    void testCompressHistory(){
        QueryTransformer compressTransformer = AdvisorFactory.createCompressionTransformer(ollamaChatModel);
        Query query = Query.builder()
                .text("那这个问题算谁的责任啊啊啊，太气人了")
                .history(List.of(
                        new UserMessage("MT12345222  渠道去哪儿 供应商美团 酒店名字： 京州大酒店，电话134231。客人反馈找不到酒店，电话也联系不上"),
                        new AssistantMessage("我认为是渠道责任。经核查，渠道展示的电话号码（134231）与我司及供应商下发的号码（010-88886666）不一致，" +
                                "且该号码实际无法接通，符合规则 H-04 中“酒店电话错误、空号、非前台电话”的情形。地址信息因证据缺失暂无法定责。结论仅供参考，如有异议请人工复核。")))
                .build();

        Query compressed = compressTransformer.transform(query);
        System.out.println("压缩后独立查询: " + compressed.text());
    }

    /**
     * 测试压缩历史对话 和 改写查询
     */
    @Test
    void testRewritePrompt() {
        QueryTransformer rewriteTransformer = AdvisorFactory.createRewriteTransformer(ollamaChatModel);
        Query query = new Query("那这个问题算谁的责任啊啊啊，太气人了");
        Query rewritten = rewriteTransformer.transform(query);
        System.out.println("改写后检索查询: " + rewritten.text());
    }

    @Test
    void compressionShortCircuitOnEmptyHistory() {
        QueryTransformer compression = AdvisorFactory.createCompressionTransformer(deepSeekChatModel);
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