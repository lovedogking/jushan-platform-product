package com.smartparking.deviceaccess.adapter.zhenshi.model;

import lombok.Getter;

/**
 * 臻识设备事件的统一基类。
 * <p>
 * v0.1 仅在 adapter 内部使用，不对外暴露。
 * 等引入 EventBus 后（v0.3+），事件模型可提取到 common 模块。
 */
@Getter
public abstract class ZhenshiEvent {

    /** 设备序列号 */
    private final String sn;

    /** 事件类型，对应 MQTT name 字段 */
    private final String eventType;

    /** 事件时间戳（Unix 秒） */
    private final Long timestamp;

    protected ZhenshiEvent(String sn, String eventType, Long timestamp) {
        this.sn = sn;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }
}
