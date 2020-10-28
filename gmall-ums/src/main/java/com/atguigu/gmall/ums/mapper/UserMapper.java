package com.atguigu.gmall.ums.mapper;

import com.atguigu.gmall.ums.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表
 * 
 * @author bailiang
 * @email 724454448@qq.com
 * @date 2020-10-28 18:58:53
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
	
}
