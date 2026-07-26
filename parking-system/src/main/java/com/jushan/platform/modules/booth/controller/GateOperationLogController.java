package com.jushan.platform.modules.booth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.booth.entity.GateOperationLog;
import com.jushan.platform.modules.booth.service.GateOperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/booth/operation-logs")
public class GateOperationLogController {

    private final GateOperationLogService logService;

    public GateOperationLogController(GateOperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    @RequirePermission("booth:view")
    public R<IPage<GateOperationLog>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String plateNumber) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<GateOperationLog> w = new LambdaQueryWrapper<GateOperationLog>()
                .eq(tenantId != null, GateOperationLog::getTenantId, tenantId)
                .eq(parkingLotId != null, GateOperationLog::getParkingLotId, parkingLotId)
                .eq(operationType != null, GateOperationLog::getOperationType, operationType)
                .like(plateNumber != null, GateOperationLog::getPlateNumber, plateNumber)
                .orderByDesc(GateOperationLog::getOperationTime);
        return R.ok(logService.page(new Page<>(current, size), w));
    }

    @GetMapping("/types")
    @RequirePermission("booth:view")
    public R<List<String>> operationTypes() {
        return R.ok(List.of("OPEN_GATE", "MANUAL_RELEASE", "CLOSE_GATE"));
    }
}
