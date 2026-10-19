package com.twithu.orderliabilityagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twithu.orderliabilityagent.entity.RunTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运行轨迹 Mapper，记录 P1-P8 各步骤耗时与摘要，供回放与评测。
 */
@Mapper
public interface RunTraceMapper extends BaseMapper<RunTrace> {
}
