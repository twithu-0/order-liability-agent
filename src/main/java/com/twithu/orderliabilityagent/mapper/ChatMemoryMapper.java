package com.twithu.orderliabilityagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twithu.orderliabilityagent.entity.ChatMemory;
import com.twithu.orderliabilityagent.entity.Rule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 判责规则 Mapper。
 * <p>基础 CRUD 由 MyBatis-Plus 提供；规则检索（metadata 过滤 + 混合检索）
 * 在 RAG 服务层实现，可按需追加自定义方法。</p>
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemory> {
}
