package com.twithu.orderliabilityagent.controller;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.core.LiabilityOrchestrator;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {

    @Resource
    LiabilityOrchestrator orchestrator;

    @RequestMapping("/liability/judge")
    public BaseResponse<String> liabilityJudge(@RequestParam String userPrompt,
                                               @RequestParam(defaultValue = "default") String chatId) {
        return orchestrator.execute(userPrompt, chatId);
    }
}
