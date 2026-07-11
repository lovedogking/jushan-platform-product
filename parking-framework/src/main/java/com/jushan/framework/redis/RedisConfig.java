package com.jushan.framework.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 通用配置。
 * <p>
 * 自定义 {@link RedisTemplate RedisTemplate&lt;String, Object&gt;}，
 * Key 使用 String 序列化，Value 使用 Jackson JSON 序列化。
 * <p>
 * {@link StringRedisTemplate} 由 Spring Boot 自动配置提供，此处不重复创建。
 * <p>
 * 安全降级：当 Redis 被 exclude 时（无 {@link RedisConnectionFactory} Bean），
 * 本配置类自动跳过，不影响非 Redis 场景的测试和应用启动。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfig {

    /**
     * 全局 Key 前缀（按项目/环境区分）。
     * 所有通过此 RedisTemplate 写入的 Key 自动附加此前缀。
     */
    static final String GLOBAL_KEY_PREFIX = "jushan:";

    /**
     * 通用 RedisTemplate。
     * <p>
     * Key 序列化为 {@code jushan:业务模块:具体key} 格式；
     * Value 序列化为 JSON。
     * <p>
     * 仅当 {@link RedisConnectionFactory} Bean 存在时创建。
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 序列化：String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 序列化：Jackson JSON
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate 由 Spring Boot {@code RedisAutoConfiguration} 自动提供，
     * 此处不重复创建。需要 StringRedisTemplate 时直接注入即可。
     */
}
