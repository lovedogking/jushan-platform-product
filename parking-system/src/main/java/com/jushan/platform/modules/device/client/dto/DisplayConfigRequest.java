package com.jushan.platform.modules.device.client.dto;

/**
 * Device Access v0.4 显示屏配置请求。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/display/config} 的请求体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class DisplayConfigRequest {

    /** 配置类型：VOLUME / BRIGHTNESS / DIRECTION / TIME_SYNC */
    private String configType;

    /** 整数型配置值 */
    private Integer intValue;

    /** 字符串型配置值 */
    private String stringValue;

    public DisplayConfigRequest() {}

    public DisplayConfigRequest(String configType, Integer intValue, String stringValue) {
        this.configType = configType;
        this.intValue = intValue;
        this.stringValue = stringValue;
    }

    // ==================== getter / setter ====================

    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }

    public Integer getIntValue() { return intValue; }
    public void setIntValue(Integer intValue) { this.intValue = intValue; }

    public String getStringValue() { return stringValue; }
    public void setStringValue(String stringValue) { this.stringValue = stringValue; }
}
