package com.ticketplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台配置项，见 application.yml 的 app.* 前缀。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Llm llm = new Llm();
    private final Pipeline pipeline = new Pipeline();
    private final Knowledge knowledge = new Knowledge();
    private final Security security = new Security();

    public Llm getLlm() { return llm; }
    public Pipeline getPipeline() { return pipeline; }
    public Knowledge getKnowledge() { return knowledge; }
    public Security getSecurity() { return security; }

    public static class Llm {
        /** mock | openai */
        private String mode = "mock";
        private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
        private String apiKey = "";
        private String model = "glm-4.6";
        private double temperature = 0.2;
        private int maxTokens = 1024;
        private int timeoutSeconds = 60;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Pipeline {
        private int retryAttempts = 3;
        private long retryBackoffMs = 800;

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    }

    public static class Knowledge {
        private int chunkSize = 400;
        private int chunkOverlap = 80;
        private int topK = 3;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    public static class Security {
        /** HMAC 签名密钥；生产环境必须改成随机长字符串 */
        private String secret = "change-me-in-production-please-use-a-long-random-secret-key";
        private long tokenTtlHours = 12;
        /** 前端 vite 开发服务器的跨域白名单 */
        private java.util.List<String> corsOrigins =
                java.util.List.of("http://localhost:5173", "http://127.0.0.1:5173");

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getTokenTtlHours() { return tokenTtlHours; }
        public void setTokenTtlHours(long tokenTtlHours) { this.tokenTtlHours = tokenTtlHours; }
        public java.util.List<String> getCorsOrigins() { return corsOrigins; }
        public void setCorsOrigins(java.util.List<String> corsOrigins) { this.corsOrigins = corsOrigins; }
    }
}
