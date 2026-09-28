package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_run_trace")
public class RunTrace implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话编号，等于 conversationId，用于把记忆/结果/轨迹串起来 */
    private String sessionNo;

    /** 调用的工具名（P5b 证据采集等），非工具步骤为 null */
    private String tool;

    /** 调用的模型名，非模型步骤为 null */
    private String model;

    /** 输入 token */
    private Long promptTokens;

    /** 输出 token */
    private Long completionTokens;

    /** 合计 token */
    private Long totalTokens;

    /** 该步骤耗时（毫秒） */
    private Integer timeCost;

    /** 步骤摘要：命中规则数 / firstBadHop / 守卫拒绝原因等，便于回放 */
    private String detail;

    /** 创建时间，由 DB 默认值维护 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /** 是否为模型调用步骤（决定是否统计 token） */
    public boolean isModelStep() {
        return model != null && !model.isBlank();
    }
}
