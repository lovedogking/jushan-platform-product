package com.jushan.platform.modules.booth.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.platform.modules.booth.entity.GateOperationLog;
import com.jushan.platform.modules.booth.mapper.GateOperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GateOperationLogService extends ServiceImpl<GateOperationLogMapper, GateOperationLog> {

    public void saveLog(GateOperationLog operationLog) {
        if (operationLog.getOperationTime() == null) {
            operationLog.setOperationTime(java.time.LocalDateTime.now());
        }
        this.save(operationLog);
        log.info("操作日志已记录: type={}, lane={}, plate={}", operationLog.getOperationType(), operationLog.getLaneName(), operationLog.getPlateNumber());
    }
}
