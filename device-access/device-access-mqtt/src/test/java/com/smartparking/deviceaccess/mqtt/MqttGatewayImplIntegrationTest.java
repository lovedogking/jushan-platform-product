package com.smartparking.deviceaccess.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.common.dto.mqtt.MqttMessage;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MQTT 网关集成测试。
 * <p>
 * 直接连接已有 EMQX 容器（localhost:1883），不依赖 Testcontainers。
 * 使用唯一测试 SN 前缀避免与真实设备冲突。
 * <p>
 * 前提：EMQX 容器必须已启动。
 */
@DisplayName("MqttGatewayImpl Integration Tests (real EMQX)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MqttGatewayImplIntegrationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TEST_SN = "test-mqtt-integration";

    private MqttGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        MqttProperties props = new MqttProperties();
        props.setBrokerUrl(BROKER_URL);
        props.setClientId("test-" + UUID.randomUUID().toString().substring(0, 8));
        props.setConnectionTimeoutSeconds(10);
        props.setKeepAliveIntervalSeconds(30);
        props.setDefaultQos(1);

        gateway = new MqttGatewayImpl(props, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (gateway != null && gateway.isConnected()) {
            gateway.disconnect();
        }
    }

    // ──────────────────── 连接测试 ────────────────────

    @Test
    @Order(1)
    @DisplayName("Should connect to EMQX Broker successfully")
    void shouldConnectSuccessfully() {
        gateway.connect();
        assertThat(gateway.isConnected()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("Should not throw when connect is called twice")
    void shouldSkipSecondConnect() {
        gateway.connect();
        assertThat(gateway.isConnected()).isTrue();

        gateway.connect(); // 第二次，应跳过
        assertThat(gateway.isConnected()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Should disconnect and report not connected")
    void shouldDisconnect() {
        gateway.connect();
        assertThat(gateway.isConnected()).isTrue();

        gateway.disconnect();
        assertThat(gateway.isConnected()).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("Should report not connected before connect is called")
    void shouldNotBeConnectedInitially() {
        assertThat(gateway.isConnected()).isFalse();
    }

    // ──────────────────── 发布/消息接收测试 ────────────────────

    @Test
    @Order(5)
    @DisplayName("Should register listener and publish message without error")
    void shouldRegisterListenerAndPublish() throws Exception {
        gateway.connect();

        MqttMessageListener listener = (topic, msg) -> { /* no-op */ };
        gateway.registerListener(listener);

        MqttMessage message = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name("test_message")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();

        gateway.publish("device/" + TEST_SN + "/message/up/test", message);
        // 不抛异常即为通过
    }

    @Test
    @Order(6)
    @DisplayName("Should receive message via listener after subscribing")
    void shouldReceivePublishedMessage() throws Exception {
        gateway.connect();

        AtomicReference<MqttMessage> received = new AtomicReference<>();
        AtomicReference<String> receivedTopic = new AtomicReference<>();

        gateway.registerListener((topic, message) -> {
            received.set(message);
            receivedTopic.set(topic);
        });

        MqttMessage testMsg = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name("keep_alive")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();

        String topic = "device/" + TEST_SN + "/message/up/keep_alive";
        gateway.publish(topic, testMsg);

        // 等待消息被 listener 接收
        Thread.sleep(500);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().getName()).isEqualTo("keep_alive");
        assertThat(received.get().getSn()).isEqualTo(TEST_SN);
    }

    // ──────────────────── 请求-响应测试 ────────────────────

    @Test
    @Order(7)
    @DisplayName("Should complete future when reply matches message id")
    void shouldCorrelateReplyByMessageId() throws Exception {
        gateway.connect();

        String replyId = UUID.randomUUID().toString();
        String requestTopic = "device/" + TEST_SN + "/message/down/set_time";
        String replyTopic = requestTopic + "/reply";

        MqttMessage requestMsg = MqttMessage.builder()
                .id(replyId)
                .sn(TEST_SN)
                .name("set_time")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();

        // 用独立线程模拟设备回复
        Thread replyThread = new Thread(() -> {
            try {
                Thread.sleep(500);
                MqttClient replyClient = new MqttClient(
                        BROKER_URL,
                        "test-reply-" + UUID.randomUUID().toString().substring(0, 8),
                        new MemoryPersistence());
                replyClient.connect();
                MqttMessage replyMsg = MqttMessage.builder()
                        .id(replyId)
                        .sn(TEST_SN)
                        .name("set_time")
                        .code(200)
                        .timestamp(System.currentTimeMillis() / 1000)
                        .build();
                replyClient.publish(replyTopic,
                        new org.eclipse.paho.client.mqttv3.MqttMessage(
                                objectMapper.writeValueAsBytes(replyMsg)));
                replyClient.disconnect();
                replyClient.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        replyThread.start();

        CompletableFuture<MqttMessage> future =
                gateway.publishAndWait(requestTopic, requestMsg, 10, TimeUnit.SECONDS);

        MqttMessage reply = future.get(15, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(reply.getCode()).isEqualTo(200);
        assertThat(reply.getId()).isEqualTo(replyId);

        replyThread.join(5000);
    }

    @Test
    @Order(8)
    @DisplayName("Should timeout when no reply is received")
    void shouldTimeoutWhenNoReply() throws Exception {
        gateway.connect();

        MqttMessage requestMsg = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name("set_time")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();

        // 发到一个不会有回复的 topic
        CompletableFuture<MqttMessage> future = gateway.publishAndWait(
                "device/" + TEST_SN + "/message/down/set_time",
                requestMsg, 3, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    // ──────────────────── 异常测试 ────────────────────

    @Test
    @Order(9)
    @DisplayName("Should throw when publishing while disconnected")
    void shouldThrowWhenPublishingDisconnected() {
        MqttMessage message = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .name("test")
                .build();

        assertThatThrownBy(() -> gateway.publish("test/topic", message))
                .hasMessageContaining("not connected");
    }

    @Test
    @Order(10)
    @DisplayName("Should throw when publishAndWait while disconnected")
    void shouldThrowWhenPublishAndWaitDisconnected() {
        MqttMessage message = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .name("test")
                .build();

        assertThatThrownBy(() ->
                gateway.publishAndWait("test/topic", message, 5, TimeUnit.SECONDS))
                .hasMessageContaining("not connected");
    }

    @Test
    @Order(11)
    @DisplayName("Should throw MqttConnectionException for invalid broker")
    void shouldThrowForInvalidBroker() {
        MqttProperties badProps = new MqttProperties();
        badProps.setBrokerUrl("tcp://localhost:1");
        badProps.setClientId("test-" + UUID.randomUUID().toString().substring(0, 8));
        badProps.setConnectionTimeoutSeconds(3);
        badProps.setKeepAliveIntervalSeconds(30);

        MqttGatewayImpl badGateway = new MqttGatewayImpl(badProps, objectMapper);
        assertThatThrownBy(badGateway::connect)
                .hasMessageContaining("MQTT connection failed");
    }
}
