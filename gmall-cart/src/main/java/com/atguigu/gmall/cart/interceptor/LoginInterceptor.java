package com.atguigu.gmall.cart.interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

/**
 * 自定义拦截器，此外还需要配置拦截器
 */
@EnableConfigurationProperties(JwtProperties.class)
@Component
public class LoginInterceptor implements HandlerInterceptor {

    //public String userId;

    // ThreadLocal为线程的局部变量
    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Autowired
    private JwtProperties properties;

    /**
     * 在目标方法完成之前，获取登录信息
     * @param request
     * @param response
     * @param handler
     * @return  true为放行，false为拦截
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        System.out.println("前置方法。。。。。。。。。。。。");

        UserInfo userInfo = new UserInfo();

        String userKey = CookieUtils.getCookieValue(request, this.properties.getUserKey());
        if (StringUtils.isBlank(userKey)){
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.properties.getUserKey(), userKey, this.properties.getExpire());
        }
        userInfo.setUserKey(userKey);

        // 从token中获取用户信息，得到userId，赋值给userInfo中的userId
        String token = CookieUtils.getCookieValue(request, this.properties.getCookieName());
        if (StringUtils.isNotBlank(token)){
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());
            userInfo.setUserId(Long.valueOf(map.get("userId").toString()));
        }

        // 为保证线程安全，使用ThreadLocal
        THREAD_LOCAL.set(userInfo);
        return true;
    }

    // 获取线程局部变量
    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 这里我们使用了tomcat线程池，所以显式的删除线程的局部变量中的值，是必须的。否则会导致内存泄漏（线程局部变量不能被回收，无法利用内存）。
        THREAD_LOCAL.remove();
    }
}
