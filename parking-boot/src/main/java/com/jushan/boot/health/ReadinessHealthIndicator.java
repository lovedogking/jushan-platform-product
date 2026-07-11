package com.jushan.boot.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * 自定义就绪检查。
 * <p>
 * 用于 K8s / Docker Compose 的 readiness probe。
 * 当前阶段检查：
 * <ul>
 *   <li>Spring 上下文状态 — 始终 OK（只要能执行到此）</li>
 *   <li>数据库连通性 — T07 启用</li>
 *   <li>Redis 连通性 — T08 启用</li>
 * </ul>
 * 后续阶段逐项添加：
 * <ul>
 *   <li>T23: Device Access 可达性</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ReadinessHealthIndicator.class);

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 使用 ObjectProvider 允许 DataSource / Redis 缺失时优雅降级。
     */
    public ReadinessHealthIndicator(ObjectProvider<DataSource> dataSourceProvider,
                                    ObjectProvider<RedisConnectionFactory> redisProvider) {
        this.dataSource = dataSourceProvider.getIfAvailable();
        this.redisConnectionFactory = redisProvider.getIfAvailable();
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("module", "parking-boot")
                .withDetail("phase", "T08 - Redis、Sa-Token 与会话基础设施");

        // ---- 数据库连通性 ----
        checkDatabase(builder);

        // ---- Redis 连通性 ----
        checkRedis(builder);

        return builder.build();
    }

    private void checkDatabase(Health.Builder builder) {
        if (dataSource == null) {
            builder.withDetail("database", "未配置");
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                DatabaseMetaData meta = conn.getMetaData();
                builder.withDetail("database", meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion())
                        .withDetail("dbUrl", meta.getURL());
            } else {
                log.warn("数据库连接无效");
                builder.down().withDetail("database", "连接无效");
            }
        } catch (SQLException e) {
            log.warn("数据库连通性检查失败: {}", e.getMessage());
            builder.down()
                    .withDetail("database", "不可达")
                    .withDetail("dbError", e.getMessage());
        }
    }

    private void checkRedis(Health.Builder builder) {
        if (redisConnectionFactory == null) {
            builder.withDetail("redis", "未配置");
            return;
        }

        try {
            var conn = redisConnectionFactory.getConnection();
            String pong = conn.ping();
            conn.close();

            if ("PONG".equals(pong)) {
                builder.withDetail("redis", "可达 (PONG)");
            } else {
                log.warn("Redis PING 返回异常: {}", pong);
                builder.down().withDetail("redis", "异常响应");
            }
        } catch (Exception e) {
            log.warn("Redis 连通性检查失败: {}", e.getMessage());
            builder.down()
                    .withDetail("redis", "不可达")
                    .withDetail("redisError", e.getMessage());
        }
    }
}
