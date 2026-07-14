package com.jushan.platform.modules.log.service;

import com.jushan.platform.infra.log.BusinessLogEvent;
import com.jushan.platform.infra.log.BusinessLogStorage;
import com.jushan.platform.modules.log.entity.SysBusinessLog;
import com.jushan.platform.modules.log.mapper.SysBusinessLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 业务操作日志持久化实现。
 * <p>
 * 实现 {@link BusinessLogStorage} 接口，将基础设施层发布的
 * {@link BusinessLogEvent} 持久化到 {@code sys_business_log} 表。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class SysBusinessLogStorage implements BusinessLogStorage {

    private final SysBusinessLogMapper businessLogMapper;

    public SysBusinessLogStorage(SysBusinessLogMapper businessLogMapper) {
        this.businessLogMapper = businessLogMapper;
    }

    /**
     * 保存操作日志。
     *
     * @param event 日志事件
     */
    @Override
    public void save(BusinessLogEvent event) {
        if (event == null) {
            return;
        }

        SysBusinessLog log = new SysBusinessLog();
        log.setTenantId(event.getTenantId());
        log.setOperatorId(event.getOperatorId());
        log.setOperatorName(event.getOperatorName());
        log.setIp(event.getIp());
        log.setOperationType(event.getOperationType());
        log.setOperationObject(event.getOperationObject());
        log.setObjectId(event.getObjectId());
        log.setBeforeValue(event.getBeforeValue());
        log.setAfterValue(event.getAfterValue());
        log.setResult(event.getResult());
        log.setErrorMsg(event.getErrorMsg());
        log.setCreatedAt(event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now());

        businessLogMapper.insert(log);
    }
}
