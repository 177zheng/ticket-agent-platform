package com.ticketplatform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由回退：前端用 history 路由，刷新 /login、/tickets 等路径时
 * Spring 没有对应资源，统一转发到 index.html 交给前端路由处理。
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/login", "/register", "/tickets", "/knowledge"})
    public String forward() {
        return "forward:/index.html";
    }
}
