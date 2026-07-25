package com.jushan.platform.modules.device.client.dto;

/**
 * Device Access v0.4 语音播报请求。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/voice/control} 的请求体。
 * 采用文本匹配模式：发送文字内容，显示屏通过词组匹配查找内置语音库播放。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class VoiceControlRequest {

    /** 操作：PLAY / STOP */
    private String action;

    /** 播报文本（如"欢迎光临,请入场停车"），action=PLAY 时必填 */
    private String voiceText;

    /** 操作选项：0x00=添加到队列不播放, 0x01=添加到队列并播放（默认）, 0x02=清除队列后播放 */
    private Integer opt;

    public VoiceControlRequest() {}

    public VoiceControlRequest(String action, String voiceText, Integer opt) {
        this.action = action;
        this.voiceText = voiceText;
        this.opt = opt;
    }

    // ==================== getter / setter ====================

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getVoiceText() { return voiceText; }
    public void setVoiceText(String voiceText) { this.voiceText = voiceText; }

    public Integer getOpt() { return opt; }
    public void setOpt(Integer opt) { this.opt = opt; }
}
