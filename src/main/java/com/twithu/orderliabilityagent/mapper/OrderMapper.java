package com.twithu.orderliabilityagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twithu.orderliabilityagent.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单证据 Mapper，基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
