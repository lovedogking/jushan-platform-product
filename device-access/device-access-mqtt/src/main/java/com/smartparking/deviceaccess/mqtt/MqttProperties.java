package com.smartparking.deviceaccess.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQTT 连接配置属性。
 * <p>
 * 绑定 application.yml 中 device-access.mqtt 下的所有配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "device-access.mqtt")
public class MqttProperties {

    /** EMQX Broker 地址，例如 tcp://localhost:1883 */
    private String brokerUrl = "tcp://localhost:1883";

    /** MQTT Client ID，同一 Broker 下必须唯一 */
    private String clientId = "device-access-v0.1";

    /**
     * 实例标识（可选）。
     * <p>
     * 用于构造固定的 MQTT clientId。不配置时自动从 HOSTNAME 环境变量或本机主机名获取。
     * 修复：替代 UUID.randomUUID()，确保重启后 clientId 不变，配合持久化会话恢复未消费消息。
     */
    private String instanceId;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 自动重连间隔（秒） */
    private int reconnectIntervalSeconds = 10;

    /** 连接超时（秒） */
    private int connectionTimeoutSeconds = 30;

    /** Keep Alive 间隔（秒） */
    private int keepAliveIntervalSeconds = 60;

    /** 默认 QoS 等级 */
    private int defaultQos = 1;
}
