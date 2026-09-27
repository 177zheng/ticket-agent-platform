package com.ticketplatform.security;

import com.ticketplatform.config.AppProperties;
import com.ticketplatform.domain.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 手写 JWT 的签发与校验：正常往返、篡改拒绝、过期拒绝。 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new AppProperties());

    @Test
    void 签发后校验_返回用户名与角色() {
        String token = jwtService.issue("zhangsan", Role.EMPLOYEE);
        JwtService.Claims claims = jwtService.verify(token);
        assertEquals("zhangsan", claims.username());
        assertEquals(Role.EMPLOYEE, claims.role());
        assertTrue(claims.expiresAtEpoch() > System.currentTimeMillis() / 1000);
    }

    @Test
    void 三段式结构_头部算法为HS256() {
        String token = jwtService.issue("admin", Role.ADMIN);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        String header = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
        assertTrue(header.contains("HS256"));
    }

    @Test
    void 篡改载荷_签名校验必须失败() {
        String t1 = jwtService.issue("zhangsan", Role.EMPLOYEE);
        String t2 = jwtService.issue("admin", Role.ADMIN);
        String[] p1 = t1.split("\\.");
        String[] p2 = t2.split("\\.");
        // 用 t2 的载荷 + t1 的签名：内容与签名不匹配
        String tampered = p1[0] + "." + p2[1] + "." + p1[2];
        assertThrows(JwtService.JwtException.class, () -> jwtService.verify(tampered));
    }

    @Test
    void 过期token_校验必须失败() {
        AppProperties props = new AppProperties();
        props.getSecurity().setTokenTtlHours(0); // 0 小时 = 立即过期
        JwtService instant = new JwtService(props);
        String token = instant.issue("u", Role.EMPLOYEE);
        assertThrows(JwtService.JwtException.class, () -> instant.verify(token));
    }

    @Test
    void 非token字符串_校验必须失败() {
        assertThrows(JwtService.JwtException.class, () -> jwtService.verify("not-a-jwt"));
        assertThrows(JwtService.JwtException.class, () -> jwtService.verify(""));
    }
}
