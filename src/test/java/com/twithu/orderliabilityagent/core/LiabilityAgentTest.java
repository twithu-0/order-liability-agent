package com.twithu.orderliabilityagent.core;

import cn.hutool.core.util.RandomUtil;
import com.twithu.orderliabilityagent.MyTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@MyTest
class LiabilityAgentTest {

    @Resource
    LiabilityAgent liabilityAgent;

    @Test
    void chatClient() {
        String chatId = "chat" + RandomUtil.randomNumbers(4);
        // liabilityAgent.chat("你好,我叫小A","abc");
        // liabilityAgent.chat("上一个问题里我是谁？","abc");
        liabilityAgent.chatOutputStructure("小明很累，明天要去公园休息",chatId);
        liabilityAgent.chatOutputStructure("妈妈今天在家做饭",chatId);
    }

    @Test
    void testChatWithRag(){
        String chatId = "chat" + RandomUtil.randomNumbers(4);
        String prompt = "有没有酒店入住呀";
        String dimension = "hotel1";
        String s = liabilityAgent.chatWithRag(prompt, chatId, dimension);
        System.out.println(s);
    }
}