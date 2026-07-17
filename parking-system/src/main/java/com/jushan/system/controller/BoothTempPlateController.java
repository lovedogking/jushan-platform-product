package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.TempPlateEntryRequest;
import com.jushan.system.dto.TempPlateExitRequest;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.service.ExitResult;
import com.jushan.system.service.TempPlateNumberGenerator;
import com.jushan.system.service.TempPlateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 岗亭端临时车牌处理接口（任务包 3-4）。
 * <p>
 * 提供岗亭端的无牌车处理能力：
 * <ul>
 *   <li>建议临时车牌号（预览）</li>
 *   <li>手动无牌车入场（创建记录 + 开闸）</li>
 *   <li>手动无牌车出场匹配计费</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/booth/temp-plate")
public class BoothTempPlateController {

    private final TempPlateService tempPlateService;
    private final TempPlateNumberGenerator numberGenerator;

    public BoothTempPlateController(TempPlateService tempPlateService,
                                      TempPlateNumberGenerator numberGenerator) {
        this.tempPlateService = tempPlateService;
        this.numberGenerator = numberGenerator;
    }

    /**
     * 生成建议临时车牌号（预览，不消耗序号）。
     */
    @GetMapping("/suggest")
    @RequirePermission("booth:monitor")
    public R<Map<String, String>> suggest(@RequestParam Long parkingLotId) {
        String suggested = numberGenerator.suggest(parkingLotId);
        return R.ok(Map.of("tempPlate", suggested));
    }

    /**
     * 岗亭手动无牌车入场。
     * <p>
     * 创建在场记录 + 预订单 + 开闸。若 tempPlate 为空则自动生成（消耗序号）。
     */
    @PostMapping("/entry")
    @RequirePermission("booth:monitor")
    @BusinessLog(value = "岗亭手动无牌车入场", module = "temp-plate", operationType = "CREATE")
    public R<Map<String, Object>> manualEntry(
            @RequestBody @Valid TempPlateEntryRequest request) {
        Long boothUserId = TenantContext.getUserId();

        String tempPlate = request.getTempPlate();
        if (tempPlate == null || tempPlate.isBlank()) {
            tempPlate = numberGenerator.generate(request.getParkingLotId());
        }

        ParkingRecord record = tempPlateService.manualEntry(
                request.getParkingLotId(), request.getLaneId(),
                tempPlate, boothUserId);

        return R.ok(Map.of(
                "recordId", record.getId(),
                "tempPlate", tempPlate,
                "entryTime", record.getEntryTime().toString()));
    }

    /**
     * 岗亭手动无牌车出场匹配计费。
     * <p>
     * 通过临时车牌匹配在场记录 → 计费 → 现金支付 → 开闸放行。
     */
    @PostMapping("/exit-match")
    @RequirePermission("booth:monitor")
    @BusinessLog(value = "岗亭手动无牌车出场", module = "temp-plate", operationType = "UPDATE")
    public R<Map<String, Object>> exitMatch(
            @RequestBody @Valid TempPlateExitRequest request) {
        Long boothUserId = TenantContext.getUserId();

        ExitResult result = tempPlateService.handleExitMatch(
                request.getTempPlate(), request.getParkingLotId(),
                request.getLaneId(), boothUserId);

        return R.ok(Map.of(
                "exitRecordId", result.getExitRecordId(),
                "orderId", result.getOrderId(),
                "feeCents", result.getFeeCents(),
                "message", result.getReason()));
    }
}
