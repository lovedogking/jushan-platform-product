package com.jushan.platform.modules.parking.event;

import com.jushan.platform.modules.parking.entity.ParkingOrder;

/**
 * 支付成功事件 — 模拟支付确认成功后发布（Phase 1 A0）。
 * <p>
 * 由 {@code MockPaymentService.confirmPay()} 和 {@code manualMarkPaid()} 在订单
 * 标记为 PAID 后发布。监听者包括：
 * <ul>
 *   <li>{@code RenewalPaymentListener} — 月卡续费订单支付成功后自动延长有效期</li>
 * </ul>
 * <p>
 * 事件发布是同步的（默认 Spring {@code ApplicationEventMulticaster}），监听者应
 * 自行 try-catch 异常，不得影响支付主流程。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class PaymentSuccessEvent {

    private final ParkingOrder order;
    private final String paySerial;
    private final String paidBy;

    /**
     * @param order     已标记为 PAID 的订单（含完整订单信息，用于判断订单类型）
     * @param paySerial 支付流水号（由 MockPaymentService 生成，格式 MOCK-{orderId}-{timestamp}）
     * @param paidBy    支付人标识（小程序="miniapp"，手动标记=operatorId，系统超时="SYSTEM"）
     */
    public PaymentSuccessEvent(ParkingOrder order, String paySerial, String paidBy) {
        this.order = order;
        this.paySerial = paySerial;
        this.paidBy = paidBy;
    }

    public ParkingOrder getOrder() {
        return order;
    }

    public String getPaySerial() {
        return paySerial;
    }

    public String getPaidBy() {
        return paidBy;
    }
}
