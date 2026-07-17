package com.smartparking.deviceaccess.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动建立 MQTT 连接。
 * <p>
 * 使用 {@link ApplicationRunner} 而非 {@code @PostConstruct}
 * 是为了确保 Spring 上下文完全初始化后再连接，
 * 避免 Bean 依赖尚未就绪时就开始接收消息。
 * <p>
 * 连接失败不阻塞应用启动 —— 记录错误日志后由 Paho 自动重连机制接管。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttConnectionInitializer implements ApplicationRunner {

    private final MqttGateway mqttGateway;

    @Override
    public void run(ApplicationArguments args) {
        try {
            mqttGateway.connect();
        } catch (Exception e) {
            log.error("Failed to establish initial MQTT connection. "
                    + "The application will continue to start. "
                    + "MQTT auto-reconnect will keep retrying.", e);
        }
    }
}
