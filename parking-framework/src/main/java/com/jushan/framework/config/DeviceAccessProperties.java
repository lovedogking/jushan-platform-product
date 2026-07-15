package com.jushan.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Device Access HTTP 客户端配置属性（T23）。
 * <p>
 * 配置前缀：{@code jushan.device-access}。
 * Base URL 来自后端可信配置，不允许前端传入。
 * <p>
 * <strong>超时说明</strong>：
 * <ul>
 *   <li>连接超时默认 3 秒（内网环境足够）</li>
 *   <li>读取超时默认 12 秒（覆盖 Device Access 内部约 10 秒 MQTT 等待）</li>
 *   <li>不得配置任何形式的自动重试</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "jushan.device-access")
public class DeviceAccessProperties {

    /** Device Access 服务 Base URL，如 {@code http://192.168.1.100:8081} */
    private String baseUrl = "http://localhost:8081";

    /** 连接超时（毫秒），默认 3 秒 */
    private int connectTimeout = 3000;

    /** 读取超时（毫秒），默认 12 秒（需覆盖 DA 内部约 10 秒 MQTT 等待） */
    private int readTimeout = 12000;

    /** Device Access API Key 认证 */
    private String apiKey = "";

    // ==================== getter / setter ====================

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
