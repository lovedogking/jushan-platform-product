package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.booth.dto.FeeReductionCmd;
import com.jushan.platform.modules.booth.service.FeeReductionService;
import com.jushan.platform.modules.booth.vo.FeeReductionVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 费用减免控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/booth/charge")
public class BoothFeeReductionController {

    private final FeeReductionService feeReductionService;

    public BoothFeeReductionController(FeeReductionService feeReductionService) {
        this.feeReductionService = feeReductionService;
    }

    @PostMapping("/fee-reduction")
    @RequirePermission("fee:reduce")
    public R<FeeReductionVO> applyFeeReduction(@Valid @RequestBody FeeReductionCmd cmd) {
        FeeReductionVO vo = feeReductionService.apply(cmd);
        log.info("费用减免完成: sessionId={}, reducedFeeCents={}", cmd.getSessionId(), cmd.getReducedFeeCents());
        return R.ok(vo);
    }
}
