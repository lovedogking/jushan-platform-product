package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.FixedSpaceCreateRequest;
import com.jushan.platform.modules.miniapp.dto.FixedSpaceRenewRequest;
import com.jushan.platform.modules.miniapp.service.FixedSpaceService;
import com.jushan.platform.modules.miniapp.vo.FixedSpaceVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 固定车位管理 Controller（Phase 1 A2）。
 * <p>
 * 提供固定车位绑定关系的独立管理 API。
 * 基于 {@code fixed_space_binding} 表。
 * <p>
 * 所有接口需要 {@code fixed:manage} 权限。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/fixed-spaces")
public class FixedSpaceController {

    private static final Logger log = LoggerFactory.getLogger(FixedSpaceController.class);

    private final FixedSpaceService fixedSpaceService;

    public FixedSpaceController(FixedSpaceService fixedSpaceService) {
        this.fixedSpaceService = fixedSpaceService;
    }

    // ==================== 列表查询 ====================

    /**
     * 分页查询固定车位绑定列表。
     *
     * @param parkingLotId 车场ID（可选）
     * @param zoneId       区域ID（可选）
     * @param spaceNo      车位号（可选，模糊匹配）
     * @param plateNumber  车牌号（可选，模糊匹配）
     * @param status       状态（可选）
     * @param page         页码（默认1）
     * @param size         每页大小（默认20）
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission("fixed:manage")
    public R<IPage<FixedSpaceVO>> pageList(
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String spaceNo,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<FixedSpaceVO> result = fixedSpaceService.pageList(
                parkingLotId, zoneId, spaceNo, plateNumber, status, page, size);
        return R.ok(result);
    }

    /**
     * 到期预警列表。
     * <p>
     * 查询 N 天内到期的固定车位绑定（默认 7 天），按到期时间升序。
     *
     * @param days 提前天数（可选，默认7）
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/expiring")
    @RequirePermission("fixed:manage")
    public R<IPage<FixedSpaceVO>> expiringList(
            @RequestParam(required = false) Integer days,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<FixedSpaceVO> result = fixedSpaceService.expiringList(days, page, size);
        return R.ok(result);
    }

    // ==================== 绑定 ====================

    /**
     * 绑定固定车位。
     * <p>
     * 自动查找或创建车辆记录，校验车位号和车辆不重复绑定。
     */
    @PostMapping
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "固定车位绑定", module = "fixed-space", operationType = "CREATE",
            operationObject = "固定车位", objectIdExpression = "#result?.data?.id")
    public R<FixedSpaceVO> create(@Valid @RequestBody FixedSpaceCreateRequest request) {
        FixedSpaceVO vo = fixedSpaceService.create(request);
        log.info("固定车位绑定完成: id={} spaceNo={} plate={}", vo.getId(), vo.getSpaceNo(), vo.getPlateNumber());
        return R.ok(vo);
    }

    // ==================== 续期 ====================

    /**
     * 固定车位续期。
     *
     * @param id      绑定记录ID
     * @param request 续期请求
     * @return 更新后的绑定记录视图
     */
    @PutMapping("/{id}/renew")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "固定车位续期", module = "fixed-space", operationType = "UPDATE",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> renew(@PathVariable Long id,
                                  @Valid @RequestBody FixedSpaceRenewRequest request) {
        FixedSpaceVO vo = fixedSpaceService.renew(id, request);
        log.info("固定车位续期完成: id={} spaceNo={} newEnd={}", id, vo.getSpaceNo(), vo.getValidEnd());
        return R.ok(vo);
    }

    // ==================== 注销 ====================

    /**
     * 固定车位注销。
     * <p>
     * 释放车位号，该车辆入场时按临停计费。
     *
     * @param id 绑定记录ID
     * @return 更新后的绑定记录视图
     */
    @PutMapping("/{id}/cancel")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "固定车位注销", module = "fixed-space", operationType = "UPDATE",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> cancel(@PathVariable Long id) {
        FixedSpaceVO vo = fixedSpaceService.cancel(id);
        log.info("固定车位注销完成: id={} spaceNo={}", id, vo.getSpaceNo());
        return R.ok(vo);
    }

    // ==================== 审核 ====================

    /**
     * 审核通过固定车位绑定。
     *
     * @param id 绑定记录ID
     * @return 更新后的绑定记录视图
     */
    @PutMapping("/{id}/approve")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "固定车位审核通过", module = "fixed-space", operationType = "UPDATE",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> approve(@PathVariable Long id) {
        FixedSpaceVO vo = fixedSpaceService.approve(id);
        log.info("固定车位审核通过完成: id={} spaceNo={}", id, vo.getSpaceNo());
        return R.ok(vo);
    }

    /**
     * 驳回固定车位绑定。
     *
     * @param id 绑定记录ID
     * @return 更新后的绑定记录视图
     */
    @PutMapping("/{id}/reject")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "固定车位审核驳回", module = "fixed-space", operationType = "UPDATE",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> reject(@PathVariable Long id) {
        FixedSpaceVO vo = fixedSpaceService.reject(id);
        log.info("固定车位审核驳回完成: id={} spaceNo={}", id, vo.getSpaceNo());
        return R.ok(vo);
    }
}
