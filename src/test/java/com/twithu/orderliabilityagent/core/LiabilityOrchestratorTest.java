package com.twithu.orderliabilityagent.core;

import cn.hutool.core.util.RandomUtil;
import com.twithu.orderliabilityagent.MyTest;
import com.twithu.orderliabilityagent.common.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@MyTest
class LiabilityOrchestratorTest {

    @Autowired
    private LiabilityOrchestrator liabilityOrchestrator;

    @Test
    void execute() {
        String chatId = "chat" + RandomUtil.randomNumbers(4);
        // String chatId = "chat6159";
        BaseResponse<String> res = liabilityOrchestrator.execute("MT12345222  渠道去哪儿 供应商美团 酒店名字： 京州大酒店，电话134231。客人反馈找不到酒店，电话也联系不上"
                , chatId);
        System.out.println(res.getData());
    }
}