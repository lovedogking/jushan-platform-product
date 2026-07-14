package com.jushan.platform.modules.booth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.booth.dto.ShiftCloseCmd;
import com.jushan.platform.modules.booth.dto.ShiftStartCmd;
import com.jushan.platform.modules.booth.entity.ShiftRecord;
import com.jushan.platform.modules.booth.service.ShiftRecordService;
import com.jushan.platform.modules.booth.vo.ShiftRecordVO;
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
 * 交接班管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/shift-records")
public class ShiftRecordController {

    private final ShiftRecordService shiftRecordService;

    public ShiftRecordController(ShiftRecordService shiftRecordService) {
        this.shiftRecordService = shiftRecordService;
    }

    @PostMapping("/start")
    @RequirePermission("booth:operate")
    public R<ShiftRecordVO> startShift(@Valid @RequestBody ShiftStartCmd cmd) {
        ShiftRecordVO vo = shiftRecordService.startShift(cmd);
        log.info("交接班开班: shiftId={}, operatorId={}, lotId={}",
                vo.getId(), vo.getOperatorId(), cmd.getParkingLotId());
        return R.ok(vo);
    }

    @PostMapping("/close")
    @RequirePermission("booth:operate")
    public R<ShiftRecordVO> closeShift(@Valid @RequestBody ShiftCloseCmd cmd) {
        ShiftRecordVO vo = shiftRecordService.closeShift(cmd);
        log.info("交接班交班: shiftId={}", cmd.getShiftId());
        return R.ok(vo);
    }

    @GetMapping("/current")
    @RequirePermission("booth:view")
    public R<ShiftRecordVO> getCurrentShift() {
        return R.ok(shiftRecordService.getCurrentShift());
    }

    @GetMapping("/{id}")
    @RequirePermission("booth:view")
    public R<ShiftRecordVO> detail(@PathVariable Long id) {
        return R.ok(shiftRecordService.detail(id));
    }

    @GetMapping
    @RequirePermission("booth:view")
    public R<IPage<ShiftRecordVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String status) {
        IPage<ShiftRecordVO> result = shiftRecordService.pageList(
                new Page<ShiftRecord>(current, size), parkingLotId, status);
        return R.ok(result);
    }

    @GetMapping("/lot/{parkingLotId}")
    @RequirePermission("booth:view")
    public R<List<ShiftRecordVO>> listByParkingLotId(@PathVariable Long parkingLotId) {
        return R.ok(shiftRecordService.listByParkingLotId(parkingLotId));
    }
}
