package com.ticketplatform.service;

/**
 * 邮件发送出口。MVP 用日志实现；接入真实 SMTP 时
 * 新增一个 @Component 实现并 @Primary 覆盖，或在此处直接改用 JavaMailSender。
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
