package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 摄像头外围设备类型枚举。
 * <p>
 * Camera 可以通过 RS485/MQTT 控制多种外围设备。
 * Device Access 将其抽象为 Camera 的外围能力（Peripheral Capability），不作为独立设备管理。
 * <p>
 * v0.3 引入，当前仅实现 DISPLAY。
 *
 * @see DisplayMode
 */
@Getter
public enum PeripheralType {

    /** LED 显示屏 */
    DISPLAY("显示屏"),

    /** 道闸 */
    BARRIER("道闸"),

    /** 语音播报 */
    VOICE("语音播报"),

    /** 补光灯 */
    FILL_LIGHT("补光灯"),

    /** 报警器 */
    ALARM("报警器"),

    /** 信号灯 */
    TRAFFIC_LIGHT("信号灯"),

    /** 继电器 */
    RELAY("继电器");

    private final String description;

    PeripheralType(String description) {
        this.description = description;
    }
}
