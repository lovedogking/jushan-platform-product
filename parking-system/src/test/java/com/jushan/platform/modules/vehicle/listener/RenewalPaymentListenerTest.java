package com.jushan.platform.modules.vehicle.listener;

import com.jushan.platform.modules.vehicle.service.VehicleRenewalService;
import com.jushan.platform.modules.vehicle.vo.RenewalOrderVO;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.event.PaymentSuccessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RenewalPaymentListener} 单元测试（Phase 1 A0）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>MONTH_RENEW 订单 → 调用 applyRenewalEffect</li>
 *   <li>PARKING 订单 → 跳过（不调用续费逻辑）</li>
 *   <li>续费逻辑异常 → 捕获不传播</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RenewalPaymentListenerTest {

    @Mock
    private VehicleRenewalService renewalService;

    private RenewalPaymentListener listener;

    private ParkingOrder renewalOrder;
    private ParkingOrder parkingOrder;

    @BeforeEach
    void setUp() {
        listener = new RenewalPaymentListener(renewalService);

        renewalOrder = new ParkingOrder();
        renewalOrder.setId(100L);
        renewalOrder.setTenantId(1L);
        renewalOrder.setParkingLotId(1L);
        renewalOrder.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        renewalOrder.setPlateNumber("京A12345");
        renewalOrder.setRefId(50L);
        renewalOrder.setStatus(ParkingOrder.STATUS_PAID);

        parkingOrder = new ParkingOrder();
        parkingOrder.setId(200L);
        parkingOrder.setTenantId(1L);
        parkingOrder.setParkingLotId(1L);
        parkingOrder.setOrderType(ParkingOrder.ORDER_TYPE_PARKING);
        parkingOrder.setPlateNumber("京B67890");
        parkingOrder.setStatus(ParkingOrder.STATUS_PAID);
    }

    @Test
    @DisplayName("月卡续费订单支付成功后自动触发续费生效")
    void shouldApplyRenewalEffectForMonthRenewOrder() {
        // 准备
        PaymentSuccessEvent event = new PaymentSuccessEvent(renewalOrder, "MOCK-100-1234567890", "miniapp");
        when(renewalService.applyRenewalEffect(eq(100L), eq("MOCK-100-1234567890")))
                .thenReturn(new RenewalOrderVO());

        // 执行
        listener.handlePaymentSuccess(event);

        // 验证
        verify(renewalService).applyRenewalEffect(eq(100L), eq("MOCK-100-1234567890"));
    }

    @Test
    @DisplayName("普通停车订单支付成功后不触发续费生效")
    void shouldSkipNonMonthRenewOrder() {
        // 准备
        PaymentSuccessEvent event = new PaymentSuccessEvent(parkingOrder, "MOCK-200-1234567890", "miniapp");

        // 执行
        listener.handlePaymentSuccess(event);

        // 验证：未调用续费服务
        verify(renewalService, never()).applyRenewalEffect(any(), anyString());
    }

    @Test
    @DisplayName("续费生效异常不会传播（不影响支付主流程）")
    void shouldCatchExceptionFromRenewalService() {
        // 准备
        PaymentSuccessEvent event = new PaymentSuccessEvent(renewalOrder, "MOCK-100-1234567890", "miniapp");
        doThrow(new RuntimeException("数据库临时故障"))
                .when(renewalService).applyRenewalEffect(eq(100L), eq("MOCK-100-1234567890"));

        // 执行 — 不应抛出异常
        listener.handlePaymentSuccess(event);

        // 验证：调用被尝试但未传播异常
        verify(renewalService).applyRenewalEffect(eq(100L), eq("MOCK-100-1234567890"));
    }

    @Test
    @DisplayName("事件中订单为 null 时安全跳过")
    void shouldHandleNullOrderGracefully() {
        // 准备
        PaymentSuccessEvent event = new PaymentSuccessEvent(null, "MOCK-100-1234567890", "miniapp");

        // 执行 — 不应抛出 NullPointerException
        listener.handlePaymentSuccess(event);

        // 验证：未调用续费服务
        verify(renewalService, never()).applyRenewalEffect(any(), anyString());
    }
}
