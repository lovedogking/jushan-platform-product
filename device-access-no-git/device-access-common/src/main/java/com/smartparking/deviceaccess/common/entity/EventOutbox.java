package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件发件箱 —— 用于 Webhook 推送持久化与定时重试。
 * <p>
 * 当内存重试耗尽后，事件被写入此表；
 * {@link com.smartparking.deviceaccess.event.EventRetryService} 定时扫描 PENDING 记录继续重试。
 *
 * @since v0.4
 */
@Data
@TableName("t_event_outbox")
public class EventOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一标识 */
    private String eventId;

    /** 事件类型，如 PLATE_RECOGNIZED */
    private String eventType;

    /** 事件 JSON 报文 */
    private String payload;

    /** 推送状态：PENDING / SENT / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后重试时间 */
    private LocalDateTime lastRetryAt;
}
