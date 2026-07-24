package com.jushan.platform.modules.device.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device Access v0.4 语音播报响应 data。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/voice/control} 的 {@code data} 字段。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoiceResultDTO {

    /** 是否成功 */
    private Boolean success;

    /** 结果描述 */
    private String message;

    // ==================== getter / setter ====================

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }

    @Override
    public String toString() {
        return "VoiceResultDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                '}';
    }
}
