package com.ticketplatform.domain;

/**
 * 回复 Agent 给出的处置建议。
 */
public enum SuggestedAction {
    /** 直接自动回复并关单 */
    AUTO_REPLY("自动回复"),
    /** 回复草稿需人工审核后发送 */
    NEED_HUMAN("人工审核"),
    /** 问题复杂/超权，转人工处理组 */
    ESCALATE("转人工");

    private final String label;

    SuggestedAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static SuggestedAction parse(String value, SuggestedAction fallback) {
        if (value == null) return fallback;
        try {
            return SuggestedAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
