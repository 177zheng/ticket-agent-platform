package com.ticketplatform.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 请求体 DTO。
 */
public final class Requests {

    private Requests() {
    }

    public record CreateTicketRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最长 200 字")
            String title,
            @NotBlank(message = "问题描述不能为空")
            String description,
            @NotBlank(message = "提单人姓名不能为空") @Size(max = 100)
            String requesterName,
            @NotBlank(message = "联系邮箱不能为空") @Email(message = "邮箱格式不正确")
            String requesterEmail) {
    }

    public record ReviewRequest(
            @NotNull(message = "approve 不能为空")
            Boolean approve,
            @Size(max = 500, message = "审核意见最长 500 字")
            String comment) {
    }

    public record CloseRequest(
            @Size(max = 500, message = "处理说明最长 500 字")
            String comment) {
    }

    public record ManualRequest(
            @NotBlank(message = "手册标题不能为空") @Size(max = 200)
            String title,
            @NotBlank(message = "手册内容不能为空")
            String content) {
    }
}
