package com.atguigu.gmall.sms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.sms.vo.SeckillSkuNoticeEntity;

/**
 * 秒杀商品通知订阅
 *
 * @author bailiang
 * @email 724454448@qq.com
 * @date 2020-10-28 18:59:56
 */
public interface SeckillSkuNoticeService extends IService<SeckillSkuNoticeEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

