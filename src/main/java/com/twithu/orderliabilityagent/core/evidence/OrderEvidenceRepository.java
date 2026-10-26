package com.twithu.orderliabilityagent.core.evidence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.twithu.orderliabilityagent.entity.Order;
import com.twithu.orderliabilityagent.mapper.OrderMapper;
import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 订单证据库：从 t_order 读取四跳快照，JSON 列解析为 OrderEvidence 的字段 Map。
 * 替代原 MockOrderEvidenceRepository（classpath JSON 文件），上层 OrderEvidenceTool 与编排器不变。
 * null 语义保留：快照列为 null/空 → Map 为 null，比对器据此判 EVIDENCE_GAP。
 */
@Component
@RequiredArgsConstructor
public class OrderEvidenceRepository {

    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    /** 按订单号取证据快照，查不到返回 null */
    public OrderEvidence find(String orderNo) {
        if (orderNo == null) {
            return null;
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            return null;
        }
        return new OrderEvidence(order.getOrderNo(), order.getChannel(), order.getSupplier(),
                parseSnapshot(order.getSupplierSource()),
                parseSnapshot(order.getOurTransfer()),
                parseSnapshot(order.getChannelDisplay()),
                parseSnapshot(order.getCustomerActual()));
    }

    private Map<String, String> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }
}
