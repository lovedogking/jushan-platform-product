package com.jushan.framework.mq;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 平台内部消息信封。
 * <p>
 * 所有通过 RabbitMQ 投递的平台消息必须包装为此类型。
 * 封装公共元数据（messageId、类型、租户上下文、时间戳），
 * 消费端按 envelope 统一解析，再做业务处理。
 * <p>
 * <strong>注意</strong>：
 * <ul>
 *   <li>{@code tenantId} 和 {@code parkingLotId} 由后端在发送时从可信上下文填入，
 *       禁止信任消息体中的前端传入值。</li>
 *   <li>{@code messageId} 用于幂等判定，消费端应在处理前检查。</li>
 * </ul>
 *
 * @param <T> 消息体类型
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageEnvelope<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一 ID（UUID v4） */
    private String messageId;

    /** 消息类型标识（如 "device.status.changed"、"record.entry.created"） */
    private String type;

    /** 租户 ID（可空；非空时消费端须校验数据范围） */
    private Long tenantId;

    /** 停车场 ID（可空；非空时消费端须校验数据范围） */
    private Long parkingLotId;

    /** 消息产生时间（UTC） */
    private Instant timestamp;

    /** 消息体载荷 */
    private T payload;

    // ==================== 工厂方法 ====================

    /**
     * 创建消息信封。
     *
     * @param type    消息类型标识
     * @param payload 消息体
     * @param <T>     载荷类型
     * @return 预填充 messageId 和 timestamp 的信封
     */
    public static <T> MessageEnvelope<T> of(String type, T payload) {
        MessageEnvelope<T> env = new MessageEnvelope<>();
        env.messageId = UUID.randomUUID().toString();
        env.type = type;
        env.timestamp = Instant.now();
        env.payload = payload;
        return env;
    }

    // ==================== Fluent 方法 ====================

    public MessageEnvelope<T> tenantId(Long tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public MessageEnvelope<T> parkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
        return this;
    }

    // ==================== Getter / Setter ====================

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getParkingLotId() {
        return parkingLotId;
    }

    public void setParkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "MessageEnvelope{messageId='" + messageId + "', type='" + type
                + "', tenantId=" + tenantId + ", parkingLotId=" + parkingLotId
                + ", timestamp=" + timestamp + "}";
    }
}
