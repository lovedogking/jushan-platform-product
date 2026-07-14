package com.jushan.platform.modules.vehicle.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.vehicle.dto.VehicleCreateCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleMultiPlateBindCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleUpdateCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.service.SysVehicleService;
import com.jushan.platform.modules.vehicle.vo.VehicleVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 车辆主表管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vehicles")
public class SysVehicleController {

    private final SysVehicleService sysVehicleService;

    public SysVehicleController(SysVehicleService sysVehicleService) {
        this.sysVehicleService = sysVehicleService;
    }

    @PostMapping
    @RequirePermission("vehicle:create")
    public R<VehicleVO> create(@Valid @RequestBody VehicleCreateCmd cmd) {
        VehicleVO vo = sysVehicleService.create(cmd);
        log.info("新增车辆成功: vehicleId={}, plate={}", vo.getId(), vo.getPlateNumber());
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("vehicle:update")
    public R<VehicleVO> update(@PathVariable Long id, @Valid @RequestBody VehicleUpdateCmd cmd) {
        VehicleVO vo = sysVehicleService.updateVehicle(id, cmd);
        log.info("编辑车辆成功: vehicleId={}", id);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("vehicle:delete")
    public R<Void> delete(@PathVariable Long id) {
        sysVehicleService.deleteVehicle(id);
        log.info("删除车辆成功: vehicleId={}", id);
        return R.ok();
    }

    @PostMapping("/batch-delete")
    @RequirePermission("vehicle:delete")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        sysVehicleService.batchDelete(ids);
        log.info("批量删除车辆成功: count={}", ids.size());
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequirePermission("vehicle:view")
    public R<VehicleVO> detail(@PathVariable Long id) {
        return R.ok(sysVehicleService.detail(id));
    }

    @GetMapping
    @RequirePermission("vehicle:view")
    public R<IPage<VehicleVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String status) {
        IPage<VehicleVO> result = sysVehicleService.pageList(
                new Page<SysVehicle>(current, size), plateNumber, vehicleType,
                departmentId, parkingLotId, status);
        return R.ok(result);
    }

    @PostMapping("/{id}/multi-plates")
    @RequirePermission("vehicle:update")
    public R<Void> addMultiPlate(@PathVariable Long id, @Valid @RequestBody VehicleMultiPlateBindCmd cmd) {
        sysVehicleService.addMultiPlate(id, cmd);
        log.info("添加一位多车绑定成功: vehicleId={}, plate={}", id, cmd.getPlateNumber());
        return R.ok();
    }

    @DeleteMapping("/{id}/multi-plates/{bindId}")
    @RequirePermission("vehicle:update")
    public R<Void> removeMultiPlate(@PathVariable Long id, @PathVariable Long bindId) {
        sysVehicleService.removeMultiPlate(id, bindId);
        log.info("解除一位多车绑定成功: vehicleId={}, bindId={}", id, bindId);
        return R.ok();
    }
}
