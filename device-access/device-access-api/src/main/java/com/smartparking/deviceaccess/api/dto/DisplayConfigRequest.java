package com.smartparking.deviceaccess.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 显示屏配置请求 DTO。
 * <p>
 * 用于配置显示屏硬件参数（音量、亮度、方向、时间同步等）。
 * 配置与显示内容分离，各自独立生命周期。
 * <p>
 * v0.5 引入。
 */
@Data
public class DisplayConfigRequest {

    /**
     * 配置项类型。
     * <p>
     * 支持值：
     * <ul>
     *   <li>VOLUME — 音量控制（0~100）</li>
     *   <li>BRIGHTNESS — 亮度控制（10~100）</li>
     *   <li>DIRECTION — 显示方向（0=正常，1=旋转180度）</li>
     *   <li>TIME_SYNC — 时间同步（使用服务器当前时间）</li>
     *   <li>FONT_DEFAULT — 默认字体（0~7）</li>
     * </ul>
     */
    @NotBlank(message = "configType 不能为空")
    private String configType;

    /**
     * 整数值配置参数。
     * <p>
     * 根据 configType 含义不同：
     * - VOLUME: 0~100
     * - BRIGHTNESS: 10~100
     * - DIRECTION: 0 或 1
     * - TIME_SYNC: 忽略
     * - FONT_DEFAULT: 0~7（对应 FontType 枚举值）
     */
    private Integer intValue;

    /**
     * 字符串值配置参数（预留扩展）。
     */
    private String strValue;
}
