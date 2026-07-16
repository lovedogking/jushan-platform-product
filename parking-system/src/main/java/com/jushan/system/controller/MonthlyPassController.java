package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.service.MonthlyPassService;
import com.jushan.system.vo.MonthlyPassVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 月卡管理 Controller（Phase 1 A1）。
 * <p>
 * 提供独立的月卡管理 API，不与普通车辆管理混淆。
 * 基于 {@code sys_vehicle} 表（{@code vehicleType=MONTHLY}）。
 * <p>
 * 所有接口需要 {@code monthly:manage} 权限。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/monthly-passes")
public class MonthlyPassController {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassController.class);

    private final MonthlyPassService monthlyPassService;

    public MonthlyPassController(MonthlyPassService monthlyPassService) {
        this.monthlyPassService = monthlyPassService;
    }

    // ==================== 列表查询 ====================

    /**
     * 分页查询月卡列表。
     *
     * @param plateNumber  车牌号（可选，模糊匹配）
     * @param parkingLotId 车场ID（可选）
     * @param status       状态（可选）
     * @param validEndFrom 有效期起（可选）
     * @param validEndTo   有效期止（可选）
     * @param page         页码（默认1）
     * @param size         每页大小（默认20）
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> pageList(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validEndFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validEndTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MonthlyPassVO> result = monthlyPassService.pageList(
                plateNumber, parkingLotId, status, validEndFrom, validEndTo, page, size);
        return R.ok(result);
    }

    /**
     * 到期预警列表。
     * <p>
     * 查询 N 天内到期的月卡（默认 7 天），按到期时间升序。
     *
     * @param days 提前天数（可选，默认7）
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/expiring")
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> expiringList(
            @RequestParam(required = false) Integer days,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MonthlyPassVO> result = monthlyPassService.expiringList(days, page, size);
        return R.ok(result);
    }

    // ==================== 月卡登记 ====================

    /**
     * 登记月卡。
     */
    @PostMapping
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡登记", module = "monthly-pass", operationType = "CREATE",
            operationObject = "月卡", objectIdExpression = "#result?.data?.id")
    public R<MonthlyPassVO> create(@Valid @RequestBody MonthlyPassCreateRequest request) {
        MonthlyPassVO vo = monthlyPassService.create(request);
        log.info("月卡登记完成: id={} plate={}", vo.getId(), vo.getPlateNumber());
        return R.ok(vo);
    }

    // ==================== 月卡续期 ====================

    /**
     * 月卡续期。
     * <p>
     * 管理员确认收款后调用，直接延长有效期并记录续费日志。
     *
     * @param id      车辆ID
     * @param request 续期请求
     * @return 更新后的月卡视图
     */
    @PutMapping("/{id}/renew")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡续期", module = "monthly-pass", operationType = "UPDATE",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> renew(@PathVariable Long id,
                                   @Valid @RequestBody MonthlyPassRenewRequest request) {
        MonthlyPassVO vo = monthlyPassService.renew(id, request);
        log.info("月卡续期完成: id={} plate={} newEnd={}", id, vo.getPlateNumber(), vo.getValidEndDate());
        return R.ok(vo);
    }

    // ==================== 月卡注销 ====================

    /**
     * 月卡注销。
     * <p>
     * 将车辆类型改为 FREE，入场时按临停计费。
     *
     * @param id 车辆ID
     * @return 更新后的月卡视图
     */
    @PutMapping("/{id}/cancel")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡注销", module = "monthly-pass", operationType = "UPDATE",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> cancel(@PathVariable Long id) {
        MonthlyPassVO vo = monthlyPassService.cancel(id);
        log.info("月卡注销完成: id={} plate={}", id, vo.getPlateNumber());
        return R.ok(vo);
    }
}
