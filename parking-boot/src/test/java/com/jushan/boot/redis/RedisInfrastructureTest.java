package com.jushan.boot.redis;

import com.jushan.framework.lock.DistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis + 分布式锁 集成测试。
 * <p>
 * 启动 Redis 7 Alpine 容器，验证：
 * <ul>
 *   <li>Redis 连通性与基本读写</li>
 *   <li>Key 前缀隔离</li>
 *   <li>分布式锁获取/释放、并发互斥、超时自动释放</li>
 * </ul>
 * <p>
 * Sa-Token 会话读写（Redis 存储）需 Web 上下文，将在 T12 登录功能实现后通过
 * {@code @WebMvcTest} 或完整集成测试验证。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("Redis 基础设施集成测试")
class RedisInfrastructureTest {

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DistributedLock distributedLock;

    // ==================== ① Redis 连通性 ====================

    @Test
    @DisplayName("Redis 连通 — PING 返回 PONG")
    void shouldPingPong() {
        String pong = stringRedisTemplate.getConnectionFactory()
                .getConnection().ping();
        assertThat(pong).isEqualTo("PONG");
    }

    @Test
    @DisplayName("Redis 基本读写 — set/get 正常")
    void shouldSetAndGet() {
        stringRedisTemplate.opsForValue().set("test:hello", "world");
        assertThat(stringRedisTemplate.opsForValue().get("test:hello")).isEqualTo("world");
        stringRedisTemplate.delete("test:hello");
    }

    // ==================== ② Key 前缀隔离 ====================

    @Test
    @DisplayName("Key 前缀隔离 — 不同前缀 Key 不冲突")
    void shouldIsolateKeysByPrefix() {
        stringRedisTemplate.opsForValue().set("app1:counter", "1");
        stringRedisTemplate.opsForValue().set("app2:counter", "2");
        assertThat(stringRedisTemplate.opsForValue().get("app1:counter")).isEqualTo("1");
        assertThat(stringRedisTemplate.opsForValue().get("app2:counter")).isEqualTo("2");
        stringRedisTemplate.delete("app1:counter");
        stringRedisTemplate.delete("app2:counter");
    }

    // ==================== ③ 分布式锁 ====================

    @Test
    @DisplayName("分布式锁 — 获取和释放正常")
    void shouldAcquireAndReleaseLock() {
        String lockKey = "test:lock:acquire";

        assertThat(distributedLock.tryLock(lockKey, 0, 10, TimeUnit.SECONDS))
                .as("应能获取空闲锁").isTrue();
        distributedLock.unlock(lockKey);

        assertThat(distributedLock.tryLock(lockKey, 0, 1, TimeUnit.SECONDS))
                .as("释放后应能再次获取").isTrue();
        distributedLock.unlock(lockKey);
    }

    @Test
    @DisplayName("分布式锁 — 并发互斥")
    void shouldPreventConcurrentLockAcquisition() throws Exception {
        String lockKey = "test:lock:concurrent";

        assertThat(distributedLock.tryLock(lockKey, 0, 30, TimeUnit.SECONDS))
                .as("主线程应获取锁").isTrue();

        AtomicBoolean secondAcquired = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            secondAcquired.set(distributedLock.tryLock(lockKey, 0, 1, TimeUnit.SECONDS));
            latch.countDown();
        }).start();

        latch.await(5, TimeUnit.SECONDS);
        assertThat(secondAcquired.get()).as("另一线程不应获取到已被持有的锁").isFalse();

        distributedLock.unlock(lockKey);
        assertThat(distributedLock.tryLock(lockKey, 0, 5, TimeUnit.SECONDS))
                .as("释放后其他线程应能获取").isTrue();
        distributedLock.unlock(lockKey);
    }

    @Test
    @DisplayName("分布式锁 — 锁超时自动释放")
    void shouldAutoReleaseAfterLeaseTime() throws Exception {
        String lockKey = "test:lock:timeout";

        assertThat(distributedLock.tryLock(lockKey, 0, 1, TimeUnit.SECONDS))
                .as("应能获取锁").isTrue();

        Thread.sleep(1500);

        assertThat(distributedLock.tryLock(lockKey, 0, 5, TimeUnit.SECONDS))
                .as("锁过期后应能重新获取").isTrue();
        distributedLock.unlock(lockKey);
    }
}
