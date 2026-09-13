package com.twithu.orderliabilityagent.controller;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.core.LiabilityOrchestrator;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {

    @Resource
    LiabilityOrchestrator orchestrator;

    @RequestMapping("/liability/judge")
    public BaseResponse<String> liabilityJudge() {
        return orchestrator.execute("liability judge", "1a2ff34d567");
    }
}
