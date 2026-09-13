package com.twithu.orderliabilityagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twithu.orderliabilityagent.entity.Session;
import org.apache.ibatis.annotations.Mapper;

/**
 * 判责会话 Mapper。
 */
@Mapper
public interface SessionMapper extends BaseMapper<Session> {
}
