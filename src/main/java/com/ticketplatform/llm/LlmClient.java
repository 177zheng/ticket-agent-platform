package com.ticketplatform.llm;

/**
 * LLM 调用统一入口。
 * 当前实现为 OpenAI 兼容 HTTP 客户端；后续若切换 Spring AI，
 * 只需另写一个实现类替换本接口，Agent 层代码不动。
 */
public interface LlmClient {

    /**
     * 以 system + user 两条消息调用对话补全接口，返回模型输出文本。
     * 内部已包含重试（指数退避），重试耗尽抛出 LlmException。
     */
    String complete(String systemPrompt, String userPrompt);
}
