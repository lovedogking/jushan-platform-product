package com.smartparking.deviceaccess.adapter.xinlutong.model;

import lombok.Builder;
import lombok.Value;

/**
 * 信路通命令回执事件。
 * <p>
 * 设备对下行命令（Open / Close / Config / Rtd）的回复。
 */
@Value
@Builder
public class ResultEvent {

    /** 设备序列号（从 Topic 中提取） */
    String sn;

    /** 消息时间 */
    String time;

    /** 关联的命令 msgId */
    String msgId;

    /** 执行结果码（0 = 成功） */
    Integer result;
}
