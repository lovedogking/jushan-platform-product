package com.jushan.platform.infra.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 业务操作日志异步监听器。
 * <p>
 * 异步消费 {@link BusinessLogEvent}，并调用 {@link BusinessLogStorage} 持久化。
 * 线程池配置见 {@link BusinessLogAsyncConfig}。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class BusinessLogListener {

    private static final Logger log = LoggerFactory.getLogger(BusinessLogListener.class);

    private final BusinessLogStorage businessLogStorage;

    public BusinessLogListener(BusinessLogStorage businessLogStorage) {
        this.businessLogStorage = businessLogStorage;
    }

    /**
     * 异步处理操作日志事件。
     */
    @Async("businessLogExecutor")
    @EventListener
    public void onBusinessLogEvent(BusinessLogEvent event) {
        try {
            businessLogStorage.save(event);
            log.debug("操作日志已异步保存: operatorId={} operationType={}",
                    event.getOperatorId(), event.getOperationType());
        } catch (Exception e) {
            // 日志保存失败不能影响主业务
            log.error("操作日志保存失败: operatorId={} operationType={}",
                    event.getOperatorId(), event.getOperationType(), e);
        }
    }
}
