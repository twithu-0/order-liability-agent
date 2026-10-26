package com.twithu.orderliabilityagent.core.evidence;

import com.twithu.orderliabilityagent.common.exception.BusinessException;
import com.twithu.orderliabilityagent.common.exception.ErrorCode;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * P4 获取证据：以 Spring AI @Tool 形式暴露订单证据查询。
 * 数据源为 t_order 表（四跳快照 JSON 列）。
 * 主链路由 Orchestrator 确定性调用（稳定、可单测）；
 * 后续做判责 ChatClient 时可通过 .tools(orderEvidenceTool) 交给模型自主调用，
 * 演示完整 Tool Calling 循环。
 */
@Service
public class OrderEvidenceTool {

    private final OrderEvidenceRepository repository;

    public OrderEvidenceTool(OrderEvidenceRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "按订单号查询订单全链路数据快照：供应商源数据、我司传输数据、渠道展示数据、客人实际体验值")
    public OrderEvidence getOrderEvidence(
            @ToolParam(description = "订单号，MT开头+8位数字，如 MT12345222") String orderNo) {
        OrderEvidence evidence = repository.find(orderNo);
        if (evidence == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                    "未查询到订单 " + orderNo + " 的证据数据，请核对订单号，或转人工处理");
        }
        return evidence;
    }
}
