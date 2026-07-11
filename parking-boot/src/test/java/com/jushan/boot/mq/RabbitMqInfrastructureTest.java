package com.jushan.boot.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.framework.mq.MessageEnvelope;
import com.jushan.framework.mq.MessageIdempotency;
import com.jushan.framework.mq.MqConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitMQ 平台内部基础设施集成测试。
 */
@DisplayName("RabbitMQ 基础设施集成测试")
class RabbitMqInfrastructureTest extends RabbitMqTestBase {

    static final String TEST_QUEUE = "jushan.test.infrastructure.echo";
    static final String TEST_ROUTING_KEY = "jushan.test.infrastructure.echo";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private MessageIdempotency messageIdempotency;

    // 使用 Spring Boot 自动配置的 ObjectMapper，已包含 JavaTimeModule
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        var queue = QueueBuilder.durable(TEST_QUEUE)
                .withArguments(com.jushan.framework.mq.RabbitMqConfig.deadLetterArgs())
                .build();
        rabbitAdmin.declareQueue(queue);
        var binding = BindingBuilder.bind(queue)
                .to(new TopicExchange(MqConstants.EXCHANGE_INTERNAL))
                .with(TEST_ROUTING_KEY);
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(TEST_QUEUE, true);
    }

    // ==================== ① 消息投递与消费 ====================

    @Test
    @DisplayName("消息投递 — 信封被路由到队列并可取出")
    void shouldDeliverAndConsumeMessage() throws Exception {
        MessageEnvelope<String> envelope = MessageEnvelope
                .of("test.echo", "hello-rabbitmq")
                .tenantId(100L);

        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_INTERNAL, TEST_ROUTING_KEY, envelope);

        // 使用 receive() 获取原始 Message，手动反序列化
        Message amqpMessage = rabbitTemplate.receive(TEST_QUEUE, 5000);
        assertThat(amqpMessage).as("消息应被投递到队列").isNotNull();

        MessageEnvelope<?> received = objectMapper.readValue(
                amqpMessage.getBody(), MessageEnvelope.class);
        assertThat(received.getType()).isEqualTo("test.echo");
        assertThat(received.getTenantId()).isEqualTo(100L);
        assertThat(received.getMessageId()).isEqualTo(envelope.getMessageId());
    }

    // ==================== ② 消息幂等 ====================

    @Test
    @DisplayName("消息幂等 — 相同 messageId 第二次被识别为已处理")
    void shouldDetectDuplicateMessage() {
        String messageId = "dup-test-" + System.nanoTime();
        assertThat(messageIdempotency.isProcessed(messageId)).isFalse();
        messageIdempotency.markProcessed(messageId, Duration.ofMinutes(5));
        assertThat(messageIdempotency.isProcessed(messageId)).isTrue();
    }

    @Test
    @DisplayName("消息幂等 — 不同 messageId 互不干扰")
    void shouldTreatDifferentMessageIdsIndependently() {
        String msg1 = "msg-" + System.nanoTime();
        String msg2 = "msg-" + (System.nanoTime() + 1);
        messageIdempotency.markProcessed(msg1, Duration.ofMinutes(5));
        assertThat(messageIdempotency.isProcessed(msg2)).isFalse();
    }

    // ==================== ③ 重复消息 ====================

    @Test
    @DisplayName("重复投递 — 消息可被投递多次（消费侧负责幂等）")
    void shouldBeAbleToSendDuplicateMessages() {
        MessageEnvelope<String> env = MessageEnvelope.of("test.dup", "data");
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_INTERNAL, TEST_ROUTING_KEY, env);
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_INTERNAL, TEST_ROUTING_KEY, env);

        Message msg1 = rabbitTemplate.receive(TEST_QUEUE, 3000);
        Message msg2 = rabbitTemplate.receive(TEST_QUEUE, 3000);

        assertThat(msg1).isNotNull();
        assertThat(msg2).as("重复投递的消息也应入队").isNotNull();
    }

    // ==================== ④ DLX ====================

    @Test
    @DisplayName("死信队列声明正确")
    void shouldDeclareDeadLetterQueue() {
        var dlqProps = rabbitAdmin.getQueueProperties(MqConstants.QUEUE_DLX);
        assertThat(dlqProps).as("DLX 队列应存在").isNotNull();
    }

    // ==================== ⑤ 序列化往返 ====================

    @Test
    @DisplayName("消息信封序列化 — JSON 往返一致")
    void shouldSerializeAndDeserializeEnvelope() throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("key1", "value1");
        payload.put("key2", 123);
        MessageEnvelope<Map<String, Object>> envelope = MessageEnvelope
                .of("test.payload.roundtrip", payload)
                .tenantId(200L)
                .parkingLotId(300L);

        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_INTERNAL, TEST_ROUTING_KEY, envelope);

        Message amqpMessage = rabbitTemplate.receive(TEST_QUEUE, 5000);
        assertThat(amqpMessage).isNotNull();

        // 使用 TypeReference 正确处理泛型
        com.fasterxml.jackson.core.type.TypeReference<MessageEnvelope<Map<String, Object>>> typeRef =
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
        MessageEnvelope<Map<String, Object>> received = objectMapper.readValue(amqpMessage.getBody(), typeRef);

        assertThat(received.getType()).isEqualTo("test.payload.roundtrip");
        assertThat(received.getTenantId()).isEqualTo(200L);
        assertThat(received.getParkingLotId()).isEqualTo(300L);
        assertThat(received.getPayload()).containsEntry("key1", "value1");
        assertThat(received.getPayload()).containsEntry("key2", 123);
    }
}
