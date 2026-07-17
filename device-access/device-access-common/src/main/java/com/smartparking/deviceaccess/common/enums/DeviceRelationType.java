package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 设备关系类型枚举。
 * <p>
 * 定义设备间业务关联的类型。关系由 t_device_relation 表管理，
 * 支持启用/停用，与设备本体属性解耦。
 * v0.3 引入。
 */
@Getter
public enum DeviceRelationType {

    /** 辅助摄像头关联 — 主摄像头(source) → 辅助摄像头(target) */
    AUX_CAMERA("辅助摄像头"),

    /** RS485 显示屏代理 — 摄像头(source) → 显示屏(target)，摄像头通过 RS485 控制显示屏 */
    RS485_DISPLAY("RS485显示屏");

    private final String description;

    DeviceRelationType(String description) {
        this.description = description;
    }
}
