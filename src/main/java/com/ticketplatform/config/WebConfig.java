package com.ticketplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 把本地 data/avatars 目录映射为 /avatars/** 静态资源，
 * 头像 <img> 标签同源加载（不带 Authorization 头）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get("data", "avatars").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/avatars/**").addResourceLocations(location);
    }
}
