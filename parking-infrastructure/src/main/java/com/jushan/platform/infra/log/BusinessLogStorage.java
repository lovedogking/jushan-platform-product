package com.jushan.platform.infra.log;

/**
 * 业务操作日志持久化接口。
 * <p>
 * 由业务模块实现，将 {@link BusinessLogEvent} 写入持久化存储（如 sys_business_log 表）。
 * infrastructure 层通过本接口解耦，不直接依赖业务表。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface BusinessLogStorage {

    /**
     * 保存操作日志。
     *
     * @param event 日志事件
     */
    void save(BusinessLogEvent event);
}
