package com.ticketplatform.util;

import java.util.HashSet;
import java.util.Set;

/**
 * 轻量分词器：中文字符两两组成 bigram，英文/数字按单词切分。
 * 用于知识库与历史工单的相似度打分（MVP 阶段不引分词依赖，效果够用）。
 */
public final class Tokenizer {

    private Tokenizer() {
    }

    public static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String s = text.toLowerCase();
        StringBuilder latin = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) {
                // 中文 bigram
                if (i + 1 < s.length() && isCjk(s.charAt(i + 1))) {
                    tokens.add(s.substring(i, i + 2));
                } else {
                    tokens.add(String.valueOf(c));
                }
            } else if (Character.isLetterOrDigit(c)) {
                latin.append(c);
            } else if (latin.length() > 0) {
                tokens.add(latin.toString());
                latin.setLength(0);
            }
        }
        if (latin.length() > 0) {
            tokens.add(latin.toString());
        }
        return tokens;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN;
    }
}
