package com.jushan.boot.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 集成测试基类。
 * <p>
 * 启动一个 MySQL 8.4 容器并通过 {@link ServiceConnection} 自动注入
 * {@code spring.datasource.*} 属性到 Spring 上下文。
 * <p>
 * 容器以静态字段共享，同一测试类中所有方法复用同一容器实例。
 * 不同测试类之间由于 JUnit 默认机制，容器在 JVM 生命周期内保持运行。
 * <p>
 * <strong>使用要求</strong>：本地必须安装 Docker。
 * <p>
 * <strong>后续模块（如需要 MySQL 集成测试的 parking-system 等）应将本基类
 * 提升到 parking-framework 的 test 源码集。</strong>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class TestcontainersBaseTest {

    /** MySQL 8.4 容器镜像，与生产目标版本一致 */
    static final String MYSQL_IMAGE = "mysql:8.4";

    /**
     * 共享 MySQL 容器实例。
     * <p>
     * {@code @ServiceConnection} 在 Spring Boot 3.1+ 中自动将容器连接信息
     * 注册为 {@code spring.datasource.url}、{@code spring.datasource.username}、
     * {@code spring.datasource.password} 等属性，无需手动 {@code @DynamicPropertySource}。
     */
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("jushan_platform_test")
            .withCommand("--character-set-server=utf8mb4",
                         "--collation-server=utf8mb4_unicode_ci",
                         "--max_connections=500");
}
