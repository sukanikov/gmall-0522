package com.atguigu.gmall.index.aspect;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {
    //缓存key前缀
    String prefix() default "gmall:cache";

    //缓存过期时间，单位：分钟
    long timeout() default 5l;

    //在缓存时间基础上添加随机值，防止缓存雪崩
    int random() default 5;

    //给缓存添加分布式锁，防止缓存击穿
    String lock() default "lock:";
}
