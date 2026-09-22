package com.twithu.orderliabilityagent.core;

import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import com.twithu.orderliabilityagent.service.SessionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 主要任务流程编排器
 */
@Component
public class LiabilityOrchestrator {
    @Resource
    private SessionService sessionService;
    @Resource
    private LiabilityAgent agent;

    public BaseResponse<String> execute(String userPrompt, String chatId){
        // 记录对话
        // 预处理用户输入
        PreprocessResult preResult = UserPromptPreprocess.extract(userPrompt);
        if (!preResult.isLiabilityRequest()){ // 先限制只能进行订单问题相关对话
            return ResultUtil.success("无效订单责任问题，请检查输入的订单号、供应商、渠道信息。");
        }
        // 使用LLM抽取用户输入关键词
        ExtractResult extractResult = agent.extract(preResult, chatId);
        System.out.println(extractResult);
        // 比对证据
        // 检索规则 判责
        // 校验、落库

        return ResultUtil.success("处理成功");
    }
}
