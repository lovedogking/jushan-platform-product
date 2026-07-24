package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.entity.MonthlyPass;
import com.jushan.platform.modules.miniapp.mapper.MonthlyPassMapper;
import com.jushan.platform.modules.miniapp.vo.MonthlyPassVO;
import com.jushan.platform.modules.miniapp.service.MonthlyPassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 月卡审核管理接口。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Tag(name = "月卡审核管理")
@RestController
@RequestMapping("/api/v1/admin/monthly-pass-audit")
public class MonthlyPassAuditController {

    private final MonthlyPassService monthlyPassService;
    private final MonthlyPassMapper monthlyPassMapper;

    public MonthlyPassAuditController(MonthlyPassService monthlyPassService,
                                       MonthlyPassMapper monthlyPassMapper) {
        this.monthlyPassService = monthlyPassService;
        this.monthlyPassMapper = monthlyPassMapper;
    }

    @Operation(summary = "分页查询待审核月卡")
    @GetMapping("/pending")
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> pendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long parkingLotId) {
        IPage<MonthlyPassVO> result = monthlyPassService.pageAuditPending(page, size, parkingLotId);
        return R.ok(result);
    }

    @Operation(summary = "通过月卡审核")
    @PostMapping("/{id}/approve")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "通过月卡审核", module = "monthly-pass", operationType = "AUDIT",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> approve(@PathVariable Long id,
                                     @RequestParam(required = false) String remark) {
        MonthlyPass entity = monthlyPassMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        if (!MonthlyPass.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该月卡不在待审核状态");
        }
        entity.setReviewStatus(MonthlyPass.REVIEW_APPROVED);
        entity.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        entity.setReviewRemark(remark);
        entity.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(entity);
        return R.ok(monthlyPassService.toVO(entity));
    }

    @Operation(summary = "驳回月卡审核")
    @PostMapping("/{id}/reject")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "驳回月卡审核", module = "monthly-pass", operationType = "AUDIT",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> reject(@PathVariable Long id,
                                    @RequestParam(required = false) String remark) {
        MonthlyPass entity = monthlyPassMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        if (!MonthlyPass.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该月卡不在待审核状态");
        }
        entity.setReviewStatus(MonthlyPass.REVIEW_REJECTED);
        entity.setPassStatus(MonthlyPass.STATUS_CANCELLED);
        entity.setReviewRemark(remark);
        entity.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(entity);
        return R.ok(monthlyPassService.toVO(entity));
    }
}
