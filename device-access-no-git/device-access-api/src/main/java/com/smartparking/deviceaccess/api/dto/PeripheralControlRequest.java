package com.smartparking.deviceaccess.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 外围设备控制请求 DTO。
 * <p>
 * 用于控制 Camera 的外围设备能力（如显示屏），不管理设备内容。
 * v0.3 引入，替代 DisplaySendRequest。
 *
 * @see com.smartparking.deviceaccess.common.enums.PeripheralType
 * @see com.smartparking.deviceaccess.common.enums.DisplayMode
 */
@Data
public class PeripheralControlRequest {

    /**
     * 控制动作。
     * <ul>
     *   <li>ENABLE  — 启用外围设备</li>
     *   <li>DISABLE — 关闭外围设备</li>
     *   <li>SET_MODE — 设置模式（需配合 mode 字段）</li>
     * </ul>
     */
    @NotNull
    private String action;

    /**
     * 显示模式（仅 action=SET_MODE 时需要）。
     * <ul>
     *   <li>TWO_LINE  — 2行模式（竖屏）</li>
     *   <li>FOUR_LINE — 4行模式（横屏）</li>
     * </ul>
     */
    private String mode;
}
