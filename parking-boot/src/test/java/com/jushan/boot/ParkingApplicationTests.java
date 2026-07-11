package com.jushan.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 最小上下文加载测试。
 * <p>
 * 验证 Spring 容器可正常启动，模块依赖方向正确。
 * 排除数据库等外部依赖，仅验证 Bean 装配和模块依赖。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class ParkingApplicationTests {

    @Test
    void contextLoads() {
        // 上下文成功加载即为通过
    }
}
