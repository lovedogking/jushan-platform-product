package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import com.jushan.system.vo.FixedSpaceVO;
import com.jushan.system.service.FixedSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "固定车位审核管理")
@RestController
@RequestMapping("/api/v1/admin/fixed-space-audit")
public class FixedSpaceAuditController {

    private final FixedSpaceService fixedSpaceService;
    private final FixedSpaceBindingMapper fixedSpaceBindingMapper;

    public FixedSpaceAuditController(FixedSpaceService fixedSpaceService,
                                     FixedSpaceBindingMapper fixedSpaceBindingMapper) {
        this.fixedSpaceService = fixedSpaceService;
        this.fixedSpaceBindingMapper = fixedSpaceBindingMapper;
    }

    @Operation(summary = "分页查询待审核固定车位")
    @GetMapping("/pending")
    @RequirePermission("fixed:manage")
    public R<IPage<FixedSpaceVO>> pendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long parkingLotId) {
        IPage<FixedSpaceVO> result = fixedSpaceService.pageAuditPending(page, size, parkingLotId);
        return R.ok(result);
    }

    @Operation(summary = "通过固定车位审核")
    @PostMapping("/{id}/approve")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "通过固定车位审核", module = "fixed-space", operationType = "AUDIT",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> approve(@PathVariable Long id,
                                    @RequestParam(required = false) String remark) {
        FixedSpaceBinding entity = fixedSpaceBindingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "固定车位绑定不存在");
        }
        if (!FixedSpaceBinding.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该固定车位不在待审核状态");
        }
        entity.setReviewStatus(FixedSpaceBinding.REVIEW_APPROVED);
        entity.setStatus(FixedSpaceBinding.STATUS_ACTIVE);
        entity.setReviewRemark(remark);
        // FixedSpaceBinding 不需要 updatedAt
        fixedSpaceBindingMapper.updateById(entity);
        return R.ok(fixedSpaceService.toVO(entity));
    }

    @Operation(summary = "驳回固定车位审核")
    @PostMapping("/{id}/reject")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "驳回固定车位审核", module = "fixed-space", operationType = "AUDIT",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> reject(@PathVariable Long id,
                                   @RequestParam(required = false) String remark) {
        FixedSpaceBinding entity = fixedSpaceBindingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "固定车位绑定不存在");
        }
        if (!FixedSpaceBinding.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该固定车位不在待审核状态");
        }
        entity.setReviewStatus(FixedSpaceBinding.REVIEW_REJECTED);
        entity.setStatus(FixedSpaceBinding.STATUS_DISABLED);
        entity.setReviewRemark(remark);
        fixedSpaceBindingMapper.updateById(entity);
        return R.ok(fixedSpaceService.toVO(entity));
    }
}
