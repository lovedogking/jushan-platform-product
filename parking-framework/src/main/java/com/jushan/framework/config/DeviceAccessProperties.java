package com.jushan.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Device Access HTTP 客户端配置属性（T23, 任务包 7-1 增强）。
 * <p>
 * 配置前缀：{@code jushan.device-access}。
 * Base URL 来自后端可信配置，不允许前端传入。
 * <p>
 * <strong>v7-1 新增</strong>：
 * <ul>
 *   <li>{@link Retry} — 写命令重试策略（openGate/closeGate）</li>
 *   <li>{@link Mock} — Mock 适配器开关（仅 local/test）</li>
 *   <li>{@code whitelistSyncApiKey} — 白名单同步 API 鉴权 Key</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "jushan.device-access")
public class DeviceAccessProperties {

    /** Device Access 服务 Base URL，如 {@code http://localhost:8082} */
    private String baseUrl = "http://localhost:8082";

    /** 连接超时（毫秒），默认 3 秒 */
    private int connectTimeout = 3000;

    /** 读取超时（毫秒），默认 12 秒（需覆盖 DA 内部约 10 秒 MQTT 等待） */
    private int readTimeout = 12000;

    /** Device Access API Key 认证 */
    private String apiKey = "";

    /** 重试策略配置 */
    private Retry retry = new Retry();

    /** Mock 适配器配置 */
    private Mock mock = new Mock();

    /** 白名单同步 API Key（Adapter → Platform 内部通信鉴权） */
    private String whitelistSyncApiKey = "";

    // ==================== getter / setter ====================

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }

    public Mock getMock() { return mock; }
    public void setMock(Mock mock) { this.mock = mock; }

    public String getWhitelistSyncApiKey() { return whitelistSyncApiKey; }
    public void setWhitelistSyncApiKey(String whitelistSyncApiKey) { this.whitelistSyncApiKey = whitelistSyncApiKey; }

    // ==================== 嵌套配置类 ====================

    /**
     * 写命令重试策略（仅 openGate / closeGate）。
     */
    public static class Retry {
        /** 最大尝试次数（含首次），默认 3 */
        private int maxAttempts = 3;

        /** 重试间隔（毫秒），按重试次数索引：第 1 次重试间隔 intervals[0]，第 2 次 intervals[1]… */
        private List<Integer> intervals = List.of(1000, 5000, 30000);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public List<Integer> getIntervals() { return intervals; }
        public void setIntervals(List<Integer> intervals) { this.intervals = intervals; }
    }

    /**
     * Mock 适配器配置。
     */
    public static class Mock {
        /** 是否启用 Mock 适配器（local/test 为 true，生产 false） */
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
