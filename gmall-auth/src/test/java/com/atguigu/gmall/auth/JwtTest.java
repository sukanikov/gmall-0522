package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {
    private static final String pubKeyPath = "E:\\IdeaProjects\\rsa\\rsa.pub";
    private static final String priKeyPath = "E:\\IdeaProjects\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    /**
     * 测试生成公钥和私钥
     * @throws Exception
     */
    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    /**
     * 测试生成token
     * @throws Exception
     */
    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 2);
        System.out.println("token = " + token);
    }

    /**
     * 测试解析token
     * @throws Exception
     */
    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MDU1MzE3MzB9.bWvxjtxQW4HdMeAt9TY-O7FkHo1lbrDmDJZrZfuYLAb3UuFOJf5OKMBfsa4Wjl3ijg_boyhl49-kxqGe-IdawR3nKgC07gslMiKD6pggy-_2MrGqQ3YN_5OZfXGFCMINxySQ5hRvj56sIMMU3ZTd5KzLYNszXR9n9T1Z9k6B_iRpstLq23SKddtN2fLlv-6YzUkF8NccKKOsLXe8elc4hbsZm-VD09BI9pVs-coNt2qGQxvSsxRNbe9RGtG8zbIo1iNBOf1DSmhEqBXSRfKTYM7-Rfz-YZjB99yXfIq4XmVAITME8bvORIrUvhqC1wXrvZS8S88QQcZ02ldLJsOYZw";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}
