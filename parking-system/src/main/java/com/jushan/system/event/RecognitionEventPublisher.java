package com.jushan.system.event;

import com.jushan.framework.mq.MessageEnvelope;
import com.jushan.framework.mq.MqConstants;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.mapper.RecognitionEventLogMapper;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 识别事件发布器（T28）。
 * <p>
 * 负责将平台内部标准识别事件：
 * <ol>
 *   <li>持久化到 {@code recognition_event_log} 表（审计追溯）</li>
 *   <li>包装为 {@link MessageEnvelope} 并发布到平台内部 RabbitMQ Exchange</li>
 * </ol>
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>事件中的 {@code tenantId} 和 {@code parkingLotId} 必须已由调用方从可信记录推导，
 *       本方法不信任 {@code payload} 中可能残留的外部传入值</li>
 *   <li>发布失败不影响已持久化的日志记录（异步解耦）</li>
 * </ul>
 * <p>
 * <strong>RabbitMQ 降级</strong>：当 RabbitMQ 不可用时，使用 ObjectProvider 注入，
 * 日志仍持久化到数据库，MQ 发布静默跳过。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class RecognitionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RecognitionEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RecognitionEventLogMapper eventLogMapper;
    private final BoothWebSocketPublisher boothWebSocketPublisher;

    public RecognitionEventPublisher(ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                                      RecognitionEventLogMapper eventLogMapper,
                                      ObjectProvider<BoothWebSocketPublisher> boothWebSocketPublisherProvider) {
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.eventLogMapper = eventLogMapper;
        this.boothWebSocketPublisher = boothWebSocketPublisherProvider.getIfAvailable();
    }

    /**
     * 发布识别事件。
     * <p>
     * 先持久化到数据库，再发布到 RabbitMQ。
     * RabbitMQ 发布失败只记录 warning 日志，不抛异常（事件已在数据库保留）。
     *
     * @param payload 识别事件载荷（tenantId/parkingLotId 必须已由调用方推导并设置）
     * @return 持久化后的日志记录
     */
    public RecognitionEventLog publish(RecognitionEventPayload payload) {
        // 1. 持久化事件日志
        RecognitionEventLog logEntry = toEntity(payload);
        eventLogMapper.insert(logEntry);
        payload.setLogId(logEntry.getId());
        log.info("识别事件已持久化: eventId={} plate={} direction={} source={} logId={}",
                payload.getEventId(), payload.getPlateNumber(),
                payload.getDirection(), payload.getSource(), logEntry.getId());

        // 2. 发布到 RabbitMQ（降级：MQ 不可用或失败不影响已持久化日志）
        if (rabbitTemplate != null) {
            try {
                MessageEnvelope<RecognitionEventPayload> envelope = MessageEnvelope
                        .of(MqConstants.TYPE_RECOGNITION_EVENT, payload)
                        .tenantId(payload.getTenantId())
                        .parkingLotId(payload.getParkingLotId());

                rabbitTemplate.convertAndSend(
                        MqConstants.EXCHANGE_INTERNAL,
                        MqConstants.ROUTING_KEY_RECOGNITION_EVENT,
                        envelope);

                log.info("识别事件已发布到 MQ: messageId={} eventId={}",
                        envelope.getMessageId(), payload.getEventId());
            } catch (Exception e) {
                log.warn("识别事件发布到 RabbitMQ 失败（已持久化到数据库，可补偿重发）: eventId={} error={}",
                        payload.getEventId(), e.getMessage());
            }
        } else {
            log.info("RabbitMQ 不可用，识别事件仅持久化到数据库: eventId={}", payload.getEventId());
        }

        // 3. 推送到岗亭 WebSocket（P005：实时识别事件，失败不影响已持久化日志）
        if (boothWebSocketPublisher != null && logEntry.getParkingLotId() != null) {
            boothWebSocketPublisher.sendRecognitionEvent(logEntry.getParkingLotId(), logEntry);
        }

        return logEntry;
    }

    /**
     * 将载荷转换为实体。
     */
    private RecognitionEventLog toEntity(RecognitionEventPayload payload) {
        RecognitionEventLog entity = new RecognitionEventLog();
        entity.setEventId(payload.getEventId());
        entity.setTenantId(payload.getTenantId());
        entity.setParkingLotId(payload.getParkingLotId());
        entity.setLaneId(payload.getLaneId());
        entity.setDeviceId(payload.getDeviceId());
        entity.setPlateNumber(payload.getPlateNumber());
        entity.setDirection(payload.getDirection());
        entity.setEventTime(payload.getEventTime() != null ? payload.getEventTime() : LocalDateTime.now());
        entity.setConfidence(payload.getConfidence());
        entity.setImagePath(payload.getImagePath());
        entity.setPlateImagePath(payload.getPlateImagePath());
        entity.setSource(payload.getSource());
        entity.setRawData(payload.getRawData());
        entity.setStatus("RECEIVED");
        entity.setVendorEventId(payload.getVendorEventId());
        return entity;
    }
}
