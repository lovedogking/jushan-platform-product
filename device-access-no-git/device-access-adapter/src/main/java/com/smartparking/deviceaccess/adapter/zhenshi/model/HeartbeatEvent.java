package com.smartparking.deviceaccess.adapter.zhenshi.model;

import lombok.Getter;

/**
 * 设备心跳事件。
 * <p>
 * 臻识设备每 5 秒上报一次心跳（Web 端可配置）。
 * Topic: $/device/{sn}/message/up/keep_alive
 * <p>
 * 文档依据：《自定义MQTT协议文档 v1.1.14》第8章 心跳
 */
@Getter
public class HeartbeatEvent extends ZhenshiEvent {

    /** 设备上报的心跳时间戳（Unix 秒） */
    private final Long deviceTimestamp;

    public HeartbeatEvent(String sn, Long eventTimestamp, Long deviceTimestamp) {
        super(sn, "keep_alive", eventTimestamp);
        this.deviceTimestamp = deviceTimestamp;
    }
}
