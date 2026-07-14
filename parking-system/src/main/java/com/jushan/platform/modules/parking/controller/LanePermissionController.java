package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.LanePermissionCreateCmd;
import com.jushan.platform.modules.parking.dto.LanePermissionUpdateCmd;
import com.jushan.platform.modules.parking.entity.LanePermission;
import com.jushan.platform.modules.parking.service.LanePermissionService;
import com.jushan.platform.modules.parking.vo.LanePermissionVO;
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
 * 通道权限管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/lane-permissions")
public class LanePermissionController {

    private final LanePermissionService lanePermissionService;

    public LanePermissionController(LanePermissionService lanePermissionService) {
        this.lanePermissionService = lanePermissionService;
    }

    @PostMapping
    @RequirePermission("lane:update")
    public R<LanePermissionVO> create(@Valid @RequestBody LanePermissionCreateCmd cmd) {
        LanePermissionVO vo = lanePermissionService.create(cmd);
        log.info("创建通道权限成功: permissionId={}, laneId={}, targetType={}",
                vo.getId(), cmd.getLaneId(), cmd.getTargetType());
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("lane:update")
    public R<LanePermissionVO> update(@PathVariable Long id, @Valid @RequestBody LanePermissionUpdateCmd cmd) {
        LanePermissionVO vo = lanePermissionService.update(id, cmd);
        log.info("更新通道权限成功: permissionId={}", id);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("lane:delete")
    public R<Void> delete(@PathVariable Long id) {
        lanePermissionService.delete(id);
        log.info("删除通道权限成功: permissionId={}", id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequirePermission("lane:view")
    public R<LanePermissionVO> detail(@PathVariable Long id) {
        return R.ok(lanePermissionService.detail(id));
    }

    @GetMapping
    @RequirePermission("lane:view")
    public R<IPage<LanePermissionVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long laneId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String status) {
        IPage<LanePermissionVO> result = lanePermissionService.pageList(
                new Page<LanePermission>(current, size), laneId, targetType, targetId, status);
        return R.ok(result);
    }

    @GetMapping("/lane/{laneId}")
    @RequirePermission("lane:view")
    public R<List<LanePermissionVO>> listByLaneId(@PathVariable Long laneId) {
        return R.ok(lanePermissionService.listByLaneId(laneId));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @RequirePermission("lane:view")
    public R<List<LanePermissionVO>> listByVehicleId(@PathVariable Long vehicleId) {
        return R.ok(lanePermissionService.listByVehicleId(vehicleId));
    }

    @GetMapping("/department/{departmentId}")
    @RequirePermission("lane:view")
    public R<List<LanePermissionVO>> listByDepartmentId(@PathVariable Long departmentId) {
        return R.ok(lanePermissionService.listByDepartmentId(departmentId));
    }
}
