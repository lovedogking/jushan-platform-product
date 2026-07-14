package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.PayMerchantConfig;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.service.PyunPaymentClient;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.PayMerchantConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单支付 Controller（Sprint 8）。
 * <p>
 * 提供：
 * <ul>
 *   <li>POST /api/v1/orders/{orderId}/pay —— 订单支付（P云预请求）</li>
 *   <li>POST /api/v1/orders/{orderId}/cancel —— 取消订单</li>
 *   <li>GET /api/v1/orders/{orderId} —— 查询订单</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class ParkingOrderController {

    private static final Logger log = LoggerFactory.getLogger(ParkingOrderController.class);

    private final ParkingOrderService orderService;
    private final ParkingOrderMapper orderMapper;
    private final PyunPaymentClient pyunClient;
    private final PayMerchantConfigMapper merchantConfigMapper;
    private final ObjectMapper objectMapper;

    public ParkingOrderController(ParkingOrderService orderService,
                                   ParkingOrderMapper orderMapper,
                                   PyunPaymentClient pyunClient,
                                   PayMerchantConfigMapper merchantConfigMapper,
                                   ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.pyunClient = pyunClient;
        this.merchantConfigMapper = merchantConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 订单支付 —— 调用 P云交易预请求。
     */
    @PostMapping("/{orderId}/pay")
    public R<?> payOrder(@PathVariable Long orderId,
                              @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
                              @RequestBody PayOrderRequest request) {
        ParkingOrder order = orderService.getById(orderId);
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }

        if (!ParkingOrder.STATUS_PENDING_PAY.equals(order.getStatus())) {
            return R.fail(4002, "订单状态不允许支付: " + order.getStatus());
        }

        // 检查是否过期
        if (order.getExpiredAt() != null && order.getExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            orderService.cancelOrder(orderId);
            return R.fail(4002, "订单已过期");
        }

        // 获取商户配置
        PayMerchantConfig config = merchantConfigMapper.selectActiveByParkingLot(order.getTenantId(), order.getParkingLotId());
        if (config == null) {
            log.warn("停车场未配置 P云商户: parkingLotId={}", order.getParkingLotId());
            return R.fail(4003, "支付渠道未配置");
        }

        // 标记支付中
        boolean started = orderService.startPaying(orderId, request.getPayChannel());
        if (!started) {
            return R.fail(4002, "订单状态变更失败");
        }

        // 一期仅支持 P云，其他渠道【预留】
        if (!"PYUN".equals(request.getPayChannel())) {
            log.info("支付渠道预留: channel={}", request.getPayChannel());
            return R.ok(Map.of(
                    "orderId", orderId,
                    "orderNo", order.getOrderNo(),
                    "payChannel", request.getPayChannel(),
                    "status", "RESERVED",
                    "message", "该支付渠道一期预留，请使用 P云"
            ));
        }

        // 调用 P云交易预请求
        Map<String, String> params = new HashMap<>();
        params.put("pay_order", order.getOrderNo());
        params.put("subject", "停车支付" + formatYuan(order.getPayableAmount()) + "元(" + order.getPlateNumber() + ")");
        params.put("body", "【" + order.getPlateNumber() + "】在停车场支付" + formatYuan(order.getPayableAmount()) + "元");
        params.put("value", String.valueOf(order.getPayableAmount()));
        params.put("payer", request.getPayerOpenId());
        params.put("notify_url", config.getNotifyUrl());
        params.put("callback_url", config.getCallbackUrl());
        params.put("expire_time", PyunPaymentClient.formatExpireTime(15));
        params.put("trade_scene", "PARKING");

        // extra 字段：停车场景信息
        Map<String, Object> extra = new HashMap<>();
        extra.put("plate", order.getPlateNumber());
        extra.put("plate_color", "-1");
        extra.put("parking_serial", String.valueOf(order.getParkingRecordId()));
        extra.put("park_name", "停车场");
        extra.put("enter_time", String.valueOf(System.currentTimeMillis()));
        extra.put("parking_time", String.valueOf(order.getPayableAmount()));
        try {
            params.put("extra", objectMapper.writeValueAsString(extra));
        } catch (Exception e) {
            log.warn("extra JSON 序列化失败", e);
        }

        JsonNode response = pyunClient.tradePrepare(config, params);
        String code = response.path("code").asText("");

        if ("1000".equals(code) || "1001".equals(code)) {
            String paySerial = response.path("pay_serial").asText("");
            log.info("P云预请求成功: orderId={} paySerial={}", orderId, paySerial);
            return R.ok(Map.of(
                    "orderId", orderId,
                    "orderNo", order.getOrderNo(),
                    "payChannel", "PYUN",
                    "status", "PAYING",
                    "paySerial", paySerial,
                    "prepayParams", Map.of(
                            "paySerial", paySerial,
                            "appId", config.getAppId()
                    )
            ));
        } else {
            log.warn("P云预请求失败: orderId={} code={} message={}", orderId, code, response.path("message").asText());
            orderService.markPayFailed(orderId);
            return R.fail(4003, "支付预请求失败: " + response.path("message").asText("未知错误"));
        }
    }

    /**
     * 取消订单。
     */
    @PostMapping("/{orderId}/cancel")
    public R<?> cancelOrder(@PathVariable Long orderId) {
        boolean cancelled = orderService.cancelOrder(orderId);
        if (cancelled) {
            return R.ok(Map.of("orderId", orderId, "status", "CANCELLED"));
        }
        return R.fail(4002, "订单取消失败（状态不允许）");
    }

    /**
     * 查询订单详情。
     */
    @GetMapping("/{orderId}")
    public R<?> getOrder(@PathVariable Long orderId) {
        ParkingOrder order = orderService.getById(orderId);
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }
        return R.ok(order);
    }

    /**
     * P云支付回调通知（一期 mock 模式）。
     * <p>
     * 接收 P云异步支付结果通知，更新订单状态。
     * 实际生产环境需验签、幂等、防重放。
     *
     * @param params 回调参数（Form 表单）
     * @return 处理结果
     */
    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public R<?> payNotify(@RequestParam Map<String, String> params) {
        String payOrder = params.get("pay_order");
        String status = params.get("status");
        String paySerial = params.get("pay_serial");

        log.info("P云支付回调: payOrder={}, status={}, paySerial={}", payOrder, status, paySerial);

        if (payOrder == null || payOrder.isEmpty()) {
            return R.fail(4001, "缺少 pay_order");
        }

        ParkingOrder order = orderService.getByOrderNo(payOrder);
        if (order == null) {
            log.warn("回调订单不存在: payOrder={}", payOrder);
            return R.fail(1003, "订单不存在");
        }

        // 幂等：已完成的订单不再处理
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus()) || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            log.info("订单已处理，跳过回调: orderId={}", order.getId());
            return R.ok("已处理");
        }

        if ("PAID".equals(status) || "COMPLETED".equals(status)) {
            boolean success = orderService.completePay(order.getId(), paySerial, java.time.LocalDateTime.now());
            if (success) {
                log.info("订单支付完成: orderId={}, paySerial={}", order.getId(), paySerial);
                return R.ok("支付成功");
            } else {
                log.warn("订单支付完成更新失败: orderId={}", order.getId());
                return R.fail(4002, "订单状态更新失败");
            }
        } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
            orderService.cancelOrder(order.getId());
            log.info("订单支付失败/取消: orderId={}, status={}", order.getId(), status);
            return R.ok("已取消");
        }

        return R.ok("已接收");
    }

    private String formatYuan(int cents) {
        return String.format("%.2f", cents / 100.0);
    }

    /**
     * 支付请求 DTO。
     */
    public static class PayOrderRequest {
        private String payChannel;
        private String payerOpenId;
        private String payAmount;
        private String scene;

        public String getPayChannel() { return payChannel; }
        public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
        public String getPayerOpenId() { return payerOpenId; }
        public void setPayerOpenId(String payerOpenId) { this.payerOpenId = payerOpenId; }
        public String getPayAmount() { return payAmount; }
        public void setPayAmount(String payAmount) { this.payAmount = payAmount; }
        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
    }
}
