package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.service.MiniUserService;
import com.jushan.platform.modules.miniapp.vo.MiniParkingRecordVO;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序车主服务控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mini")
public class MiniUserController {

    private final MiniUserService miniUserService;

    public MiniUserController(MiniUserService miniUserService) {
        this.miniUserService = miniUserService;
    }

    @GetMapping("/parking-records")
    @RequirePermission("miniapp:view")
    public R<IPage<MiniParkingRecordVO>> listParkingRecords(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String tab) {
        return R.ok(miniUserService.listParkingRecords(current, size, tab));
    }

    @GetMapping("/parking-records/{id}")
    @RequirePermission("miniapp:view")
    public R<MiniParkingRecordVO> getParkingRecordDetail(@PathVariable Long id) {
        return R.ok(miniUserService.getParkingRecordDetail(id));
    }

    @GetMapping("/parking-records/plate/{plateNumber}")
    @RequirePermission("miniapp:view")
    public R<IPage<MiniParkingRecordVO>> listParkingRecordsByPlate(
            @PathVariable String plateNumber,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(miniUserService.listParkingRecordsByPlate(plateNumber, current, size));
    }

    @GetMapping("/current-sessions")
    @RequirePermission("miniapp:view")
    public R<List<MiniParkingRecordVO>> listCurrentSessions() {
        return R.ok(miniUserService.listCurrentSessions());
    }

    @GetMapping("/parking-lot/{parkingLotId}/remain")
    @RequirePermission("miniapp:view")
    public R<ParkingSpaceRemainVO> getParkingLotRemain(@PathVariable Long parkingLotId) {
        return R.ok(miniUserService.getParkingLotRemain(parkingLotId));
    }

    @GetMapping("/bound-plates")
    @RequirePermission("miniapp:view")
    public R<List<String>> listBoundPlates() {
        return R.ok(miniUserService.listBoundPlates());
    }
}
