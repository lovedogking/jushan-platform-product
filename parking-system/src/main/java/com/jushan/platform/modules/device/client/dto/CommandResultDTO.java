package com.jushan.platform.modules.device.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device Access v0.4 命令执行结果响应 data（开闸/关闸/校时等控制命令通用）。
 * <p>
 * 字段对齐 DA {@code CommandResultDTO}：{@code success}（Boolean）、{@code deviceCode}（Integer）、
 * {@code message}（String）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommandResultDTO {

    /** 是否成功 */
    private Boolean success;

    /** 设备回复的状态码（200 表示成功） */
    private Integer deviceCode;

    /** 结果描述 */
    private String message;

    // ==================== getter / setter ====================

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public Integer getDeviceCode() { return deviceCode; }
    public void setDeviceCode(Integer deviceCode) { this.deviceCode = deviceCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /**
     * 判断命令是否执行成功。
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success) && (deviceCode == null || deviceCode == 200);
    }

    @Override
    public String toString() {
        return "CommandResultDTO{" +
                "success=" + success +
                ", deviceCode=" + deviceCode +
                ", message='" + message + '\'' +
                '}';
    }
}
