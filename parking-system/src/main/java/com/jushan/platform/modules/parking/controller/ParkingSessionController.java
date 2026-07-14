package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.parking.vo.ParkingSessionVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在场车辆管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parking-sessions")
public class ParkingSessionController {

    private final ParkingSessionService parkingSessionService;

    public ParkingSessionController(ParkingSessionService parkingSessionService) {
        this.parkingSessionService = parkingSessionService;
    }

    @PostMapping("/entry")
    @RequirePermission("booth:operate")
    public R<ParkingSessionVO> entry(@Valid @RequestBody ParkingSessionEntryCmd cmd) {
        ParkingSessionVO vo = parkingSessionService.entry(cmd);
        log.info("车辆入场: sessionId={}, plate={}, lotId={}", vo.getId(), cmd.getPlateNumber(), cmd.getParkingLotId());
        return R.ok(vo);
    }

    @PostMapping("/exit")
    @RequirePermission("booth:operate")
    public R<ParkingSessionVO> exit(@Valid @RequestBody ParkingSessionExitCmd cmd) {
        ParkingSessionVO vo = parkingSessionService.exit(cmd);
        log.info("车辆出场: sessionId={}, fee={}", vo.getId(), cmd.getFeeAmount());
        return R.ok(vo);
    }

    @GetMapping("/{id}")
    @RequirePermission("booth:view")
    public R<ParkingSessionVO> detail(@PathVariable Long id) {
        return R.ok(parkingSessionService.detail(id));
    }

    @GetMapping
    @RequirePermission("booth:view")
    public R<IPage<ParkingSessionVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String status) {
        IPage<ParkingSessionVO> result = parkingSessionService.pageList(
                new Page<ParkingSession>(current, size), parkingLotId, plateNumber, status);
        return R.ok(result);
    }

    @GetMapping("/in/{parkingLotId}")
    @RequirePermission("booth:view")
    public R<List<ParkingSessionVO>> listInByParkingLotId(@PathVariable Long parkingLotId) {
        return R.ok(parkingSessionService.listInByParkingLotId(parkingLotId));
    }

    @GetMapping("/in/plate/{plateNumber}")
    @RequirePermission("booth:view")
    public R<ParkingSessionVO> getInByPlateNumber(@PathVariable String plateNumber) {
        return R.ok(parkingSessionService.getInByPlateNumber(plateNumber));
    }

    @GetMapping("/in/{parkingLotId}/count")
    @RequirePermission("booth:view")
    public R<Long> countInByParkingLotId(@PathVariable Long parkingLotId) {
        return R.ok(parkingSessionService.countInByParkingLotId(parkingLotId));
    }
}
