package com.jushan.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GPIO 道闸控制服务 — 通过 MQTT gpio_out 命令直接控制臻识 C5H 相机 GPIO 引脚。
 * <p>
 * <b>临时方案</b>：当前 Device Access 不支持 gpio_out 命令，平台直接通过 MQTT 向 EMQX
 * 发布 gpio_out 命令。待 DA 支持 gpio_out 后迁移到统一 DeviceAccessClient。
 * <p>
 * GPIO 信号配置（经真机联调验证）：
 * <ul>
 *   <li>开闸：io=0, value=2（脉冲）, delay=1500ms</li>
 *   <li>关闸：io=1, value=2（脉冲）, delay=3000ms</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class GpioGateService {

    private static final Logger log = LoggerFactory.getLogger(GpioGateService.class);

    /** MQTT Broker 地址 */
    private final String brokerUrl;

    /** MQTT 客户端 ID 前缀 */
    private final String clientId;

    /** MQTT 连接超时（秒） */
    private static final int CONNECTION_TIMEOUT = 10;

    /** MQTT 客户端实例（延迟初始化，用完即弃） */
    private MqttClient mqttClient;

    private final ObjectMapper objectMapper;

    public GpioGateService(@Value("${jushan.gpio-gate.broker-url:tcp://121.41.131.215:1883}") String brokerUrl,
                           ObjectMapper objectMapper) {
        this.brokerUrl = brokerUrl;
        this.clientId = "platform-gpio-" + UUID.randomUUID().toString().substring(0, 8);
        this.objectMapper = objectMapper;
    }

    /**
     * 发送 gpio_out 开闸命令。
     * <p>
     * 经真机联调验证：io=0, value=2（脉冲）, delay=1500ms → 闸杆抬起。
     *
     * @param deviceSn 设备 SN（臻识 C5H 序列号）
     * @return true 表示 MQTT 发布成功（不保证相机执行成功）
     */
    public boolean openGate(String deviceSn) {
        return sendGpioCommand(deviceSn, 0, 2, 1500);
    }

    /**
     * 发送 gpio_out 关闸命令。
     * <p>
     * 经真机联调验证：io=1, value=2（脉冲）, delay=3000ms → 闸杆落下。
     *
     * @param deviceSn 设备 SN（臻识 C5H 序列号）
     * @return true 表示 MQTT 发布成功（不保证相机执行成功）
     */
    public boolean closeGate(String deviceSn) {
        return sendGpioCommand(deviceSn, 1, 2, 3000);
    }

    /**
     * 发送 gpio_out 命令。
     *
     * @param deviceSn 设备 SN
     * @param io       GPIO 端口号
     * @param value    信号类型（0=断开, 1=常通, 2=脉冲）
     * @param delay    脉冲宽度（毫秒）
     * @return true 表示 MQTT 发布成功
     */
    public boolean sendGpioCommand(String deviceSn, int io, int value, int delay) {
        try {
            String topic = "device/" + deviceSn + "/message/down/gpio_out";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            payload.put("sn", deviceSn);
            payload.put("name", "gpio_out");
            payload.put("version", "1.0");
            payload.put("timestamp", System.currentTimeMillis() / 1000);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("delay", delay);
            body.put("io", io);
            body.put("value", value);

            Map<String, Object> payloadBody = new LinkedHashMap<>();
            payloadBody.put("type", "gpio_out");
            payloadBody.put("body", body);

            payload.put("payload", payloadBody);

            String payloadJson = objectMapper.writeValueAsString(payload);

            MqttMessage mqttMsg = new MqttMessage(payloadJson.getBytes());
            mqttMsg.setQos(1);

            MqttClient client = getClient();
            client.publish(topic, mqttMsg);

            log.info("GPIO 命令已发送: deviceSn={}, topic={}, io={}, value={}, delay={}",
                    deviceSn, topic, io, value, delay);
            return true;

        } catch (Exception e) {
            log.error("GPIO 命令发送失败: deviceSn={}, io={}, value={}, delay={}, error={}",
                    deviceSn, io, value, delay, e.getMessage());
            return false;
        }
    }

    /**
     * 获取 MQTT 客户端（延迟初始化）。
     */
    private MqttClient getClient() throws Exception {
        if (mqttClient == null || !mqttClient.isConnected()) {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(CONNECTION_TIMEOUT);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            client.connect(options);
            mqttClient = client;
            log.info("MQTT 客户端已连接: brokerUrl={}, clientId={}", brokerUrl, clientId);
        }
        return mqttClient;
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT 客户端已断开: clientId={}", clientId);
            } catch (Exception e) {
                log.warn("MQTT 客户端断开异常: {}", e.getMessage());
            }
        }
    }
}
