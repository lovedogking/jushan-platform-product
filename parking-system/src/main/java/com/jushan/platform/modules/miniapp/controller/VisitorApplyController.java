package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.VisitorApplyAuditCmd;
import com.jushan.platform.modules.miniapp.dto.VisitorApplyCreateCmd;
import com.jushan.platform.modules.miniapp.entity.VisitorApply;
import com.jushan.platform.modules.miniapp.service.VisitorApplyService;
import com.jushan.platform.modules.miniapp.vo.VisitorApplyVO;
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
 * 访客预约申请控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/visitor-applies")
public class VisitorApplyController {

    private final VisitorApplyService visitorApplyService;

    public VisitorApplyController(VisitorApplyService visitorApplyService) {
        this.visitorApplyService = visitorApplyService;
    }

    @PostMapping
    @RequirePermission("miniapp:operate")
    public R<VisitorApplyVO> submit(@Valid @RequestBody VisitorApplyCreateCmd cmd) {
        VisitorApplyVO vo = visitorApplyService.submit(cmd);
        log.info("访客预约申请提交: applyId={}, plate={}, visitorName={}",
                vo.getId(), cmd.getPlateNumber(), cmd.getVisitorName());
        return R.ok(vo);
    }

    @PutMapping("/{id}/audit")
    @RequirePermission("vehicle:update")
    public R<VisitorApplyVO> audit(@PathVariable Long id, @Valid @RequestBody VisitorApplyAuditCmd cmd) {
        VisitorApplyVO vo = visitorApplyService.audit(id, cmd);
        log.info("访客预约审核: applyId={}, status={}", id, cmd.getApplyStatus());
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("miniapp:operate")
    public R<VisitorApplyVO> cancel(@PathVariable Long id) {
        VisitorApplyVO vo = visitorApplyService.cancel(id);
        log.info("访客预约取消: applyId={}", id);
        return R.ok(vo);
    }

    @GetMapping("/{id}")
    @RequirePermission("miniapp:view")
    public R<VisitorApplyVO> detail(@PathVariable Long id) {
        return R.ok(visitorApplyService.detail(id));
    }

    @GetMapping
    @RequirePermission("miniapp:view")
    public R<IPage<VisitorApplyVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String applyStatus,
            @RequestParam(required = false) String plateNumber) {
        IPage<VisitorApplyVO> result = visitorApplyService.pageList(
                new Page<VisitorApply>(current, size), parkingLotId, applyStatus, plateNumber);
        return R.ok(result);
    }

    @GetMapping("/lot/{parkingLotId}")
    @RequirePermission("miniapp:view")
    public R<List<VisitorApplyVO>> listByParkingLotId(@PathVariable Long parkingLotId) {
        return R.ok(visitorApplyService.listByParkingLotId(parkingLotId));
    }

    @GetMapping("/my")
    @RequirePermission("miniapp:view")
    public R<List<VisitorApplyVO>> listMyApplies() {
        Long applicantId = com.jushan.common.auth.TenantContext.getUserId();
        return R.ok(visitorApplyService.listByApplicantId(applicantId));
    }
}
