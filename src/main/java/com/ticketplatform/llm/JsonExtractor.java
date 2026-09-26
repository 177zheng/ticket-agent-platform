package com.ticketplatform.llm;

/**
 * 从 LLM 输出中提取 JSON 对象。
 * 模型偶尔会包一层 ```json 代码块或加前后缀说明文字，这里做容错提取。
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmException("LLM 输出为空，无法解析 JSON");
        }
        String s = raw.trim();
        // 去掉 markdown 代码块围栏
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                s = s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("LLM 输出中未找到 JSON 对象: " + preview(s));
        }
        return s.substring(start, end + 1);
    }

    private static String preview(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
