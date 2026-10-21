package com.twithu.orderliabilityagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话列表项 / 创建对话返回体，供前端侧边栏渲染。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatVO {

    /** 对话编号，后续历史查询 / 发送消息都带它 */
    private String chatId;

    /** 对话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最近活跃时间，列表按它倒序 */
    private LocalDateTime updateTime;
}
