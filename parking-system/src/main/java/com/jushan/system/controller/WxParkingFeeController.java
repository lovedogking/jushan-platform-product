package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.system.dto.FeePreviewRequest;
import com.jushan.system.service.ParkingFeeService;
import com.jushan.system.vo.ParkingFeeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * 小程序停车费用查询控制器（P007）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/wx/parking/fee")
@Validated
public class WxParkingFeeController {

    private static final Logger log = LoggerFactory.getLogger(WxParkingFeeController.class);

    private final ParkingFeeService parkingFeeService;

    public WxParkingFeeController(ParkingFeeService parkingFeeService) {
        this.parkingFeeService = parkingFeeService;
    }

    /**
     * 按车牌查询当前停车费用。
     *
     * @param plate 车牌号
     * @return 费用信息
     */
    @GetMapping
    public R<ParkingFeeVO> queryByPlate(@RequestParam("plate") @NotBlank(message = "车牌号不能为空") String plate) {
        ParkingFeeVO vo = parkingFeeService.queryByPlateForWx(plate);
        log.info("小程序查询停车费用: plate={}, recordId={}, feeCents={}",
                vo.getPlate(), vo.getRecordId(), vo.getFeeCents());
        return R.ok(vo);
    }

    /**
     * 按停车记录 ID 查询费用。
     *
     * @param recordId 停车记录 ID
     * @return 费用信息
     */
    @GetMapping("/{recordId}")
    public R<ParkingFeeVO> queryByRecordId(@PathVariable("recordId") Long recordId) {
        ParkingFeeVO vo = parkingFeeService.queryByRecordIdForWx(recordId);
        log.info("小程序查询停车费用: recordId={}, feeCents={}", vo.getRecordId(), vo.getFeeCents());
        return R.ok(vo);
    }

    /**
     * 结算预览 / 重新计费。
     *
     * @param request 预览请求
     * @return 费用信息
     */
    @PostMapping("/preview")
    public R<ParkingFeeVO> preview(@Valid @RequestBody FeePreviewRequest request) {
        ParkingFeeVO vo = parkingFeeService.previewForWx(request);
        log.info("小程序预览停车费用: recordId={}, previewExitTime={}, feeCents={}",
                vo.getRecordId(), vo.getPreviewExitTime(), vo.getFeeCents());
        return R.ok(vo);
    }
}
