package com.jushan.system.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.system.dto.CreateParkingLotRequest;
import com.jushan.system.dto.ParkingLotCapacityRequest;
import com.jushan.system.dto.ParkingLotStatusRequest;
import com.jushan.system.dto.UpdateParkingLotRequest;
import com.jushan.system.service.ParkingLotService;
import com.jushan.system.service.ReadinessCheckService;
import com.jushan.system.vo.ParkingLotReadinessVO;
import com.jushan.system.vo.ParkingLotVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 停车场管理控制器（运营端）。
 * <p>
 * 客户管理员和停车场管理员通过此接口管理本租户内的停车场。
 * 所有操作从当前登录会话推导租户范围，不信任前端传入的 tenantId。
 * <p>
 * <strong>权限区分</strong>：
 * <ul>
 *   <li>{@code parking:read} — 查看停车场列表和详情（管理员、财务、运维、岗亭均可用）</li>
 *   <li>{@code parking:write} — 创建/编辑停车场、修改容量（仅客户管理员）</li>
 *   <li>{@code parking:disable} — 启用/停用停车场（仅超级管理员和客户管理员，停车场管理员无权）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/parking-lots")
public class ParkingLotController {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotController.class);

    private final ParkingLotService parkingLotService;
    private final ReadinessCheckService readinessCheckService;

    public ParkingLotController(ParkingLotService parkingLotService,
                                 ReadinessCheckService readinessCheckService) {
        this.parkingLotService = parkingLotService;
        this.readinessCheckService = readinessCheckService;
    }

    /**
     * 分页查询本租户停车场列表。
     * <p>
     * 权限：parking:read
     *
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @param status 状态筛选（可选：ENABLED / DISABLED）
     */
    @GetMapping
    @RequirePermission("parking:read")
    public R<IPage<ParkingLotVO>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String status) {
        IPage<ParkingLotVO> result = parkingLotService.list(page, size, status);
        return R.ok(result);
    }

    /**
     * 查询停车场详情。
     * <p>
     * 权限：parking:read
     */
    @GetMapping("/{id}")
    @RequirePermission("parking:read")
    public R<ParkingLotVO> detail(@PathVariable Long id) {
        ParkingLotVO vo = parkingLotService.get(id);
        return R.ok(vo);
    }

    /**
     * 创建停车场。
     * <p>
     * 权限：parking:write（仅客户管理员）
     */
    @PostMapping
    @RequirePermission("parking:write")
    public R<ParkingLotVO> create(@Valid @RequestBody CreateParkingLotRequest request) {
        ParkingLotVO vo = parkingLotService.create(request);
        log.info("创建停车场成功: parkingLotId={}, name={}", vo.getId(), vo.getName());
        return R.ok(vo);
    }

    /**
     * 更新停车场基础信息（不含容量和状态）。
     * <p>
     * 权限：parking:write
     */
    @PutMapping("/{id}")
    @RequirePermission("parking:write")
    public R<ParkingLotVO> update(@PathVariable Long id, @Valid @RequestBody UpdateParkingLotRequest request) {
        ParkingLotVO vo = parkingLotService.update(id, request);
        log.info("更新停车场成功: parkingLotId={}", id);
        return R.ok(vo);
    }

    /**
     * 启用或停用停车场。
     * <p>
     * 权限：parking:disable（仅超级管理员和客户管理员，停车场管理员<b>无权</b>）
     * <p>
     * 停用时必须填写原因。停用时可选保留范围（是否允许入场/缴费/出场/自动开闸/仅限制后台配置）。
     *
     * @param id      停车场 ID
     * @param request 状态变更请求（action: ENABLED / DISABLED）
     */
    @PostMapping("/{id}/status")
    @RequirePermission("parking:disable")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ParkingLotStatusRequest request) {
        parkingLotService.updateStatus(id, request);
        log.info("停车场状态变更成功: parkingLotId={}, action={}", id, request.getAction());
        return R.ok();
    }

    /**
     * 修改总车位数或人工修正剩余车位数。
     * <p>
     * 权限：parking:write（客户管理员和停车场管理员均可）
     * <p>
     * 修改原因必填，修改前后数值自动记录到审计表。
     *
     * @param id      停车场 ID
     * @param request 容量变更请求（fieldName: total_spaces / remaining_spaces, value, reason）
     */
    @PostMapping("/{id}/capacity")
    @RequirePermission("parking:write")
    public R<Void> updateCapacity(@PathVariable Long id, @Valid @RequestBody ParkingLotCapacityRequest request) {
        parkingLotService.updateCapacity(id, request);
        log.info("停车场容量变更成功: parkingLotId={}, field={}, value={}",
                id, request.getFieldName(), request.getValue());
        return R.ok();
    }

    // ==================== 就绪检查（T22） ====================

    /**
     * 停车场就绪检查。
     * <p>
     * 在启用停车场前检查车道配置、设备绑定、执行相机、容量等关键项。
     * 返回 BLOCKER（阻塞启用）和 WARNING（提示但不阻塞）两级结果。
     * 未实现模块（收费规则、支付、设备在线）以 {@code implemented=false} 明确标记。
     * <p>
     * 权限：parking:read
     *
     * @param id 停车场 ID
     * @return 就绪检查结果（含详细检查项列表）
     */
    @GetMapping("/{id}/readiness")
    @RequirePermission("parking:read")
    public R<ParkingLotReadinessVO> checkReadiness(@PathVariable Long id) {
        ParkingLotReadinessVO vo = readinessCheckService.check(id);
        log.info("就绪检查完成: parkingLotId={}, ready={}, blockers={}, warnings={}",
                id, vo.isReady(), vo.getBlockerCount(), vo.getWarningCount());
        return R.ok(vo);
    }
}
