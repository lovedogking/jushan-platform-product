package com.smartparking.deviceaccess.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 语音控制请求 DTO。
 * <p>
 * 用于控制显示屏语音播报功能。
 * 语音与显示内容分离，各自独立生命周期。
 * <p>
 * v0.5 引入。
 */
@Data
public class VoiceControlRequest {

    /**
     * 动作类型。
     * <p>
     * 支持值：
     * <ul>
     *   <li>PLAY — 播放指定语音</li>
     *   <li>STOP — 立即停止当前语音</li>
     *   <li>QUEUE_CLEAR — 清空语音队列（预留）</li>
     * </ul>
     */
    @NotBlank(message = "action 不能为空")
    private String action;

    /**
     * 语音文本内容（GBK编码）。
     * <p>
     * action=PLAY 时必填。
     * 显示屏通过词组匹配查找内置语音库播放。
     * 例如"欢迎光临,请入场停车"。
     * 多个词组用逗号或句号分隔。
     */
    private String voiceText;

    /**
     * 操作选项。
     * <p>
     * 0x00 = 添加到队列不立即播放
     * 0x01 = 添加到队列并立即播放（默认）
     * 0x02 = 清除队列后播放
     */
    private Integer opt;

    /**
     * 关联显示文字（可选）。
     * <p>
     * 如需语音与文字同步显示，传入此字段。
     * 实际显示由 /display/text 接口处理。
     */
    private String displayText;
}
