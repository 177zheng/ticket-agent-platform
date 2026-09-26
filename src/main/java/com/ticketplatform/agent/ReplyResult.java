package com.ticketplatform.agent;

import com.ticketplatform.domain.SuggestedAction;

/**
 * 回复 Agent 的产出：回复草稿 + 处置建议。
 */
public record ReplyResult(String replyDraft, SuggestedAction action, double confidence) {
}
