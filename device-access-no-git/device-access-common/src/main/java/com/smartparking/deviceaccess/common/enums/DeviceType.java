package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 设备类型枚举。
 * <p>
 * 区分设备品类 — 不同品类的通信方式和业务行为完全不同。
 * v0.3 引入，支持摄像头和显示屏两种设备类型。
 */
@Getter
public enum DeviceType {

    /** 摄像头（车牌识别） */
    CAMERA("摄像头"),

    /** LED 显示屏（通过 RS485 代理通信） */
    DISPLAY("显示屏");

    private final String description;

    DeviceType(String description) {
        this.description = description;
    }
}
