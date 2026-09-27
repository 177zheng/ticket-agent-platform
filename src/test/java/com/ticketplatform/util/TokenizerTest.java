package com.ticketplatform.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 中英混合分词：中文 bigram + 英文单词，供相似度检索使用。 */
class TokenizerTest {

    @Test
    void 中文按二元切分() {
        Set<String> tokens = Tokenizer.tokenize("无法连接");
        assertTrue(tokens.containsAll(Set.of("无法", "法连", "连接")));
    }

    @Test
    void 英文按单词切分并转小写() {
        Set<String> tokens = Tokenizer.tokenize("VPN Cannot Connect");
        assertTrue(tokens.containsAll(Set.of("vpn", "cannot", "connect")));
    }

    @Test
    void 中英混合与数字() {
        Set<String> tokens = Tokenizer.tokenize("ERP系统报500错误");
        assertTrue(tokens.contains("erp"));
        assertTrue(tokens.contains("500"));
        assertTrue(tokens.contains("系统"));
    }

    @Test
    void 空与null返回空集合() {
        assertTrue(Tokenizer.tokenize("").isEmpty());
        assertTrue(Tokenizer.tokenize(null).isEmpty());
        assertTrue(Tokenizer.tokenize("   ，。！  ").isEmpty());
    }
}
