package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 设备状态枚举。
 * <p>
 * 当前 v0.1 仅关注在线/离线，后续版本可能扩展。
 */
@Getter
public enum DeviceStatus {

    ONLINE("在线"),
    OFFLINE("离线"),
    UNKNOWN("未知");

    private final String description;

    DeviceStatus(String description) {
        this.description = description;
    }
}
