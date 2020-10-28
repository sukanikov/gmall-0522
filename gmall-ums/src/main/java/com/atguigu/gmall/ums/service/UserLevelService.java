package com.atguigu.gmall.ums.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.ums.entity.UserLevelEntity;

import java.util.Map;

/**
 * 会员等级表
 *
 * @author bailiang
 * @email 724454448@qq.com
 * @date 2020-10-28 18:58:53
 */
public interface UserLevelService extends IService<UserLevelEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

