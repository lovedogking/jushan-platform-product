package com.jushan.system.client.dto;

/**
 * Device Access v0.4 语音播报请求。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/voice/control} 的请求体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class VoiceControlRequest {

    /** 操作：PLAY / STOP */
    private String action;

    /** 语音 ID */
    private Integer voiceId;

    /** 变量参数（如车牌号） */
    private String variable;

    public VoiceControlRequest() {}

    public VoiceControlRequest(String action, Integer voiceId, String variable) {
        this.action = action;
        this.voiceId = voiceId;
        this.variable = variable;
    }

    // ==================== getter / setter ====================

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getVoiceId() { return voiceId; }
    public void setVoiceId(Integer voiceId) { this.voiceId = voiceId; }

    public String getVariable() { return variable; }
    public void setVariable(String variable) { this.variable = variable; }
}
