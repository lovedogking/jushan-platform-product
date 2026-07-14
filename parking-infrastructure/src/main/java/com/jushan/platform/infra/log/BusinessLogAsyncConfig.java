package com.jushan.platform.infra.log;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 业务操作日志异步线程池配置。
 * <p>
 * 线程池参数：
 * <ul>
 *   <li>核心线程数：4</li>
 *   <li>最大线程数：20</li>
 *   <li>队列容量：500</li>
 *   <li>拒绝策略：CallerRunsPolicy（由调用线程执行，避免丢日志）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@EnableAsync
public class BusinessLogAsyncConfig {

    /**
     * 业务日志线程池 Bean。
     */
    @Bean("businessLogExecutor")
    public Executor businessLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("business-log-");
        // CallerRunsPolicy：当线程池和队列都满时，由提交任务的线程自己执行，保证不丢日志
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
