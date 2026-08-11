package com.jushan.platform.modules.h5.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.event.PlateStandardizer;
import com.jushan.platform.modules.h5.dto.H5FeeQueryRequest;
import com.jushan.platform.modules.h5.vo.H5FeeVO;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import com.jushan.platform.modules.parking.service.FeeCalculationService;
import com.jushan.platform.modules.parking.service.ParkingOrderService;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * H5 免登录查费控制器。
 * <p>
 * 按车牌号查询在场停车记录和费用，不要求登录态。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/h5/fee")
public class H5FeeController {

    private static final Logger log = LoggerFactory.getLogger(H5FeeController.class);

    private final ParkingRecordMapper recordMapper;
    private final ParkingLotMapper lotMapper;
    private final ParkingOrderMapper orderMapper;
    private final ParkingOrderService orderService;
    private final FeeCalculationService feeCalculationService;
    private final ParkingSessionService parkingSessionService;

    public H5FeeController(ParkingRecordMapper recordMapper,
                           ParkingLotMapper lotMapper,
                           ParkingOrderMapper orderMapper,
                           ParkingOrderService orderService,
                           FeeCalculationService feeCalculationService,
                           ParkingSessionService parkingSessionService) {
        this.recordMapper = recordMapper;
        this.lotMapper = lotMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.feeCalculationService = feeCalculationService;
        this.parkingSessionService = parkingSessionService;
    }

    /**
     * 按车牌查询停车费用（免登录）。
     * <p>
     * 遍历该车牌下所有在场记录，逐条计费并返回。
     * 如果记录已有 PENDING_PAY 订单则复用，否则创建新订单。
     *
     * @param rawPlate 原始车牌号
     * @return 费用信息列表
     */
    @GetMapping("/query")
    public R<List<H5FeeVO>> queryByPlate(@RequestParam("plate") String rawPlate) {
        return queryByPlateInternal(rawPlate);
    }

    /**
     * 按车牌查询停车费用（POST，避免部分环境 GET 中文参数乱码）。
     */
    @PostMapping("/query")
    public R<List<H5FeeVO>> queryByPlatePost(@Valid @RequestBody H5FeeQueryRequest request) {
        return queryByPlateInternal(request.getPlate());
    }

    private R<List<H5FeeVO>> queryByPlateInternal(String rawPlate) {
        // H5 免登录接口：临时以平台用户身份执行，跳过租户行级过滤
        TenantContext.Snapshot previous = TenantContext.get();
        TenantContext.set(new TenantContext.Snapshot(null, null, TenantContext.USER_TYPE_PLATFORM, null, null));
        try {
            return doQueryByPlate(rawPlate);
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private R<List<H5FeeVO>> doQueryByPlate(String rawPlate) {
        // 1. 标准化车牌
        String plate = PlateStandardizer.normalize(rawPlate);
        if (plate == null || plate.isEmpty()) {
            return R.fail(4001, "请输入车牌号");
        }

        // 2. 查询在场记录（绕过租户过滤）
        List<ParkingRecord> records = recordMapper.selectActiveByPlates(List.of(plate));

        if (records.isEmpty()) {
            return R.ok(List.of());
        }

        // 3. 逐条计费 + 创建/复用订单
        List<H5FeeVO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (ParkingRecord record : records) {
            try {
                H5FeeVO vo = buildFeeVo(record, now);
                result.add(vo);
            } catch (Exception e) {
                log.warn("计费失败: recordId={} plate={} error={}", record.getId(), plate, e.getMessage());
            }
        }

        log.info("H5 查费: plate={} resultCount={}", plate, result.size());
        return R.ok(result);
    }

    // ==================== 私有方法 ====================

    private H5FeeVO buildFeeVo(ParkingRecord record, LocalDateTime now) {
        // 计费（优先使用 ParkingSession 快照）
        com.jushan.platform.modules.parking.vo.ParkingSessionVO sessionVO =
                parkingSessionService.getInByPlateAndLot(record.getStandardizedPlate(), record.getParkingLotId());
        String snapshotJson = sessionVO != null ? sessionVO.getFeeRuleSnapshot() : null;
        int feeCents = feeCalculationService.calculateFeeCents(
                record.getParkingLotId(), null,
                sessionVO != null ? sessionVO.getVehicleType() : null,
                sessionVO != null ? sessionVO.getPlateColor() : null,
                record.getEntryTime(), now, snapshotJson);
        long durationMinutes = Duration.between(record.getEntryTime(), now).toMinutes();

        // 查车场名称
        ParkingLot lot = lotMapper.selectById(record.getParkingLotId());
        String parkName = lot != null ? lot.getName() : "";

        // 查是否有已有的 PENDING_PAY 订单
        ParkingOrder existingOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getParkingRecordId, record.getId())
                        .eq(ParkingOrder::getStatus, ParkingOrder.STATUS_PENDING_PAY)
                        .isNull(ParkingOrder::getDeletedAt)
                        .orderByDesc(ParkingOrder::getCreatedAt)
                        .last("LIMIT 1"));

        Long orderId;
        if (existingOrder != null) {
            // 复用已有订单（更新金额）
            orderId = existingOrder.getId();
            // 如果费用变化，更新
            if (!Integer.valueOf(feeCents).equals(existingOrder.getPayableAmount())) {
                existingOrder.setAmountCents(Math.max(0, feeCents));
                existingOrder.setPayableAmount(Math.max(0, feeCents));
                existingOrder.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateById(existingOrder);
            }
        } else {
            // 创建新订单（PENDING_PAY）
            String idempotencyKey = "H5_" + record.getId() + "_" + System.currentTimeMillis();
            ParkingOrder newOrder = orderService.createOrder(
                    record, feeCents, idempotencyKey, ParkingOrder.PAY_SCENE_ADVANCE);
            orderId = newOrder.getId();
        }

        // 构建 VO
        H5FeeVO vo = new H5FeeVO();
        vo.setOrderId(orderId);
        vo.setRecordId(record.getId());
        vo.setParkingLotId(record.getParkingLotId());
        vo.setParkName(parkName);
        vo.setPlate(record.getStandardizedPlate());
        vo.setEntryTime(record.getEntryTime() != null ? record.getEntryTime().toString() : "");
        vo.setDurationMinutes(durationMinutes);
        vo.setFeeCents(feeCents);
        vo.setFeeYuan(formatYuan(feeCents));
        vo.setPayable(feeCents > 0);

        return vo;
    }

    private String formatYuan(int cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
