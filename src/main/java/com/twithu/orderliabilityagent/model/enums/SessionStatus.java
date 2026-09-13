package com.twithu.orderliabilityagent.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 判责会话状态。
 * <p>对应会话表 t_liability_session.status 字段。</p>
 */
@Getter
@RequiredArgsConstructor
public enum SessionStatus {

    /** 已创建，待处理 */
    PENDING("PENDING", "待处理"),

    /** Agent 处理中 */
    RUNNING("RUNNING", "处理中"),

    /** 处理完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 处理失败 */
    FAILED("FAILED", "失败");

    /** 数据库存储值 */
    private final String code;

    /** 中文名称 */
    private final String desc;

    /**
     * 按 code 反查枚举，未匹配返回 null。
     */
    public static SessionStatus of(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
