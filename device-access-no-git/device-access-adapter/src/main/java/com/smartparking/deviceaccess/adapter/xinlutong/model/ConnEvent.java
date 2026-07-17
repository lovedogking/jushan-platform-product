package com.smartparking.deviceaccess.adapter.xinlutong.model;

import lombok.Builder;
import lombok.Value;

/**
 * 信路通设备连接事件。
 * <p>
 * 设备上电或重连后发送 Conn 消息，携带设备信息。
 */
@Value
@Builder
public class ConnEvent {

    /** 设备序列号（从 Topic 中提取） */
    String sn;

    /** 消息时间 */
    String time;

    /** 设备信息（data 字段反序列化后的 devInfo） */
    String devInfo;
}
