package com.twithu.orderliabilityagent.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 责任归属（主责/次责共用枚举）。
 * <p>对应规则表 t_rule.default_liability / secondary_liability，
 * 以及判责结果表 t_liability_result.primary_liability / secondary_liability。</p>
 */
@Getter
@RequiredArgsConstructor
public enum Liability {

    /** 供应商主责 */
    SUPPLIER_PRIMARY("SUPPLIER_PRIMARY", "供应商主责", RoleType.SUPPLIER, Level.PRIMARY),

    /** 我司主责 */
    OURS_PRIMARY("OURS_PRIMARY", "我司主责", RoleType.OURS, Level.PRIMARY),

    /** 渠道主责 */
    CHANNEL_PRIMARY("CHANNEL_PRIMARY", "渠道主责", RoleType.CHANNEL, Level.PRIMARY),

    /** 供应商次责 */
    SUPPLIER_SECONDARY("SUPPLIER_SECONDARY", "供应商次责", RoleType.SUPPLIER, Level.SECONDARY),

    /** 我司次责 */
    OURS_SECONDARY("OURS_SECONDARY", "我司次责", RoleType.OURS, Level.SECONDARY),

    /** 渠道次责 */
    CHANNEL_SECONDARY("CHANNEL_SECONDARY", "渠道次责", RoleType.CHANNEL, Level.SECONDARY),

    /** 共责，需人工确定分摊 */
    SHARED("SHARED", "共责", RoleType.NONE, Level.NONE),

    /** 客户原因 */
    CUSTOMER("CUSTOMER", "客户原因", RoleType.NONE, Level.NONE),

    /** 不可抗力 */
    FORCE_MAJEURE("FORCE_MAJEURE", "不可抗力", RoleType.NONE, Level.NONE),

    /** 按合同/SLA 约定处理 */
    CONTRACT("CONTRACT", "按合同处理", RoleType.NONE, Level.NONE),

    /** 证据不足，无法判定 */
    UNDETERMINED("UNDETERMINED", "无法判定", RoleType.NONE, Level.NONE),

    /** 无次责方 */
    NONE("NONE", "无", RoleType.NONE, Level.NONE);

    /** 责任方角色 */
    enum RoleType {
        SUPPLIER, OURS, CHANNEL, NONE
    }

    /** 主责/次责级别 */
    enum Level {
        PRIMARY, SECONDARY, NONE
    }

    /** 数据库存储值 */
    private final String code;

    /** 中文名称 */
    private final String desc;

    private final RoleType role;

    private final Level level;

    /**
     * 按 code 反查枚举，未匹配返回 null。
     */
    public static Liability of(String code) {
        return Arrays.stream(values())
                .filter(l -> l.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否为明确主责（可自动给出参考结论的三方主责）。
     */
    public boolean isConcretePrimary() {
        return this == SUPPLIER_PRIMARY || this == OURS_PRIMARY || this == CHANNEL_PRIMARY;
    }
}
