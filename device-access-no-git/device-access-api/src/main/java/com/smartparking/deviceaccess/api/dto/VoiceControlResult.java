package com.smartparking.deviceaccess.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 语音控制结果 DTO。
 * <p>
 * v0.5 引入。
 */
@Data
@Builder
public class VoiceControlResult {

    /** 是否成功 */
    private boolean success;

    /** 动作类型（PLAY/STOP/QUEUE_CLEAR） */
    private String action;

    /** 语音文本（action=PLAY 时返回） */
    private String voiceText;

    /** 结果消息 */
    private String message;

    /** 错误信息（失败时） */
    private String errorMessage;
}
