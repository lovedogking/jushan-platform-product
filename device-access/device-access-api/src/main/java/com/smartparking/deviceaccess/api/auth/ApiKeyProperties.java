package com.smartparking.deviceaccess.api.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * API Key 认证配置。
 *
 * <p>支持多密钥配置，适用于多租户独立服务场景。
 * 未配置任何密钥时，API Key 过滤器放行所有请求（便于开发/测试）。
 *
 * @since v0.4
 */
@Data
@ConfigurationProperties(prefix = "device-access.auth")
public class ApiKeyProperties {

    /** 允许的 API Key 列表 */
    private List<String> keys = new ArrayList<>();

    /**
     * 是否启用 API Key 认证。
     */
    public boolean isEnabled() {
        return keys != null && !keys.isEmpty();
    }

    /**
     * 校验 API Key 是否有效。
     */
    public boolean isValid(String key) {
        return key != null && keys.contains(key);
    }
}
