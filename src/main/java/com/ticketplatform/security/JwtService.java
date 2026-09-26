package com.ticketplatform.security;

import com.ticketplatform.config.AppProperties;
import com.ticketplatform.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 手写 JWT（HS256）签发与校验，不引第三方依赖。
 * 结构即标准 JWT 三段：base64url(header).base64url(payload).base64url(HMAC-SHA256)
 *
 * 面试点：为什么无状态 token 还要在网关侧能吊销？——TTL 短（默认12h）+
 * 需要强吊销时换 secret 全局失效，或升级为服务端会话/黑名单。
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final byte[] secret;
    private final long ttlHours;

    public JwtService(AppProperties props) {
        this.secret = props.getSecurity().getSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlHours = props.getSecurity().getTokenTtlHours();
        if (props.getSecurity().getSecret().startsWith("change-me")) {
            log.warn("JWT secret 使用的是默认开发密钥，生产环境务必配置 app.security.secret！");
        }
    }

    public record Claims(String username, Role role, long expiresAtEpoch) {
    }

    public String issue(String username, Role role) {
        long exp = Instant.now().plusSeconds(ttlHours * 3600).getEpochSecond();
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"" + username + "\",\"role\":\"" + role.name()
                + "\",\"exp\":" + exp + "}");
        String signingInput = header + "." + payload;
        return signingInput + "." + b64(hmac(signingInput));
    }

    /** 校验签名与有效期，失败抛 JwtException。 */
    public Claims verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("token 格式非法");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = b64(hmac(signingInput));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            throw new JwtException("token 签名不匹配");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String sub = extract(payload, "\"sub\":\"", "\"");
        String role = extract(payload, "\"role\":\"", "\"");
        long exp = Long.parseLong(extract(payload, "\"exp\":", "}"));
        if (Instant.now().getEpochSecond() >= exp) {
            throw new JwtException("token 已过期，请重新登录");
        }
        return new Claims(sub, Role.valueOf(role), exp);
    }

    public static class JwtException extends RuntimeException {
        public JwtException(String message) {
            super(message);
        }
    }

    private String extract(String json, String startMark, String endMark) {
        int i = json.indexOf(startMark) + startMark.length();
        int j = json.indexOf(endMark, i);
        return json.substring(i, j);
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }
}
