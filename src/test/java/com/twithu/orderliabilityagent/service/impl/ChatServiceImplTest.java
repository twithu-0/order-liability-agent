package com.twithu.orderliabilityagent.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatServiceImpl 纯单元测试：标题截取 / 流式切片 / 流式表达守卫，不依赖数据库与 LLM。
 */
class ChatServiceImplTest {

    @Test
    void deriveTitleShortContentKeptAsIs() {
        assertEquals("床型不一致", ChatServiceImpl.deriveTitle("床型不一致"));
    }

    @Test
    void deriveTitleBlankFallsBackToDefault() {
        assertEquals("新对话", ChatServiceImpl.deriveTitle(null));
        assertEquals("新对话", ChatServiceImpl.deriveTitle("   "));
    }

    @Test
    void deriveTitleCollapsesWhitespaceAndTruncates() {
        String longComplaint = "订单MT12345678页面写的是大床房，到店前台却给了双床房，客人非常不满要求赔偿";
        String title = ChatServiceImpl.deriveTitle(longComplaint);
        assertEquals(23, title.length()); // 20 字 + 省略号
        assertEquals(longComplaint.substring(0, 20) + "...", title);
        assertEquals("订单 MT12345678 床型不符", ChatServiceImpl.deriveTitle("  订单\nMT12345678\t床型不符  "));
    }

    @Test
    void splitChunksCoversFullTextWithoutLoss() {
        String text = "请补充订单号（MT开头+8位数字）后再次提问。";
        List<String> chunks = ChatServiceImpl.splitChunks(text, 12);
        assertEquals(text, String.join("", chunks));
        assertTrue(chunks.size() > 1);
        assertTrue(ChatServiceImpl.splitChunks(null, 12).isEmpty());
        assertTrue(ChatServiceImpl.splitChunks("", 12).isEmpty());
    }

    @Test
    void presentGuardKeepsPolishedTextWithRuleId() {
        String original = "我认为是渠道主责。引用规则 D-08 床型展示不符。结论仅供参考，如有异议请人工复核。";
        String presented = "非常抱歉给您带来不便，经核实本单为渠道主责（规则 D-08）。结论仅供参考，如有异议请人工复核。";
        assertEquals(presented, ChatServiceImpl.presentGuard(original, presented));
    }

    @Test
    void presentGuardFallsBackWhenRuleIdLostOrEmpty() {
        String original = "我认为是渠道主责。引用规则 D-08 床型展示不符。";
        assertEquals(original, ChatServiceImpl.presentGuard(original, "非常抱歉，经核实本单为渠道主责。"));
        assertEquals(original, ChatServiceImpl.presentGuard(original, ""));
        assertEquals(original, ChatServiceImpl.presentGuard(original, null));
    }

    @Test
    void presentGuardKeepsTextWhenOriginalHasNoRuleId() {
        assertEquals("润色后的追问话术", ChatServiceImpl.presentGuard("请补充订单号。", "润色后的追问话术"));
    }
}
