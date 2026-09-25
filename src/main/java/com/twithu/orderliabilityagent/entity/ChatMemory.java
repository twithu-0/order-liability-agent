package com.twithu.orderliabilityagent.entity;

import com.baomidou.mybatisplus.annotation.*;
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
@TableName("t_chat_memory")
public class ChatMemory implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话编号，如 SES-20261116-000001 */
    private String sessionNo;


    /** 会话内容 */
    private String content;

    /** 会话消息的类型 */
    private String type;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 实际对话顺序 */
    private Integer sequenceId;

}
