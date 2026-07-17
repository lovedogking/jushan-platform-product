package com.jushan.system.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.common.R;
import com.jushan.system.service.BoothMonitorService;
import com.jushan.system.service.MonitorAlertService;
import com.jushan.system.vo.BoothMonitorSnapshotVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗亭监控接口（P005）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/booth/monitor")
public class BoothMonitorController {

    private final BoothMonitorService boothMonitorService;
    private final MonitorAlertService alertService;

    public BoothMonitorController(BoothMonitorService boothMonitorService,
                                   MonitorAlertService alertService) {
        this.boothMonitorService = boothMonitorService;
        this.alertService = alertService;
    }

    /**
     * 获取岗亭监控初始化快照。
     * <p>
     * 使用 {@code record:read} 权限校验，岗亭操作员默认拥有该权限。
     *
     * @param parkingLotId 停车场 ID
     * @return 监控快照
     */
    @GetMapping("/snapshot")
    @RequirePermission("booth:monitor")
    public R<BoothMonitorSnapshotVO> snapshot(@RequestParam("parkingLotId") Long parkingLotId) {
        return R.ok(boothMonitorService.getSnapshot(parkingLotId));
    }

    /**
     * 刷新设备状态。
     *
     * @param parkingLotId 停车场 ID
     * @return 设备状态列表
     */
    @PostMapping("/devices/refresh")
    @RequirePermission("booth:monitor")
    public R<List<com.jushan.system.vo.DeviceStatusVO>> refreshDevices(
            @RequestParam("parkingLotId") Long parkingLotId) {
        return R.ok(boothMonitorService.refreshDeviceStatuses(parkingLotId));
    }

    /**
     * 确认异常提醒。
     *
     * @param alertId 提醒 ID
     * @return 成功响应
     */
    @PostMapping("/alerts/{alertId}/ack")
    @RequirePermission("booth:monitor")
    public R<Void> acknowledgeAlert(@PathVariable("alertId") Long alertId) {
        alertService.acknowledge(alertId);
        return R.ok();
    }
}
