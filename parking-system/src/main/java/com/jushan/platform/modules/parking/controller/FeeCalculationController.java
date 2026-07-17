package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.FeeCalculateCmd;
import com.jushan.platform.modules.parking.service.FeeCalculationService;
import com.jushan.platform.modules.parking.vo.FeeCalculateResultVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 费用计算控制器。
 * <p>
 * 提供停车费用试算接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fee")
@Tag(name = "二期计费体系候选(冻结)")
public class FeeCalculationController {

    private final FeeCalculationService feeCalculationService;

    public FeeCalculationController(FeeCalculationService feeCalculationService) {
        this.feeCalculationService = feeCalculationService;
    }

    /**
     * 停车费用试算。
     */
    @PostMapping("/calculate")
    @RequirePermission("fee:read")
    public R<FeeCalculateResultVO> calculate(@Valid @RequestBody FeeCalculateCmd cmd) {
        FeeCalculateResultVO result = feeCalculationService.calculate(
                cmd.getLotId(), cmd.getZoneId(), cmd.getPlateNumber(), cmd.getVehicleType(),
                cmd.getEntryTime(), cmd.getExitTime());
        log.info("费用试算: lotId={}, plateNumber={}, amount={}分",
                cmd.getLotId(), cmd.getPlateNumber(), result.getPayableAmount());
        return R.ok(result);
    }
}
