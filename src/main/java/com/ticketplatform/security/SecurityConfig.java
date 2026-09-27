package com.ticketplatform.security;

import com.ticketplatform.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 安全总配置：无状态 JWT + 方法级权限（@PreAuthorize）。
 *
 * 权限矩阵：
 *   匿名    → 仅 登录/注册/静态页面/meta
 *   员工    → 提单、查看/检索自己的工单、仪表盘（仅自己的数据）
 *   管理员  → 全量工单、审核/驳回、失败重试、关闭工单、知识库管理
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String JSON_401 =
            "{\"error\":\"未登录或登录已过期，请先登录\"}";
    private static final String JSON_403 =
            "{\"error\":\"无权限执行该操作\"}";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
                                           AppProperties props) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(corsCfg -> corsCfg.configurationSource(apiCorsConfigurationSource(props)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/meta", "/error",
                                "/", "/index.html", "/assets/**", "/favicon.ico",
                                "/login", "/register", "/tickets", "/knowledge", "/profile",
                                "/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, JSON_401))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, JSON_403)))
                .headers(h -> h.frameOptions(fo -> fo.sameOrigin())) // h2-console 需要
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeJson(jakarta.servlet.http.HttpServletResponse res, int status, String body)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    /** 前端开发模式（vite 5173）跨域放行；不注册为 CorsConfigurationSource Bean，避免与 MVC 默认实现冲突 */
    private CorsConfigurationSource apiCorsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.getSecurity().getCorsOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
