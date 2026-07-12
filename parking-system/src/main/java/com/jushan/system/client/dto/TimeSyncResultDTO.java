package com.jushan.system.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device Access v0.2 校时操作响应 data（对齐 DA CommandResultDTO 字段）。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/time/sync} 的 {@code data} 字段。
 * DA v0.2 中校时无 commandId 幂等，每次调用都会向设备发送一次校时命令。
 * <p>
 * <strong>FIX-09</strong>：字段已对齐 DA v0.2 真实 {@code CommandResultDTO}：
 * {@code success}（Boolean）、{@code deviceCode}（Integer）、{@code message}（String）。
 * 原错误映射 {@code deviceSn/status} 已移除。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSyncResultDTO {

    /** 是否成功（来自 DA 真实响应字段） */
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
     * 判断校时是否成功。
     * FIX-09：原 getStatus() 调用者迁移至此方法。
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success) && (deviceCode == null || deviceCode == 200);
    }

    @Override
    public String toString() {
        return "TimeSyncResultDTO{" +
                "success=" + success +
                ", deviceCode=" + deviceCode +
                ", message='" + message + '\'' +
                '}';
    }
}
