package com.jushan.boot.mq;

import com.jushan.boot.ParkingApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RabbitMQ + Redis 集成测试基类。
 * <p>
 * 启动 MySQL + RabbitMQ + Redis 容器，覆盖 test profile 中 RabbitMQ 和 Redis 的排除。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest(classes = ParkingApplication.class)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
    // 移除 test profile 的所有排除项，让所有基础设施由 Testcontainers 提供
    "spring.autoconfigure.exclude=",
    // 启用平台内部 MQ 配置
    "jushan.mq.rabbit.enabled=true"
})
public abstract class RabbitMqTestBase {

    static final String MYSQL_IMAGE = "mysql:8.4";
    static final String RABBITMQ_IMAGE = "rabbitmq:4-management-alpine";
    static final String REDIS_IMAGE = "redis:7-alpine";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("jushan_platform_mq_test");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(RABBITMQ_IMAGE);

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);
}
