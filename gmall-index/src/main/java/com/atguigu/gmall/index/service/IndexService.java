package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.aspect.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

//    @Autowired
//    private StringRedisTemplate redisTemplate;
//
//    @Autowired
//    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:categories:";

    public List<CategoryEntity> queryLv1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l);

        return listResponseVo.getData();
    }

    @GmallCache(prefix = KEY_PREFIX, timeout = 129600l, random = 14400, lock = "lock:cates:")   //设置缓存时间为3个月，3个月后在10天内陆续过期
    public List<CategoryEntity> queryLv2CategoriesWithSubsByPid(Long pid) {
        ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryLv2CategoriesWithSubsByPid(pid);
        List<CategoryEntity> categoryEntities = responseVo.getData();
        return categoryEntities;
    }


//    public List<CategoryEntity> queryLv2CategoriesWithSubsByPid(Long pid) {
//        //先查缓存，命中就直接返回
//        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
//
//        //命中了直接反序列化，返回
//        if (StringUtils.isNotBlank(json)) {
//            return JSON.parseArray(json, CategoryEntity.class);
//        }
//
//        RLock lock = this.redissonClient.getLock("lock:" + pid);
//        lock.lock();
//
//        try {
//            //没有命中，执行业务远程调用，获取数据，并放入缓存
//            ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryLv2CategoriesWithSubsByPid(pid);
//            List<CategoryEntity> categoryEntities = responseVo.getData();
//            if (CollectionUtils.isEmpty(categoryEntities)){
//                // 为了防止缓存穿透，数据即使为null页缓存，为了防止缓存数据过多，缓存时间设置的极短
//                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 1, TimeUnit.MINUTES);
//            } else {
//                // 为了防止缓存雪崩，给缓存时间添加随机值（3个月后陆续失效）
//                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 2160 + new Random().nextInt(900), TimeUnit.HOURS);
//            }
//
//            return categoryEntities;
//        } finally {
//            lock.unlock();
//        }
//
//    }
}
