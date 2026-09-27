package com.ticketplatform.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 请求体 DTO。
 */
public final class Requests {

    private Requests() {
    }

    /** 提单人信息取自登录态（后端写入），前端不再提交 */
    public record CreateTicketRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最长 200 字")
            String title,
            @NotBlank(message = "问题描述不能为空")
            String description) {
    }

    public record ReviewRequest(
            @NotNull(message = "approve 不能为空")
            Boolean approve,
            @Size(max = 500, message = "审核意见最长 500 字")
            String comment) {
    }

    public record CloseRequest(
            @NotBlank(message = "处理方案不能为空（将记录为工单的最终解决方案）") @Size(max = 1000)
            String comment) {
    }

    /** 提单人对处理结果的反馈：不满意则重开转人工 */
    public record FeedbackRequest(
            @NotNull(message = "satisfied 不能为空")
            Boolean satisfied,
            @Size(max = 500, message = "反馈说明最长 500 字")
            String reason) {
    }

    public record ManualRequest(
            @NotBlank(message = "手册标题不能为空") @Size(max = 200)
            String title,
            @NotBlank(message = "手册内容不能为空")
            String content) {
    }
}
