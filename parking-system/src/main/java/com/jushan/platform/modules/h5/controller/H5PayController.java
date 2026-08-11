package com.jushan.platform.modules.h5.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.h5.dto.H5PayNotifyRequest;
import com.jushan.platform.modules.h5.dto.H5PayPrepareRequest;
import com.jushan.platform.modules.h5.vo.H5PayResultVO;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import com.jushan.platform.modules.parking.service.FeeCalculationService;
import com.jushan.platform.modules.parking.service.MockPaymentService;
import com.jushan.platform.modules.parking.service.ParkingOrderService;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

/**
 * H5 支付控制器。
 * <p>
 * 提供 H5 停车缴费的预下单、状态查询和模拟支付确认能力。
 * 复用现有 ParkingOrderService / MockPaymentService，计费走 FeeCalculationService（BillingEngine 已移除）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/h5/pay")
public class H5PayController {

    private static final Logger log = LoggerFactory.getLogger(H5PayController.class);

    /** H5 支付人标识 */
    private static final String PAID_BY_H5 = "h5_user";

    @Value("${h5.mock-pay-enabled:true}")
    private boolean mockEnabled;

    private final ParkingOrderMapper orderMapper;
    private final ParkingRecordMapper recordMapper;
    private final ParkingLotMapper lotMapper;
    private final ParkingOrderService orderService;
    private final MockPaymentService mockPaymentService;
    private final FeeCalculationService feeCalculationService;
    private final ParkingSessionService parkingSessionService;

    public H5PayController(ParkingOrderMapper orderMapper,
                           ParkingRecordMapper recordMapper,
                           ParkingLotMapper lotMapper,
                           ParkingOrderService orderService,
                           MockPaymentService mockPaymentService,
                           FeeCalculationService feeCalculationService,
                           ParkingSessionService parkingSessionService) {
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.lotMapper = lotMapper;
        this.orderService = orderService;
        this.mockPaymentService = mockPaymentService;
        this.feeCalculationService = feeCalculationService;
        this.parkingSessionService = parkingSessionService;
    }

    /**
     * 发起支付（锁定订单为 PAYING）。
     * <p>
     * 重新计费防止费用过期，更新订单金额，创建模拟支付记录并锁定订单。
     *
     * @param request 预下单请求（仅 orderId）
     * @return 支付结果（含完整展示信息）
     */
    @PostMapping("/prepare")
    public R<H5PayResultVO> prepare(@Valid @RequestBody H5PayPrepareRequest request) {
        return withPlatformContext(() -> doPrepare(request));
    }

    private R<H5PayResultVO> doPrepare(H5PayPrepareRequest request) {
        // 1. 查询订单
        ParkingOrder order = orderMapper.selectById(request.getOrderId());
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }

        // 2. 校验状态
        if (!ParkingOrder.STATUS_PENDING_PAY.equals(order.getStatus())) {
            if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                    || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
                return R.fail(4002, "该订单已支付");
            }
            if (ParkingOrder.STATUS_PAYING.equals(order.getStatus())) {
                // 已锁定，直接返回
                return R.ok(buildResult(order));
            }
            return R.fail(4002, "订单状态不允许支付: " + order.getStatus());
        }

        // 3. 校验停车记录仍在场
        ParkingRecord record = recordMapper.selectById(order.getParkingRecordId());
        if (record == null || !ParkingRecord.STATUS_PARKING.equals(record.getStatus())) {
            return R.fail(4002, "车辆已离场，无需支付");
        }

        // 4. 重新计费（防止费用过期）
        LocalDateTime now = LocalDateTime.now();
        com.jushan.platform.modules.parking.vo.ParkingSessionVO sessionVO =
                parkingSessionService.getInByPlateAndLot(record.getStandardizedPlate(), record.getParkingLotId());
        String snapshotJson = sessionVO != null ? sessionVO.getFeeRuleSnapshot() : null;
        int feeCents = feeCalculationService.calculateFeeCents(
                record.getParkingLotId(), null,
                sessionVO != null ? sessionVO.getVehicleType() : null,
                sessionVO != null ? sessionVO.getPlateColor() : null,
                record.getEntryTime(), now, snapshotJson);

        if (feeCents <= 0) {
            return R.fail(4002, "当前仍在免费时段内，无需支付");
        }

        // 更新订单金额
        if (!Integer.valueOf(feeCents).equals(order.getPayableAmount())) {
            order.setAmountCents(feeCents);
            order.setPayableAmount(feeCents);
            order.setUpdatedAt(now);
            orderMapper.updateById(order);
        }

        // 5. 创建模拟支付记录
        mockPaymentService.preparePay(order);

        // 6. 锁定订单为支付中
        orderService.startPaying(order.getId(), ParkingOrder.PAY_CHANNEL_PYUN);

        // 重新查询获取最新状态
        order = orderMapper.selectById(request.getOrderId());

        log.info("H5 支付预下单: orderId={} orderNo={} plate={} feeCents={} mock={}",
                order.getId(), order.getOrderNo(), order.getPlateNumber(), feeCents, mockEnabled);

        return R.ok(buildResult(order));
    }

    private <T> T withPlatformContext(Supplier<T> action) {
        TenantContext.Snapshot previous = TenantContext.get();
        TenantContext.set(new TenantContext.Snapshot(null, null, TenantContext.USER_TYPE_PLATFORM, null, null));
        try {
            return action.get();
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * 查询支付状态。
     *
     * @param orderId 订单 ID
     * @return 支付结果
     */
    @GetMapping("/query")
    public R<H5PayResultVO> query(@RequestParam("orderId") Long orderId) {
        return withPlatformContext(() -> doQuery(orderId));
    }

    private R<H5PayResultVO> doQuery(Long orderId) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }

        log.info("H5 支付查询: orderId={} status={}", order.getId(), order.getStatus());
        return R.ok(buildResult(order));
    }

    /**
     * 模拟支付通知（确认支付成功或失败）。
     *
     * @param request 通知请求（orderId + action）
     * @return 处理结果
     */
    @PostMapping("/notify")
    public R<?> notify(@Valid @RequestBody H5PayNotifyRequest request) {
        return withPlatformContext(() -> doNotify(request));
    }

    private R<?> doNotify(H5PayNotifyRequest request) {
        if (!mockEnabled) {
            return R.fail(4002, "模拟支付未启用");
        }

        ParkingOrder order = orderMapper.selectById(request.getOrderId());
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }

        // 幂等：已支付/已完成的订单直接返回
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            log.info("订单已处理: orderId={} status={}", order.getId(), order.getStatus());
            return R.ok(Map.of("orderId", order.getId(), "status", order.getStatus()));
        }

        if ("success".equals(request.getAction())) {
            // 模拟支付成功
            boolean success = mockPaymentService.confirmPay(order.getId(), PAID_BY_H5);
            if (!success) {
                log.warn("模拟支付确认失败: orderId={} status={}", order.getId(), order.getStatus());
                return R.fail(4002, "支付失败，订单可能已过期");
            }

            order = orderMapper.selectById(request.getOrderId());
            log.info("H5 模拟支付成功: orderId={} orderNo={} plate={} amount={}",
                    order.getId(), order.getOrderNo(), order.getPlateNumber(), order.getPaidAmount());

            return R.ok(Map.of("orderId", order.getId(), "status", order.getStatus()));
        } else {
            // 模拟支付失败：取消订单
            log.info("H5 模拟支付失败: orderId={}", order.getId());
            order.setStatus(ParkingOrder.STATUS_CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            return R.ok(Map.of("orderId", order.getId(), "status", "CANCELLED"));
        }
    }

    // ==================== 私有方法 ====================

    private H5PayResultVO buildResult(ParkingOrder order) {
        // 查询车场名称
        ParkingLot lot = lotMapper.selectById(order.getParkingLotId());
        String parkName = lot != null ? lot.getName() : "";

        H5PayResultVO vo = new H5PayResultVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPlate(order.getPlateNumber());
        vo.setParkName(parkName);
        vo.setPayableAmount(order.getPayableAmount());
        vo.setPayableAmountYuan(formatYuan(order.getPayableAmount() != null ? order.getPayableAmount() : 0));
        vo.setStatus(order.getStatus());
        vo.setMock(mockEnabled);

        if (order.getPaidAmount() != null) {
            vo.setPaidAmount(order.getPaidAmount());
        }
        if (order.getPayTime() != null) {
            vo.setPaidTime(order.getPayTime().toString());
        }

        return vo;
    }

    private String formatYuan(int cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
