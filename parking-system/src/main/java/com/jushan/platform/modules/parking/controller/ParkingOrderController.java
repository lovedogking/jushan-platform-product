package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.service.MockPaymentService;
import com.jushan.platform.modules.parking.service.ParkingOrderService;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单支付 Controller（Sprint 8 / Phase 0 S0-3 改造）。
 * <p>
 * 提供：
 * <ul>
 *   <li>POST /api/v1/orders/{orderId}/pay —— 订单支付（模拟支付）</li>
 *   <li>POST /api/v1/orders/{orderId}/cancel —— 取消订单</li>
 *   <li>GET /api/v1/orders/{orderId} —— 查询订单</li>
 * </ul>
 * <p>
 * <strong>Phase 0 改造</strong>：已切换为模拟支付模式（{@link MockPaymentService}），
 * 不再依赖真实支付平台（4pyun.com）。
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
    private final MockPaymentService mockPaymentService;

    public ParkingOrderController(ParkingOrderService orderService,
                                   ParkingOrderMapper orderMapper,
                                   MockPaymentService mockPaymentService) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.mockPaymentService = mockPaymentService;
    }

    /**
     * 订单支付 —— 模拟支付（Phase 0 改造，替代 P云预请求）。
     * <p>
     * 创建模拟支付记录，小程序端直接调用 /notify 确认支付即可。
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

        // 标记支付中
        String channel = request.getPayChannel() != null ? request.getPayChannel() : ParkingOrder.PAY_CHANNEL_PYUN;
        boolean started = orderService.startPaying(orderId, channel);
        if (!started) {
            return R.fail(4002, "订单状态变更失败");
        }

        // 创建模拟支付记录
        order = orderService.getById(orderId);
        mockPaymentService.preparePay(order);

        log.info("模拟支付预请求成功: orderId={}", orderId);
        return R.ok(Map.of(
                "orderId", orderId,
                "orderNo", order.getOrderNo(),
                "payChannel", channel,
                "status", "PAYING",
                "message", "模拟支付已就绪，请调用确认接口完成支付"
        ));
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
