package com.smartparking.deviceaccess.api.dto;

import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实时显示文字请求 DTO。
 * <p>
 * 向设备下挂 LED 控制卡临时区发送文本，立即覆盖当前显示。
 * 内容掉电丢失，不持久化。
 * <p>
 * v0.3 引入。
 * v0.5 增强：支持字体、颜色、语音同步。
 */
@Data
public class DisplayTextRequest {

    /**
     * 文本内容。
     * HORIZONTAL 模式下用 {@code \n} 分隔多行。
     */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 文本布局方向 */
    @NotNull(message = "direction 不能为空")
    private DisplayDirection direction;

    /**
     * 字体类型（可选，默认宋体16）。
     * @see OlmM1dProtocol.FontType
     */
    private OlmM1dProtocol.FontType font;

    /**
     * 文字颜色 RGBA（可选，默认白色）。
     * 4 字节数组：[R, G, B, A]，例如 {0xFF, 0x00, 0x00, 0x00} = 红色
     * <p>
     * 支持每行独立颜色：传入 List，每个元素对应一行的颜色。
     * 如果 List 大小小于行数，剩余行使用最后一个颜色或默认白色。
     */
    private java.util.List<int[]> color;

    /** 颜色名称（简化版），如 "RED"、"GREEN"、"YELLOW"、"WHITE"。优先于 color 字段 */
    private String colorName;

    /** 是否同步播放语音（可选，默认 false） */
    private boolean playVoice;

    /**
     * 同步语音ID（可选，playVoice=true 时生效）。
     * 0~341，对应协议内置语音表。
     */
    private Integer voiceId;

    /**
     * 语音变量替换文本（可选）。
     * 部分语音支持变量替换（如金额、车牌号）。
     */
    private String voiceVariable;
}
