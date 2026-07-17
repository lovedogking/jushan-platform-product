package com.jushan.system.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.common.R;
import com.jushan.system.dto.FeePreviewRequest;
import com.jushan.system.service.ParkingFeeService;
import com.jushan.system.vo.ParkingFeeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗亭停车费用查询控制器（P007）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/booth/parking/fee")
@Validated
public class BoothParkingFeeController {

    private static final Logger log = LoggerFactory.getLogger(BoothParkingFeeController.class);

    private final ParkingFeeService parkingFeeService;

    public BoothParkingFeeController(ParkingFeeService parkingFeeService) {
        this.parkingFeeService = parkingFeeService;
    }

    /**
     * 按停车场 + 车牌查询当前停车费用。
     *
     * @param plate        车牌号
     * @param parkingLotId 停车场 ID
     * @return 费用信息
     */
    @GetMapping
    @RequirePermission("record:read")
    public R<ParkingFeeVO> queryByPlate(
            @RequestParam("plate") @NotBlank(message = "车牌号不能为空") String plate,
            @RequestParam("parkingLotId") @NotNull(message = "停车场 ID 不能为空") Long parkingLotId) {
        ParkingFeeVO vo = parkingFeeService.queryByPlateForBooth(plate, parkingLotId);
        log.info("岗亭查询停车费用: plate={}, parkingLotId={}, recordId={}, feeCents={}",
                vo.getPlate(), vo.getParkingLotId(), vo.getRecordId(), vo.getFeeCents());
        return R.ok(vo);
    }

    /**
     * 按停车记录 ID 查询费用。
     *
     * @param recordId 停车记录 ID
     * @return 费用信息
     */
    @GetMapping("/{recordId}")
    @RequirePermission("record:read")
    public R<ParkingFeeVO> queryByRecordId(@PathVariable("recordId") Long recordId) {
        ParkingFeeVO vo = parkingFeeService.queryByRecordIdForBooth(recordId);
        log.info("岗亭查询停车费用: recordId={}, feeCents={}", vo.getRecordId(), vo.getFeeCents());
        return R.ok(vo);
    }

    /**
     * 结算预览 / 重新计费。
     *
     * @param request 预览请求
     * @return 费用信息
     */
    @PostMapping("/preview")
    @RequirePermission("record:read")
    public R<ParkingFeeVO> preview(@Valid @RequestBody FeePreviewRequest request) {
        ParkingFeeVO vo = parkingFeeService.previewForBooth(request);
        log.info("岗亭预览停车费用: recordId={}, previewExitTime={}, feeCents={}",
                vo.getRecordId(), vo.getPreviewExitTime(), vo.getFeeCents());
        return R.ok(vo);
    }
}
