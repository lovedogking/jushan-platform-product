package com.smartparking.deviceaccess.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.common.dto.mqtt.MqttMessage;
import com.smartparking.deviceaccess.common.exception.MqttConnectionException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * MQTT 网关的 Eclipse Paho 实现。
 * <p>
 * 使用 MqttAsyncClient（异步客户端）+ MqttCallbackExtended 接收消息。
 * Paho 的 {@code setAutomaticReconnect(true)} 处理断线重连，
 * {@code connectComplete} 回调中重新订阅 Topic。
 * <p>
 * 请求-响应关联：下发命令时按 message.id 注册 CompletableFuture，
 * 收到 reply Topic 消息时通过 id 匹配完成 Future。
 * <p>
 * 注意：Paho 的 {@link org.eclipse.paho.client.mqttv3.MqttMessage} 与
 * 本项目 {@link MqttMessage} 同名不同类，messageArrived 中使用完全限定名区分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttGatewayImpl implements MqttGateway {

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;

    private volatile MqttAsyncClient client;
    private volatile MqttMessageListener globalListener;

    /** 原始 Map 消息监听器列表（线程安全） */
    private final List<MqttRawMessageListener> rawListeners = new CopyOnWriteArrayList<>();

    /** 等待设备回复的 Future，key = message.id */
    private final ConcurrentMap<String, CompletableFuture<MqttMessage>> pendingFutures = new ConcurrentHashMap<>();

    /**
     * 业务逻辑执行线程池。
     * <p>
     * 将 listener 回调从 Paho MQTT 回调线程中异步化，避免业务逻辑阻塞 MQTT 消息接收。
     * 使用 CallerRunsPolicy 确保线程池满载时不会丢消息（回退到 Paho 线程同步执行）。
     */
    private final ExecutorService businessExecutor = new ThreadPoolExecutor(
            4, 8, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r);
                t.setName("mqtt-biz-" + t.getId());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // ──────────────────── 预订阅 Topic ────────────────────

    /** 所有设备上行消息（识别结果、心跳、道闸状态等） */
    private static final String TOPIC_ALL_UPLINK = "device/+/message/up/#";

    /** 所有设备对平台命令的回复 */
    private static final String TOPIC_ALL_REPLIES = "device/+/message/down/+/reply";

    /** 信路通：所有设备上行消息（Conn / Rtd / Result） */
    private static final String TOPIC_XLT_UPLINK = "upload/+";

    /** 信路通：所有设备对下行命令的回复 */
    private static final String TOPIC_XLT_REPLIES = "download/+/reply";

    /** 芊熠：设备注册主题（固定，设备开机上报 subtopic/pubtopic） */
    private static final String TOPIC_QY_REGISTER = "/serverAll";

    /** 芊熠：车牌相机默认上行主题（实际以设备注册上报的 pubtopic 为准，见动态订阅） */
    private static final String TOPIC_QY_UPLINK = "aiot/plate/+";

    /**
     * 芊熠：qymqtt 协议相机自定义上行主题（/{产品标识}/{sn}/qymqttpost）。
     * <p>静态订阅保证 DA 重启后、设备重新注册前心跳/识别结果不丢失。</p>
     */
    private static final String TOPIC_QY_UPLINK_QYMQTT = "/qymqtt/+/qymqttpost";

    /** 动态订阅的 Topic 集合（如芊熠设备注册上报的 pubtopic），重连后一并重订 */
    private final java.util.Set<String> dynamicSubscriptions = ConcurrentHashMap.newKeySet();

    // ──────────────────── 连接管理 ────────────────────

    @Override
    public void connect() {
        if (client != null && client.isConnected()) {
            log.info("MQTT already connected, skipping");
            return;
        }

        try {
            String brokerUrl = properties.getBrokerUrl();
            // 修复：使用稳定实例标识替代 UUID，确保重启后 clientId 不变 + 持久化会话可恢复未消费消息
            String clientId = properties.getClientId() + "_" + getInstanceId();

            // 修复：使用文件持久化替代内存持久化，配合 cleanSession=false 实现会话持久化
            client = new MqttAsyncClient(brokerUrl, clientId,
                    new org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence(
                            System.getProperty("java.io.tmpdir") + "/mqtt-persistence"));

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            // 修复：cleanSession=false 使 Broker 保留未消费消息和订阅，重启后恢复
            options.setCleanSession(false);
            options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
            options.setKeepAliveInterval(properties.getKeepAliveIntervalSeconds());

            String username = properties.getUsername();
            String password = properties.getPassword();
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
            }
            if (password != null && !password.isBlank()) {
                options.setPassword(password.toCharArray());
            }

            client.setCallback(createCallback());

            log.info("Connecting to MQTT Broker: {}", brokerUrl);
            IMqttToken token = client.connect(options);
            token.waitForCompletion(properties.getConnectionTimeoutSeconds() * 1000L);

            if (!client.isConnected()) {
                throw new MqttConnectionException("Failed to connect to MQTT Broker: " + brokerUrl);
            }

            subscribeDefaultTopics();
            log.info("MQTT connected successfully. Broker: {}, ClientId: {}", brokerUrl, clientId);
        } catch (MqttException e) {
            throw new MqttConnectionException("MQTT connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (client == null) {
            return;
        }
        try {
            // 修复：先关闭业务线程池，避免 MQTT 断开后仍有残留回调
            businessExecutor.shutdown();
            try {
                if (!businessExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    businessExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                businessExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            pendingFutures.values().forEach(f -> f.cancel(true));
            pendingFutures.clear();
            client.disconnect();
            client.close();
            log.info("MQTT disconnected");
        } catch (MqttException e) {
            log.warn("Error during MQTT disconnect: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    // ──────────────────── 消息监听 ────────────────────

    @Override
    public void registerListener(MqttMessageListener listener) {
        this.globalListener = listener;
        log.info("MQTT global listener registered: {}", listener.getClass().getSimpleName());
    }

    @Override
    public void addRawListener(MqttRawMessageListener listener) {
        this.rawListeners.add(listener);
        log.info("MQTT raw listener added: {}", listener.getClass().getSimpleName());
    }

    // ──────────────────── 发布消息 ────────────────────

    @Override
    public void publish(String topic, MqttMessage message) {
        ensureConnected();
        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            client.publish(topic, payload, properties.getDefaultQos(), false);
            log.debug("MQTT published to {}: id={}, name={}", topic, message.getId(), message.getName());
        } catch (Exception e) {
            throw new MqttConnectionException("Failed to publish MQTT message: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<MqttMessage> publishAndWait(String topic, MqttMessage message,
                                                         long timeout, TimeUnit unit) {
        ensureConnected();

        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }

        CompletableFuture<MqttMessage> future = new CompletableFuture<>();
        pendingFutures.put(message.getId(), future);

        future.orTimeout(timeout, unit)
                .whenComplete((r, e) -> pendingFutures.remove(message.getId()));

        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            client.publish(topic, payload, properties.getDefaultQos(), false);
            log.info("MQTT published (awaiting reply) to {}: id={}, name={}",
                    topic, message.getId(), message.getName());
        } catch (Exception e) {
            pendingFutures.remove(message.getId());
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public void publishRaw(String topic, String json) {
        ensureConnected();
        try {
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            client.publish(topic, payload, properties.getDefaultQos(), false);
            log.debug("MQTT raw published to {}: {}", topic, json);
        } catch (Exception e) {
            throw new MqttConnectionException("Failed to publish raw MQTT message: " + e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(String topic) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        if (!dynamicSubscriptions.add(topic)) {
            return; // 已订阅过，幂等返回
        }
        if (!isConnected()) {
            // 未连接时仅登记，connect()/重连后的 subscribeDefaultTopics 会一并订阅
            log.info("Dynamic subscription registered (pending connect): {}", topic);
            return;
        }
        try {
            client.subscribe(topic, properties.getDefaultQos()).waitForCompletion();
            log.info("Subscribed (dynamic): {}", topic);
        } catch (MqttException e) {
            dynamicSubscriptions.remove(topic);
            throw new MqttConnectionException("Failed to subscribe topic " + topic + ": " + e.getMessage(), e);
        }
    }

    // ──────────────────── 内部方法 ────────────────────

    /**
     * 获取稳定的实例标识，用于构造固定的 MQTT clientId。
     * <p>
     * 优先级：配置 instanceId > HOSTNAME 环境变量 > 本机主机名 > 时间戳 fallback。
     * 修复：替代 UUID.randomUUID()，确保重启后 clientId 不变。
     */
    private String getInstanceId() {
        String configured = properties.getInstanceId();
        if (configured != null && !configured.isBlank()) return configured;

        String env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) return env;

        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "default-" + System.currentTimeMillis();
        }
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new MqttConnectionException("MQTT is not connected. Call connect() first.");
        }
    }

    private void subscribeDefaultTopics() throws MqttException {
        client.subscribe(TOPIC_ALL_UPLINK, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_ALL_UPLINK);

        client.subscribe(TOPIC_ALL_REPLIES, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_ALL_REPLIES);

        client.subscribe(TOPIC_XLT_UPLINK, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_XLT_UPLINK);

        client.subscribe(TOPIC_XLT_REPLIES, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_XLT_REPLIES);

        client.subscribe(TOPIC_QY_REGISTER, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_QY_REGISTER);

        client.subscribe(TOPIC_QY_UPLINK, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_QY_UPLINK);

        client.subscribe(TOPIC_QY_UPLINK_QYMQTT, properties.getDefaultQos()).waitForCompletion();
        log.info("Subscribed: {}", TOPIC_QY_UPLINK_QYMQTT);

        for (String topic : dynamicSubscriptions) {
            client.subscribe(topic, properties.getDefaultQos()).waitForCompletion();
            log.info("Subscribed (dynamic): {}", topic);
        }
    }

    /**
     * 创建 Paho 回调。
     * <p>
     * messageArrived 中做三件事：
     * <ol>
     *   <li>将 JSON 字节反序列化为 {@link MqttMessage}</li>
     *   <li>检查是否为某个待处理请求的回复（按 message.id 匹配），是则完成 Future</li>
     *   <li>始终回调给全局 listener（adapter 做业务分发）</li>
     * </ol>
     */
    private MqttCallbackExtended createCallback() {
        return new MqttCallbackExtended() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connect complete. Reconnect: {}, Server: {}", reconnect, serverURI);
                if (reconnect) {
                    // 修复：重连后订阅必须在独立线程执行（subscribeDefaultTopics 内有 waitForCompletion 阻塞调用）
                    // 复用 businessExecutor 而非 new Thread()，统一线程管理
                    businessExecutor.execute(() -> {
                        try {
                            subscribeDefaultTopics();
                            log.info("Re-subscribed after reconnect");
                        } catch (MqttException e) {
                            log.error("Re-subscribe failed after reconnect", e);
                        }
                    });
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost. Paho auto-reconnect in progress. Cause: {}",
                        cause != null ? cause.getMessage() : "unknown");
            }

            @Override
            @SuppressWarnings("unchecked")
            public void messageArrived(String topic,
                                       /* 完全限定名避免与项目 MqttMessage 冲突 */
                                       org.eclipse.paho.client.mqttv3.MqttMessage pahoMsg) {
                String json = new String(pahoMsg.getPayload(), StandardCharsets.UTF_8);
                log.debug("MQTT received. Topic: {}, Payload: {}", topic, json);

                // 0. 解析为 Map（用于 raw listeners，如信路通）
                Map<String, Object> rawJson = null;
                try {
                    rawJson = objectMapper.readValue(json, Map.class);
                } catch (Exception e) {
                    // 修复：rawJson 解析失败不应阻断后续 msg 解析和 listener 回调
                    // 改为 debug 级别日志，rawJson 保持 null，后续步骤通过 null 检查安全跳过
                    log.debug("Failed to parse MQTT message as Map, rawJson will be null. Topic: {}", topic);
                }

                // 1. 尝试反序列化为臻识 MqttMessage 信封（用于 MqttMessageListener）
                MqttMessage msg = null;
                try {
                    msg = objectMapper.readValue(json, MqttMessage.class);
                } catch (Exception e) {
                    log.debug("Not standard format, route to raw listener. Topic: {}", topic);
                }

                // 2. 请求-响应关联（臻识格式：按 message.id 匹配）
                //    必须在 Paho 线程同步执行，否则释放等待线程会有延迟
                if (msg != null) {
                    String msgId = msg.getId();
                    if (msgId != null) {
                        CompletableFuture<MqttMessage> future = pendingFutures.remove(msgId);
                        if (future != null) {
                            future.complete(msg);
                        }
                    }
                }

                // 3. 请求-响应关联（信路通格式：按 rawJson 中的 msgId 匹配）
                //    信路通下行命令在 rawJson 中带有 msgId 字段用于回执关联
                //    修复：增加 rawJson != null 保护，避免 rawJson 解析失败时 NPE
                if (rawJson != null) {
                    Object rawMsgId = rawJson.get("msgId");
                    if (rawMsgId instanceof String rawId && !rawId.isEmpty()) {
                        CompletableFuture<MqttMessage> future = pendingFutures.remove(rawId);
                        if (future != null) {
                            future.complete(msg);
                        }
                    }
                }

                // 4. 回调全局 MqttMessageListener（臻识 adapter 按 message.name 分发）
                //    修复：异步化到 businessExecutor，避免阻塞 Paho 回调线程
                MqttMessageListener listener = globalListener;
                if (msg != null && listener != null) {
                    MqttMessageListener finalListener = listener;
                    MqttMessage finalMsg = msg;
                    String finalTopic = topic;
                    businessExecutor.execute(() -> {
                        try {
                            finalListener.onMessage(finalTopic, finalMsg);
                        } catch (Exception e) {
                            log.error("Error in MQTT message listener. Topic: {}, Name: {}", finalTopic, finalMsg.getName(), e);
                        }
                    });
                }

                // 5. 回调 Raw 监听器（信路通等非臻识格式的品牌）
                //    修复：异步化到 businessExecutor，避免阻塞 Paho 回调线程
                if (!rawListeners.isEmpty()) {
                    List<MqttRawMessageListener> listeners = List.copyOf(rawListeners);
                    Map<String, Object> finalRawJson = rawJson;
                    String finalTopic2 = topic;
                    businessExecutor.execute(() -> {
                        for (MqttRawMessageListener rawListener : listeners) {
                            try {
                                rawListener.onRawMessage(finalTopic2, finalRawJson);
                            } catch (Exception e) {
                                log.error("Error in MQTT raw message listener. Topic: {}", finalTopic2, e);
                            }
                        }
                    });
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 无需处理
            }
        };
    }
}
