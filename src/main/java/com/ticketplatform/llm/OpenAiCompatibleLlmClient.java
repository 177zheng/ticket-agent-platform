package com.ticketplatform.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketplatform.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /chat/completions 客户端。
 * 适用于智谱 GLM、DeepSeek、通义 Qwen、OpenAI、本地 vLLM/Ollama 等一切兼容接口。
 *
 * app.llm.mode=openai 时生效；含指数退避重试。
 */
@Component
@ConditionalOnProperty(name = "app.llm.mode", havingValue = "openai")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final AppProperties props;
    private RestClient restClient;

    public OpenAiCompatibleLlmClient(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        AppProperties.Llm llm = props.getLlm();
        if (llm.getApiKey() == null || llm.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.llm.mode=openai 但未配置 API Key：请设置环境变量 LLM_API_KEY 或配置 app.llm.api-key");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(llm.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(llm.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llm.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        AppProperties.Llm llm = props.getLlm();
        ChatRequest body = new ChatRequest(
                llm.getModel(),
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                llm.getTemperature(),
                llm.getMaxTokens());

        int attempts = Math.max(1, props.getPipeline().getRetryAttempts());
        long backoff = Math.max(0, props.getPipeline().getRetryBackoffMs());

        LlmException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                ChatResponse resp = restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        .body(ChatResponse.class);
                if (resp == null || resp.choices() == null || resp.choices().isEmpty()
                        || resp.choices().get(0).message() == null
                        || resp.choices().get(0).message().content() == null) {
                    throw new LlmException("LLM 返回内容为空");
                }
                return resp.choices().get(0).message().content();
            } catch (RestClientResponseException | ResourceAccessException | LlmException e) {
                last = wrap(e);
                log.warn("LLM 调用失败（第 {}/{} 次）：{}", i, attempts, last.getMessage());
                if (i < attempts && !isRetryable(e)) {
                    throw last;
                }
                if (i < attempts) {
                    sleep(backoff * (1L << (i - 1)));
                }
            }
        }
        throw last != null ? last : new LlmException("LLM 调用失败");
    }

    private static boolean isRetryable(Exception e) {
        // 4xx 客户端错误（鉴权/参数问题）重试无意义；超时、网络、5xx 可重试
        if (e instanceof RestClientResponseException re) {
            return re.getStatusCode().is5xxServerError();
        }
        return true; // 网络异常 / 超时 / 空返回
    }

    private static LlmException wrap(Exception e) {
        if (e instanceof RestClientResponseException re) {
            String snippet = re.getResponseBodyAsString();
            if (snippet != null && snippet.length() > 300) snippet = snippet.substring(0, 300);
            return new LlmException("LLM 接口返回 " + re.getStatusCode().value() + ": " + snippet, re);
        }
        Throwable root = e instanceof ResourceAccessException rae && rae.getCause() != null ? rae.getCause() : e;
        String hint = root instanceof SocketTimeoutException ? "（读取超时，可调大 app.llm.timeout-seconds）" : "";
        return new LlmException("LLM 网络调用失败" + hint + ": " + root.getMessage(), e);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 调用被中断", ie);
        }
    }

    // ---- OpenAI 兼容协议的请求/响应结构 ----

    record ChatRequest(String model, List<Message> messages,
                       @JsonProperty("temperature") double temperature,
                       @JsonProperty("max_tokens") int maxTokens) {
    }

    record Message(String role, String content) {
    }

    record ChatResponse(List<Choice> choices, Map<String, Object> usage) {
    }

    record Choice(int index, ChatMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatMessage(String role, String content) {
    }
}
