package com.twithu.orderliabilityagent.core.evidence;

import com.twithu.orderliabilityagent.model.dto.OrderEvidence;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * mock 订单证据库：从 classpath:mock/orders.json 加载四跳快照，key 为订单号。
 * <p>将来接真实订单系统时，只需替换本类实现，{@link OrderEvidenceTool} 与上层流程不变。</p>
 */
@Component
public class MockOrderEvidenceRepository {

    private final Map<String, OrderEvidence> orders;

    public MockOrderEvidenceRepository(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = new ClassPathResource("mock/orders.json").getInputStream()) {
            orders = objectMapper.readValue(in, new TypeReference<Map<String, OrderEvidence>>() {});
        }
    }

    /** 按订单号取证据快照，查不到返回 null */
    public OrderEvidence find(String orderNo) {
        return orderNo == null ? null : orders.get(orderNo);
    }
}
