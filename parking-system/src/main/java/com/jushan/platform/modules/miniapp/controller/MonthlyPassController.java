package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.MonthlyPassCreateRequest;
import com.jushan.platform.modules.miniapp.dto.MonthlyPassRenewRequest;
import com.jushan.platform.modules.miniapp.service.MonthlyPassService;
import com.jushan.platform.modules.miniapp.vo.MonthlyPassVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 月卡管理 Controller（任务包 3-1：基于 independent monthly_pass 实体）。
 * <p>
 * 所有接口需要 {@code monthly:manage} 权限。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/v1/monthly-passes")
public class MonthlyPassController {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassController.class);

    private final MonthlyPassService monthlyPassService;

    public MonthlyPassController(MonthlyPassService monthlyPassService) {
        this.monthlyPassService = monthlyPassService;
    }

    // ==================== 列表 ====================

    @GetMapping
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> pageList(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String passStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validEndFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validEndTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MonthlyPassVO> result = monthlyPassService.pageList(
                plateNumber, parkingLotId, passStatus, validEndFrom, validEndTo, page, size);
        return R.ok(result);
    }

    // ==================== 到期预警 ====================

    @GetMapping("/expiring")
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> expiringList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MonthlyPassVO> result = monthlyPassService.expiringList(page, size);
        return R.ok(result);
    }

    // ==================== 详情 ====================

    @GetMapping("/{id}")
    @RequirePermission("monthly:manage")
    public R<MonthlyPassVO> detail(@PathVariable Long id) {
        MonthlyPassVO vo = monthlyPassService.detail(id);
        return R.ok(vo);
    }

    // ==================== 录入 ====================

    @PostMapping
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡录入", module = "monthly-pass", operationType = "CREATE",
            operationObject = "月卡", objectIdExpression = "#result?.data?.id")
    public R<MonthlyPassVO> create(@Valid @RequestBody MonthlyPassCreateRequest request) {
        MonthlyPassVO vo = monthlyPassService.create(request);
        log.info("月卡录入完成: id={} plate={}", vo.getId(), vo.getPlateNumber());
        return R.ok(vo);
    }

    // ==================== 续期 ====================

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

    // ==================== 注销 ====================

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
