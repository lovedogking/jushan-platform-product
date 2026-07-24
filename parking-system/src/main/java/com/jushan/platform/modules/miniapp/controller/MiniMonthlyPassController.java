package com.jushan.platform.modules.miniapp.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.MiniMonthlyPassApplyRequest;
import com.jushan.platform.modules.miniapp.dto.MiniMonthlyPassRenewRequest;
import com.jushan.platform.modules.miniapp.service.MiniMonthlyPassService;
import com.jushan.platform.modules.miniapp.vo.MonthlyPassVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 小程序端月卡管理 Controller（Phase 3）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/mini/monthly-passes")
public class MiniMonthlyPassController {

    private final MiniMonthlyPassService miniMonthlyPassService;

    public MiniMonthlyPassController(MiniMonthlyPassService miniMonthlyPassService) {
        this.miniMonthlyPassService = miniMonthlyPassService;
    }

    @PostMapping
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> apply(@Valid @RequestBody MiniMonthlyPassApplyRequest request) {
        return R.ok(miniMonthlyPassService.apply(request));
    }

    @GetMapping
    @RequirePermission("miniapp:view")
    public R<List<MonthlyPassVO>> listMyPasses() {
        return R.ok(miniMonthlyPassService.listMyPasses());
    }

    @GetMapping("/{id}")
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> detail(@PathVariable Long id) {
        return R.ok(miniMonthlyPassService.detail(id));
    }

    @PostMapping("/{id}/pay")
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> pay(@PathVariable Long id) {
        return R.ok(miniMonthlyPassService.pay(id));
    }

    @PostMapping("/{id}/renew")
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> renew(@PathVariable Long id,
                                  @Valid @RequestBody MiniMonthlyPassRenewRequest request) {
        return R.ok(miniMonthlyPassService.renew(id, request));
    }
}
