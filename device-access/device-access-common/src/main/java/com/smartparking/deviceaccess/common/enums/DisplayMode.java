package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * LED 显示屏显示模式枚举。
 * <p>
 * 控制显示屏的分区布局。不同模式对应不同的物理安装方向：
 * <ul>
 *   <li>TWO_LINE — 竖屏安装，每行 32px 高，共 2 行</li>
 *   <li>FOUR_LINE — 横屏安装，每行 16px 高，共 4 行</li>
 * </ul>
 * <p>
 * v0.3 引入，属于 Camera 的 DISPLAY 外围能力控制。
 *
 * @see PeripheralType#DISPLAY
 */
@Getter
public enum DisplayMode {

    /** 2行模式（竖屏，每行 32px） */
    TWO_LINE("2行"),

    /** 4行模式（横屏，每行 16px） */
    FOUR_LINE("4行");

    private final String description;

    DisplayMode(String description) {
        this.description = description;
    }
}
