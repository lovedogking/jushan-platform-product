package com.jushan.platform.modules.miniapp.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.MiniPayNotifyRequest;
import com.jushan.platform.modules.miniapp.dto.MiniPayPrepareRequest;
import com.jushan.platform.modules.miniapp.vo.MiniPayResultVO;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.service.MockPaymentService;
import com.jushan.system.service.ParkingFeeService;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.vo.ParkingFeeVO;
import com.jushan.system.ws.BoothWebSocketPublisher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序支付控制器。
 * <p>
 * 提供小程序停车缴费的预下单和支付确认能力。
 * <p>
 * <strong>Phase 0 改造</strong>：已切换为模拟支付流程（{@link MockPaymentService}），
 * 不再依赖真实支付平台（4pyun.com/PyunPaymentClient）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mini/pay")
public class MiniPayController {

    private static final Logger log = LoggerFactory.getLogger(MiniPayController.class);

    /** 前端调用标记 */
    private static final String PAID_BY_MINIAPP = "miniapp";

    private final ParkingFeeService parkingFeeService;
    private final ParkingOrderService orderService;
    private final ParkingRecordMapper recordMapper;
    private final MockPaymentService mockPaymentService;
    private final BoothWebSocketPublisher boothWebSocketPublisher;

    public MiniPayController(ParkingFeeService parkingFeeService,
                              ParkingOrderService orderService,
                              ParkingRecordMapper recordMapper,
                              MockPaymentService mockPaymentService,
                              BoothWebSocketPublisher boothWebSocketPublisher) {
        this.parkingFeeService = parkingFeeService;
        this.orderService = orderService;
        this.recordMapper = recordMapper;
        this.mockPaymentService = mockPaymentService;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
    }

    /**
     * 支付预下单（模拟支付）。
     * <p>
     * 计算费用、创建订单、创建模拟支付记录。
     * 不再调用真实支付平台，小程序无需处理 wx.requestPayment 参数。
     *
     * @param request 预下单请求（含停车记录 ID）
     * @return 预支付结果
     */
    @PostMapping("/prepare")
    @RequirePermission("miniapp:view")
    public R<MiniPayResultVO> preparePay(@Valid @RequestBody MiniPayPrepareRequest request) {
        // 1. 查询费用（同时校验车牌归属）
        ParkingFeeVO feeVo = parkingFeeService.queryByRecordIdForWx(request.getRecordId());

        if (!Boolean.TRUE.equals(feeVo.getPayable())) {
            return R.fail(4002, "该记录不可支付（" + (feeVo.getMessage() != null ? feeVo.getMessage() : "已离场或已取消") + "）");
        }

        if (feeVo.getFeeCents() != null && feeVo.getFeeCents() <= 0) {
            return R.fail(4002, "无需支付（当前仍在免费时段内）");
        }

        // 2. 如果已有待支付订单，复用
        if (Boolean.TRUE.equals(feeVo.getHasPendingOrder()) && feeVo.getPendingOrderId() != null) {
            ParkingOrder pendingOrder = orderService.getById(feeVo.getPendingOrderId());
            if (pendingOrder != null && ParkingOrder.STATUS_PENDING_PAY.equals(pendingOrder.getStatus())) {
                return buildPrepareResult(pendingOrder);
            }
        }

        // 3. 获取停车记录
        ParkingRecord record = recordMapper.selectById(request.getRecordId());
        if (record == null) {
            return R.fail(1003, "停车记录不存在");
        }

        // 4. 创建订单
        String idempotencyKey = "MINI_" + request.getRecordId() + "_" + System.currentTimeMillis();
        int feeCents = feeVo.getFeeCents() != null ? feeVo.getFeeCents() : 0;
        ParkingOrder order = orderService.createOrder(record, feeCents, idempotencyKey);

        // 零元订单直接完成
        if (ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            MiniPayResultVO vo = new MiniPayResultVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStatus("COMPLETED");
            vo.setPayableAmount(0);
            vo.setPayableAmountYuan("0.00");
            return R.ok(vo);
        }

        // 5. 标记支付中 + 创建模拟支付记录
        orderService.startPaying(order.getId(), ParkingOrder.PAY_CHANNEL_PYUN);
        mockPaymentService.preparePay(order);

        return buildPrepareResult(order);
    }

    /**
     * 模拟支付确认（替代真实的 wx.requestPayment 回调）。
     * <p>
     * 小程序端点击"确认支付"后调用此接口完成支付。
     * 不再需要真实的 wx.requestPayment 调用及 paySign 等参数。
     *
     * @param request 支付通知请求（仅需 orderId）
     * @return 处理结果
     */
    @PostMapping("/notify")
    @RequirePermission("miniapp:view")
    public R<?> payNotify(@Valid @RequestBody MiniPayNotifyRequest request) {
        ParkingOrder order = orderService.getById(request.getOrderId());
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }

        // 幂等：已支付/已完成的订单直接返回成功
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            log.info("订单已处理，跳过通知: orderId={}", order.getId());
            return R.ok(Map.of("orderId", order.getId(), "status", "PAID"));
        }

        // 调用模拟支付确认
        boolean success = mockPaymentService.confirmPay(order.getId(), PAID_BY_MINIAPP);
        if (!success) {
            log.warn("模拟支付确认失败: orderId={} status={}", order.getId(), order.getStatus());
            return R.fail(4002, "支付失败，订单可能已过期");
        }

        // 重新查询订单获取最新状态
        order = orderService.getById(request.getOrderId());

        // 推送 WebSocket 通知到岗亭
        boothWebSocketPublisher.sendPaymentCompleted(
                order.getParkingLotId(),
                order.getId(),
                order.getOrderNo(),
                order.getPlateNumber(),
                order.getPaidAmount());

        log.info("小程序模拟支付完成: orderId={} orderNo={} plate={} amount={}",
                order.getId(), order.getOrderNo(), order.getPlateNumber(), order.getPaidAmount());

        return R.ok(Map.of("orderId", order.getId(), "status", "PAID"));
    }

    // ==================== 私有方法 ====================

    /**
     * 构建预支付结果（模拟支付版本）。
     * 返回简单的订单信息，无需 P云支付参数。
     */
    private R<MiniPayResultVO> buildPrepareResult(ParkingOrder order) {
        MiniPayResultVO vo = new MiniPayResultVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayChannel(ParkingOrder.PAY_CHANNEL_PYUN);
        vo.setStatus("PAYING");
        vo.setPayableAmount(order.getPayableAmount());
        vo.setPayableAmountYuan(formatYuan(order.getPayableAmount()));

        // 模拟支付不需要 prepayParams（无需 wx.requestPayment）
        // 前端直接调用 /notify 完成支付即可

        return R.ok(vo);
    }

    private String formatYuan(int cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
