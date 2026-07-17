package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 设备通信协议枚举。
 * <p>
 * 记录在 t_device_product 表中，表示该产品型号的通信方式。
 * v0.3 引入。
 */
@Getter
public enum Protocol {

    /** MQTT 协议 — 设备直连 EMQX Broker */
    MQTT("MQTT"),

    /** RS485 协议 — 通过 RS485 串行通信，需代理设备 */
    RS485("RS485"),

    /** 无网络通信能力 */
    NONE("无");

    private final String description;

    Protocol(String description) {
        this.description = description;
    }
}
