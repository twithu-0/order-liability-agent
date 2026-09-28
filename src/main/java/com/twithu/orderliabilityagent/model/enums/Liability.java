package com.twithu.orderliabilityagent.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 责任归属（主责/次责共用枚举）。
 * <p>对应规则表 t_rule.default_liability / secondary_liability，
 * 以及判责结果表 t_liability_result.primary_liability / secondary_liability。</p>
 * <p>角色与级别不再用嵌套枚举承载（原 RoleType/Level 是包级私有，跨包取不到），
 * 改为从枚举名派生，见 {@link #role()} / {@link #isPrimary()}。</p>
 */
@Getter
@RequiredArgsConstructor
public enum Liability {

    /** 供应商主责 */
    SUPPLIER_PRIMARY("供应商主责"),

    /** 我司主责 */
    OURS_PRIMARY("我司主责"),

    /** 渠道主责 */
    CHANNEL_PRIMARY("渠道主责"),

    /** 供应商次责 */
    SUPPLIER_SECONDARY("供应商次责"),

    /** 我司次责 */
    OURS_SECONDARY("我司次责"),

    /** 渠道次责 */
    CHANNEL_SECONDARY("渠道次责"),

    /** 共责，需人工确定分摊 */
    SHARED("共责"),

    /** 客户原因 */
    CUSTOMER("客户原因"),

    /** 不可抗力 */
    FORCE_MAJEURE("不可抗力"),

    /** 按合同/SLA 约定处理 */
    CONTRACT("按合同处理"),

    /** 证据不足，无法判定 */
    UNDETERMINED("无法判定"),

    /** 无次责方 */
    NONE("无");

    /**
     * 数据库存储值，与枚举名一致。显式标注 @EnumValue 是为了让 MyBatis-Plus 的
     * CompositeEnumTypeHandler 按 code 读写，将来重命名常量不影响存量数据。
     */
    @EnumValue
    private final String code = name();

    /** 中文名称 */
    private final String desc;

    /**
     * 按 code 反查枚举；兼容中文描述，便于吃掉 rule.json 里"供应商"这类脏值。未匹配返回 null。
     */
    @JsonCreator
    public static Liability of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return Arrays.stream(values())
                .filter(l -> l.code.equalsIgnoreCase(v) || l.desc.equals(v))
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
