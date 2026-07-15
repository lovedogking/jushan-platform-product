package com.jushan.platform.modules.miniapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.MiniPayNotifyRequest;
import com.jushan.platform.modules.miniapp.dto.MiniPayPrepareRequest;
import com.jushan.platform.modules.miniapp.vo.MiniPayResultVO;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.PayMerchantConfig;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.PayMerchantConfigMapper;
import com.jushan.system.service.ParkingFeeService;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.service.PyunPaymentClient;
import com.jushan.system.vo.ParkingFeeVO;
import com.jushan.system.ws.BoothWebSocketPublisher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 小程序支付控制器。
 * <p>
 * 提供小程序停车缴费的预下单和支付结果通知能力。
 * 复用现有 P云支付对接逻辑（{@link PyunPaymentClient}）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mini/pay")
public class MiniPayController {

    private static final Logger log = LoggerFactory.getLogger(MiniPayController.class);

    private final ParkingFeeService parkingFeeService;
    private final ParkingOrderService orderService;
    private final ParkingRecordMapper recordMapper;
    private final PyunPaymentClient pyunClient;
    private final PayMerchantConfigMapper merchantConfigMapper;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final ObjectMapper objectMapper;

    public MiniPayController(ParkingFeeService parkingFeeService,
                              ParkingOrderService orderService,
                              ParkingRecordMapper recordMapper,
                              PyunPaymentClient pyunClient,
                              PayMerchantConfigMapper merchantConfigMapper,
                              BoothWebSocketPublisher boothWebSocketPublisher,
                              ObjectMapper objectMapper) {
        this.parkingFeeService = parkingFeeService;
        this.orderService = orderService;
        this.recordMapper = recordMapper;
        this.pyunClient = pyunClient;
        this.merchantConfigMapper = merchantConfigMapper;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 支付预下单。
     * <p>
     * 计算费用、创建订单、调用 P云交易预请求，返回支付参数给小程序。
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
                return doPrepay(pendingOrder);
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

        // 5. 标记支付中 + 调用 P云预请求
        orderService.startPaying(order.getId(), ParkingOrder.PAY_CHANNEL_PYUN);
        return doPrepay(order);
    }

    /**
     * 支付结果通知（前端调用）。
     * <p>
     * 前端调用 wx.requestPayment 成功后回调此接口确认支付结果。
     *
     * @param request 支付通知请求
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
            return R.ok("已处理");
        }

        // 更新订单为已支付
        boolean success = orderService.markPaid(order.getId(), request.getPaySerial(), request.getPaidAmount());
        if (!success) {
            log.warn("订单支付状态更新失败: orderId={}", request.getOrderId());
            return R.fail(4002, "订单状态更新失败");
        }

        // 推送 WebSocket 通知到岗亭
        boothWebSocketPublisher.sendPaymentCompleted(
                order.getParkingLotId(),
                order.getId(),
                order.getOrderNo(),
                order.getPlateNumber(),
                request.getPaidAmount());

        log.info("小程序支付完成: orderId={} orderNo={} plate={} amount={}",
                order.getId(), order.getOrderNo(), order.getPlateNumber(), request.getPaidAmount());

        return R.ok(Map.of("orderId", order.getId(), "status", "PAID"));
    }

    // ==================== 私有方法 ====================

    /**
     * 执行 P云交易预请求。
     */
    private R<MiniPayResultVO> doPrepay(ParkingOrder order) {
        // 获取商户配置
        PayMerchantConfig config = merchantConfigMapper.selectActiveByParkingLot(
                order.getTenantId(), order.getParkingLotId());
        if (config == null) {
            log.warn("停车场未配置 P云商户: parkingLotId={}", order.getParkingLotId());
            orderService.markPayFailed(order.getId());
            return R.fail(4003, "支付渠道未配置");
        }

        // 构建 P云预请求参数
        Map<String, String> params = new HashMap<>();
        params.put("pay_order", order.getOrderNo());
        params.put("subject", "停车支付" + formatYuan(order.getPayableAmount()) + "元(" + order.getPlateNumber() + ")");
        params.put("body", "【" + order.getPlateNumber() + "】停车缴费" + formatYuan(order.getPayableAmount()) + "元");
        params.put("value", String.valueOf(order.getPayableAmount()));
        params.put("payer", "");
        params.put("notify_url", config.getNotifyUrl() != null ? config.getNotifyUrl() : "");
        params.put("callback_url", config.getCallbackUrl() != null ? config.getCallbackUrl() : "");
        params.put("expire_time", PyunPaymentClient.formatExpireTime(15));
        params.put("trade_scene", "PARKING");

        // extra 字段
        Map<String, Object> extra = new HashMap<>();
        extra.put("plate", order.getPlateNumber());
        extra.put("plate_color", "-1");
        extra.put("parking_serial", String.valueOf(order.getParkingRecordId()));
        extra.put("park_name", "停车场");
        try {
            params.put("extra", objectMapper.writeValueAsString(extra));
        } catch (Exception e) {
            log.warn("extra JSON 序列化失败", e);
        }

        // 调用 P云
        JsonNode response = pyunClient.tradePrepare(config, params);
        String code = response.path("code").asText("");

        if ("1000".equals(code) || "1001".equals(code)) {
            String paySerial = response.path("pay_serial").asText("");
            log.info("P云预请求成功: orderId={} paySerial={}", order.getId(), paySerial);

            MiniPayResultVO vo = new MiniPayResultVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setPaySerial(paySerial);
            vo.setPayChannel(ParkingOrder.PAY_CHANNEL_PYUN);
            vo.setStatus("PAYING");
            vo.setPayableAmount(order.getPayableAmount());
            vo.setPayableAmountYuan(formatYuan(order.getPayableAmount()));

            Map<String, Object> prepayParams = new HashMap<>();
            prepayParams.put("paySerial", paySerial);
            prepayParams.put("appId", config.getAppId());
            vo.setPrepayParams(prepayParams);

            return R.ok(vo);
        } else {
            log.warn("P云预请求失败: orderId={} code={} message={}",
                    order.getId(), code, response.path("message").asText());
            orderService.markPayFailed(order.getId());
            return R.fail(4003, "支付预请求失败: " + response.path("message").asText("未知错误"));
        }
    }

    private String formatYuan(int cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
