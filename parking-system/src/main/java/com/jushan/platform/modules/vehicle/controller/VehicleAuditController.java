package com.jushan.platform.modules.vehicle.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.vehicle.dto.VehicleAuditProcessCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleAuditSubmitCmd;
import com.jushan.platform.modules.vehicle.entity.VehicleAudit;
import com.jushan.platform.modules.vehicle.service.VehicleAuditService;
import com.jushan.platform.modules.vehicle.vo.VehicleAuditVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 车辆审核管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vehicle-audits")
public class VehicleAuditController {

    private final VehicleAuditService vehicleAuditService;

    public VehicleAuditController(VehicleAuditService vehicleAuditService) {
        this.vehicleAuditService = vehicleAuditService;
    }

    /**
     * 提交审核申请。
     */
    @PostMapping
    @RequirePermission("vehicle:update")
    public R<VehicleAuditVO> submit(@Valid @RequestBody VehicleAuditSubmitCmd cmd) {
        VehicleAuditVO vo = vehicleAuditService.submit(cmd);
        log.info("提交车辆审核申请: auditId={}, vehicleId={}, applyType={}",
                vo.getId(), cmd.getVehicleId(), cmd.getApplyType());
        return R.ok(vo);
    }

    /**
     * 处理审核（通过/驳回/待补充）。
     */
    @PutMapping("/{id}/process")
    @RequirePermission("vehicle:update")
    public R<VehicleAuditVO> process(@PathVariable Long id, @Valid @RequestBody VehicleAuditProcessCmd cmd) {
        VehicleAuditVO vo = vehicleAuditService.process(id, cmd);
        log.info("处理车辆审核: auditId={}, status={}", id, cmd.getAuditStatus());
        return R.ok(vo);
    }

    /**
     * 查询审核详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("vehicle:view")
    public R<VehicleAuditVO> detail(@PathVariable Long id) {
        return R.ok(vehicleAuditService.detail(id));
    }

    /**
     * 分页查询审核列表。
     */
    @GetMapping
    @RequirePermission("vehicle:view")
    public R<IPage<VehicleAuditVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String applyType,
            @RequestParam(required = false) String plateNumber) {
        IPage<VehicleAuditVO> result = vehicleAuditService.pageList(
                new Page<VehicleAudit>(current, size), auditStatus, applyType, plateNumber);
        return R.ok(result);
    }

    /**
     * 查询指定车辆的审核记录。
     */
    @GetMapping("/vehicle/{vehicleId}")
    @RequirePermission("vehicle:view")
    public R<List<VehicleAuditVO>> listByVehicleId(@PathVariable Long vehicleId) {
        return R.ok(vehicleAuditService.listByVehicleId(vehicleId));
    }
}
