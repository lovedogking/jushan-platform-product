package com.jushan.system.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.system.dto.CreateLaneRequest;
import com.jushan.system.dto.UpdateLaneRequest;
import com.jushan.system.service.ParkingLaneService;
import com.jushan.system.vo.ParkingLaneVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 车道管理控制器（运营端）。
 * <p>
 * 客户管理员和停车场管理员通过此接口管理本租户内停车场的车道。
 * 所有操作通过停车场归属推导租户范围，不信任前端传入的 tenantId。
 * <p>
 * <strong>权限</strong>：
 * <ul>
 *   <li>{@code parking:read} — 查看车道列表和详情</li>
 *   <li>{@code parking:write} — 创建/编辑车道、启用/停用</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/lanes")
public class ParkingLaneController {

    private static final Logger log = LoggerFactory.getLogger(ParkingLaneController.class);

    private final ParkingLaneService laneService;

    public ParkingLaneController(ParkingLaneService laneService) {
        this.laneService = laneService;
    }

    /**
     * 分页查询车道列表。
     * <p>
     * 权限：parking:read
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param parkingLotId 停车场 ID（必填）
     * @param status       状态筛选（可选：1=启用, 2=禁用, 3=维护中）
     * @param type         方向筛选（可选：1=入口, 2=出口, 3=双向）
     */
    @GetMapping
    @RequirePermission("parking:read")
    public R<IPage<ParkingLaneVO>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) Long parkingLotId,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) Integer type) {
        IPage<ParkingLaneVO> result = laneService.list(page, size, parkingLotId, status, type);
        return R.ok(result);
    }

    /**
     * 查询车道详情。
     * <p>
     * 权限：parking:read
     */
    @GetMapping("/{id}")
    @RequirePermission("parking:read")
    public R<ParkingLaneVO> detail(@PathVariable Long id) {
        ParkingLaneVO vo = laneService.get(id);
        return R.ok(vo);
    }

    /**
     * 创建车道。
     * <p>
     * 权限：parking:write
     */
    @PostMapping
    @RequirePermission("parking:write")
    public R<ParkingLaneVO> create(@Valid @RequestBody CreateLaneRequest request) {
        ParkingLaneVO vo = laneService.create(request);
        log.info("创建车道成功: laneId={}, parkingLotId={}, name={}, type={}",
                vo.getId(), vo.getParkingLotId(), vo.getName(), vo.getType());
        return R.ok(vo);
    }

    /**
     * 更新车道基础信息（部分更新）。
     * <p>
     * 权限：parking:write
     */
    @PutMapping("/{id}")
    @RequirePermission("parking:write")
    public R<ParkingLaneVO> update(@PathVariable Long id, @Valid @RequestBody UpdateLaneRequest request) {
        ParkingLaneVO vo = laneService.update(id, request);
        log.info("更新车道成功: laneId={}", id);
        return R.ok(vo);
    }

    /**
     * 删除车道（软删除）。
     * <p>
     * 权限：parking:write
     */
    @DeleteMapping("/{id}")
    @RequirePermission("parking:write")
    public R<Void> delete(@PathVariable Long id) {
        laneService.delete(id);
        log.info("删除车道成功: laneId={}", id);
        return R.ok();
    }

    /**
     * 启用或停用车道。
     * <p>
     * 权限：parking:write
     *
     * @param id     车道 ID
     * @param body   包含 action 字段：ENABLED / DISABLED
     */
    @PostMapping("/{id}/status")
    @RequirePermission("parking:write")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        laneService.updateStatus(id, action);
        log.info("车道状态变更成功: laneId={}, action={}", id, action);
        return R.ok();
    }
}
