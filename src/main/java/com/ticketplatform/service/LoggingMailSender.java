package com.ticketplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 邮件 Mock 实现：只打日志，便于演示全流程。
 * 生产接入：实现 MailSender，内部用 spring-boot-starter-mail 的 JavaMailSender。
 */
@Component
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[邮件Mock] 已发送 → 收件人: {} | 主题: {} | 正文:\n{}", to, subject, body);
    }
}
