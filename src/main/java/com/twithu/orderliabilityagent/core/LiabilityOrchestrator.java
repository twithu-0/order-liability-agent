package com.twithu.orderliabilityagent.core;

import cn.hutool.core.util.RandomUtil;
import com.twithu.orderliabilityagent.common.BaseResponse;
import com.twithu.orderliabilityagent.common.ResultUtil;
import com.twithu.orderliabilityagent.entity.Session;
import com.twithu.orderliabilityagent.entity.User;
import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import com.twithu.orderliabilityagent.service.SessionService;
import com.twithu.orderliabilityagent.service.UserService;
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
    @Resource
    private UserService userService;

    public BaseResponse<String> execute(String userPrompt, String chatId){
        User user = userService.getLoginUser();
        // 会话记录入库
        Session session = sessionService.createSession(user.getId(), userPrompt);
        sessionService.markRunning(session.getId());

        try {
            // 预处理用户输入
            PreprocessResult preResult = UserPromptPreprocess.extract(userPrompt);
            // 先限制只能进行订单问题相关对话
            if (!preResult.isLiabilityRequest()){
                return ResultUtil.success("无效订单责任问题，请检查输入的订单号、供应商、渠道信息并再次进行对话补全相关信息。");
            }
            // 调用LLM抽取用户输入关键词
            ExtractResult extractResult = agent.extract(preResult, chatId);
            // 比对证据

            // 调用LLM检索规则 判责

            // 校验、落库
        } catch (Exception e) {
            sessionService.markFailed(session.getId(), e.getMessage());
        }

        return ResultUtil.success("处理成功");
    }
}
