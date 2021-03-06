package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;


import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 模仿AddRequestHeaderGatewayFilterFactory及其父类设置自定义局部过滤器
 * 1.模仿AddRequestHeaderGatewayFilterFactory，实现apply方法
 * 2.模仿AbstractNameValueGatewayFilterFactory，自定义一个静态内部类pojo，接收配置文件的信息字段
 * 3.模仿AbstractNameValueGatewayFilterFactory，重写过滤器工厂构造方法，覆盖父类的构造方法
 * 4.模仿AbstractNameValueGatewayFilterFactory，重写shortcutFieldOrder方法，指定接收参数的字段顺序
 * 5.如果接收集合类型的参数（多个包含kv），重写shortcutType方法，指定接收字段类型
 */
@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties properties;

    @Override
    public GatewayFilter apply(PathConfig config) { // 内部类指定后，参数类型也改为内部类类型

        return (exchange, chain) -> {
            System.out.println("局部过滤器获取配置信息：key = " + config.getPaths());

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            // 1.判断当前请求的路径是否在拦截名单之中，不在则直接放行
            String curPath = request.getURI().getPath(); // 当前请求的路径
            List<String> paths = config.getPaths();// 拦截名单
            if (!paths.stream().anyMatch(path -> curPath.startsWith(path))) {
                return chain.filter(exchange);
            }

            // 2.获取请求中的token信息：异步-头信息；同步-cookie
            String token = request.getHeaders().getFirst("token"); // 异步
            if (StringUtils.isBlank(token)){ // 没有获取到，在尝试从cookie中获取
                MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                if (!CollectionUtils.isEmpty(cookies) && cookies.containsKey(properties.getCookieName())){
                    HttpCookie cookie = cookies.getFirst(properties.getCookieName());
                    token = cookie.getValue();
                }
            }

            // 3.判断token是否为空，为空则重定向到登录页面
            if (StringUtils.isBlank(token)){
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                return response.setComplete(); // 拦截后续业务逻辑
            }


            try {
                // 4.解析token信息，如果出现异常重定向到登录页面
                Map<String, Object> map = JwtUtils.getInfoFromToken(token, properties.getPublicKey());

                // 5.拿到载荷中ip和当前请求的ip比较，不一致说明别盗用，直接重定向登录页面
                String ip = map.get("ip").toString();
                String curIp = IpUtils.getIpAddressAtGateway(request);
                if (!StringUtils.equals(ip, curIp)){
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete(); // 拦截后续业务逻辑
                }

                // 6.把解析到的用户登录信息传递给后续服务
                request.mutate().header("userId", map.get("userId").toString()).build();
                exchange.mutate().request(request).build();

                // 7.放行
                return chain.filter(exchange);
            } catch (Exception e) {
                e.printStackTrace();
                // 解析出现异常，重定向到登录页面
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                return response.setComplete(); // 拦截后续业务逻辑
            }

        };
    }

    public AuthGatewayFilterFactory(){
        super(PathConfig.class);
    }


    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("paths");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Data
    public static class PathConfig {
        private List<String> paths;
    }
}
