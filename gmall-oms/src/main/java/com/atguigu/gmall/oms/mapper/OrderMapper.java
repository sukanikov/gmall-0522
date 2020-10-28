package com.atguigu.gmall.oms.mapper;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author bailiang
 * @email 724454448@qq.com
 * @date 2020-10-28 18:57:28
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
	
}
