package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.RequireRole;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 人工补录通行记录接口（GAP-08）。
 * <p>
 * 岗亭管理员或运营端手动补录车辆入场记录，
 * 写入 parking_session（entry_trigger=manual_entry，记录操作人）。
 *
 * @author Jushan Platform
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/booth/manual-entry")
@Tag(name = "人工补录", description = "手动补录车辆入场记录（GAP-08）")
@RequireRole({"platform", "tenant", "booth"}) // GAP-08: 三种角色均可（V1.4）
public class BoothManualEntryController {

    private final ParkingSessionService parkingSessionService;

    public BoothManualEntryController(ParkingSessionService parkingSessionService) {
        this.parkingSessionService = parkingSessionService;
    }

    @PostMapping
    @Operation(summary = "人工补录车辆入场记录")
    public R<Long> manualEntry(@Valid @RequestBody ManualEntryRequest request) {
        Long operatorId = TenantContext.getUserId();

        ParkingSessionEntryCmd cmd = new ParkingSessionEntryCmd();
        cmd.setParkingLotId(request.getParkingLotId());
        cmd.setLaneId(request.getLaneId());
        cmd.setPlateNumber(request.getPlateNumber().toUpperCase());
        cmd.setVehicleType("TEMP");
        cmd.setEntryTime(request.getEntryTime());
        cmd.setEntryOperator(operatorId);
        cmd.setEntryTrigger(ParkingSession.TRIGGER_MANUAL_ENTRY);
        cmd.setRemark("人工补录: " + (request.getRemark() != null ? request.getRemark() : ""));

        var session = parkingSessionService.entry(cmd);

        log.info("人工补录入场记录: plate={}, lotId={}, laneId={}, operatorId={}, sessionId={}",
                request.getPlateNumber(), request.getParkingLotId(), request.getLaneId(),
                operatorId, session.getId());

        return R.ok(session.getId());
    }

    @Data
    public static class ManualEntryRequest {
        @NotNull(message = "停车场ID不能为空")
        private Long parkingLotId;

        private Long laneId;

        @NotBlank(message = "车牌号不能为空")
        private String plateNumber;

        @NotNull(message = "入场时间不能为空")
        private LocalDateTime entryTime;

        private String remark;
    }
}
