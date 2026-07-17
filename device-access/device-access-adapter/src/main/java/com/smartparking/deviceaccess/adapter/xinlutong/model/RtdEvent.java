package com.smartparking.deviceaccess.adapter.xinlutong.model;

import lombok.Builder;
import lombok.Value;

/**
 * 信路通设备心跳事件。
 * <p>
 * 设备定期发送 Rtd 消息维持在线状态。
 */
@Value
@Builder
public class RtdEvent {

    /** 设备序列号（从 Topic 中提取） */
    String sn;

    /** 消息时间 */
    String time;

    /** 设备携带的数据（data 字段反序列化后的内容，可能为 null） */
    String data;
}
