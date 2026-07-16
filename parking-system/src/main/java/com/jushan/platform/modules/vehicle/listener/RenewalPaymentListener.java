package com.jushan.platform.modules.vehicle.listener;

import com.jushan.platform.modules.vehicle.service.VehicleRenewalService;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.event.PaymentSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功事件监听器 — 月卡续费订单支付成功后自动生效（Phase 1 A0）。
 * <p>
 * 监听 {@link PaymentSuccessEvent}，判断订单类型是否为 {@link ParkingOrder#ORDER_TYPE_MONTH_RENEW}，
 * 若是则调用 {@link VehicleRenewalService#applyRenewalEffect(Long, String)} 自动延长月卡有效期、
 * 回写在场车辆类型、记录审计。
 * <p>
 * <strong>异常处理</strong>：续费生效异常不会影响支付主流程。异常被捕获并记录 ERROR 日志，
 * 运营端需人工介入处理。
 * <p>
 * <strong>替代旧链路</strong>：在 Phase 0 之前，月卡续费支付成功由 {@code PyunNotifyController}
 * （已废弃）通过实时回调触发。Phase 1 改用本事件监听器，通过 {@code MockPaymentService}
 * 发布事件驱动，链路更清晰且不产生跨包依赖。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class RenewalPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(RenewalPaymentListener.class);

    private final VehicleRenewalService renewalService;

    public RenewalPaymentListener(VehicleRenewalService renewalService) {
        this.renewalService = renewalService;
    }

    /**
     * 处理支付成功事件 — 月卡续费自动生效。
     * <p>
     * 仅对 {@code MONTH_RENEW} 类型订单执行续费生效；其他类型订单静默跳过。
     * 异常会被捕获并记录，不影响发布者的事务。
     *
     * @param event 支付成功事件（含订单、支付流水号、支付人）
     */
    @EventListener
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        ParkingOrder order = event.getOrder();
        if (order == null || !ParkingOrder.ORDER_TYPE_MONTH_RENEW.equals(order.getOrderType())) {
            return;
        }

        try {
            renewalService.applyRenewalEffect(order.getId(), event.getPaySerial());
            log.info("月卡续费生效成功: orderId={} paySerial={} vehicleId={}",
                    order.getId(), event.getPaySerial(), order.getRefId());
        } catch (Exception e) {
            // 支付已成功，续费生效失败不影响支付结果，但需记录告警以便人工介入
            log.error("月卡续费生效处理失败（支付已成功，需人工介入）: "
                            + "orderId={} paySerial={} plate={} error={}",
                    order.getId(), event.getPaySerial(), order.getPlateNumber(), e.getMessage(), e);
        }
    }
}
