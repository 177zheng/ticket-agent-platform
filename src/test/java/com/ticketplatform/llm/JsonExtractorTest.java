package com.ticketplatform.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** LLM 输出的 JSON 容错提取：代码块围栏、前后缀文本、无 JSON。 */
class JsonExtractorTest {

    @Test
    void 纯JSON_原样返回() {
        assertEquals("{\"a\":1}", JsonExtractor.extractJsonObject("{\"a\":1}"));
    }

    @Test
    void markdown代码块围栏_剥掉() {
        String raw = "```json\n{\"a\":1}\n```";
        assertEquals("{\"a\":1}", JsonExtractor.extractJsonObject(raw));
    }

    @Test
    void 前后有说明文字_提取中间JSON() {
        String raw = "好的，结果如下：{\"category\":\"网络\",\"priority\":1} 请查收";
        assertEquals("{\"category\":\"网络\",\"priority\":1}", JsonExtractor.extractJsonObject(raw));
    }

    @Test
    void 无JSON_抛异常() {
        assertThrows(LlmException.class, () -> JsonExtractor.extractJsonObject("抱歉我无法回答"));
    }

    @Test
    void 空输入_抛异常() {
        assertThrows(LlmException.class, () -> JsonExtractor.extractJsonObject(null));
        assertThrows(LlmException.class, () -> JsonExtractor.extractJsonObject("  "));
    }
}
