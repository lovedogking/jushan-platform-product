package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 设备安装方向枚举。
 * <p>
 * 仅对摄像头类型设备有意义（显示屏不需要方向概念）。
 * v0.3 引入。
 */
@Getter
public enum Direction {

    /** 入口方向 */
    ENTRANCE("入口"),

    /** 出口方向 */
    EXIT("出口"),

    /** 双向（入口+出口共用） */
    BIDIRECTIONAL("双向");

    private final String description;

    Direction(String description) {
        this.description = description;
    }
}
