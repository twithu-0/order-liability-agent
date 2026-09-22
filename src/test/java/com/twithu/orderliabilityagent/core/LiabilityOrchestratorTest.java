package com.twithu.orderliabilityagent.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LiabilityOrchestratorTest {

    @Autowired
    private LiabilityOrchestrator liabilityOrchestrator;

    @Test
    void execute() {
        liabilityOrchestrator.execute("MT12345222  渠道去哪儿 供应商美团 酒店名字： 京州大酒店，电话134231。客人反馈找不到酒店，电话也联系不上"
        , "1a2ff34d567");
    }
}