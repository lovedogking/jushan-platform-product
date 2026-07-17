package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ParkingOrderService 状态机与退款单元测试（任务包 1-2）。
 * <p>
 * 覆盖预订单流转、欠费流转、退款闭环与非法流转守卫。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ParkingOrderServiceTest {

    @Mock
    private ParkingOrderMapper orderMapper;
    @Mock
    private ParkingRecordMapper recordMapper;
    @Mock
    private OrderStatusLogService orderStatusLogService;

    private ParkingOrderService service;

    @BeforeEach
    void setUp() {
        service = new ParkingOrderService(orderMapper, recordMapper, orderStatusLogService);
    }

    // ==================== 预订单 ====================

    @Test
    @DisplayName("入场创建预订单：状态 PRE_ORDER、金额 0、留痕")
    void shouldCreatePreOrder() {
        ParkingRecord record = record(10L, 1L, 100L, "粤B12345");
        doAnswerSetId(555L);

        ParkingOrder pre = service.createPreOrder(record);

        assertThat(pre.getStatus()).isEqualTo(ParkingOrder.STATUS_PRE_ORDER);
        assertThat(pre.getOrderType()).isEqualTo(ParkingOrder.ORDER_TYPE_PARKING);
        assertThat(pre.getPayableAmount()).isZero();
        verify(orderMapper).insert(any(ParkingOrder.class));
        verify(orderStatusLogService).record(any(ParkingOrder.class), isNull(),
                eq(ParkingOrder.STATUS_PRE_ORDER), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("预订单出场计费 → 待支付（PRE_ORDER→PENDING_PAY）")
    void shouldTransitionPreOrderToPending() {
        ParkingOrder pre = order(1L, ParkingOrder.STATUS_PRE_ORDER);
        when(orderMapper.selectById(1L)).thenReturn(pre);
        when(orderMapper.update(any(), any())).thenReturn(1);

        boolean ok = service.preOrderToPending(1L, 500, LocalDateTime.now().plusMinutes(15));

        assertThat(ok).isTrue();
        verify(orderStatusLogService).record(eq(pre), eq(ParkingOrder.STATUS_PRE_ORDER),
                eq(ParkingOrder.STATUS_PENDING_PAY), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("预订单免费放行 → 已完成（PRE_ORDER→COMPLETED）")
    void shouldTransitionPreOrderToCompleted() {
        ParkingOrder pre = order(1L, ParkingOrder.STATUS_PRE_ORDER);
        when(orderMapper.selectById(1L)).thenReturn(pre);
        when(orderMapper.update(any(), any())).thenReturn(1);

        boolean ok = service.preOrderToCompleted(1L, LocalDateTime.now());

        assertThat(ok).isTrue();
        verify(orderStatusLogService).record(eq(pre), eq(ParkingOrder.STATUS_PRE_ORDER),
                eq(ParkingOrder.STATUS_COMPLETED), anyString(), any(), any(), any());
    }

    // ==================== 欠费 ====================

    @Test
    @DisplayName("允许欠费（PENDING_PAY→ARREARS）")
    void shouldAllowArrears() {
        ParkingOrder pending = order(2L, ParkingOrder.STATUS_PENDING_PAY);
        when(orderMapper.selectById(2L)).thenReturn(pending);
        when(orderMapper.update(any(), any())).thenReturn(1);

        boolean ok = service.allowArrears(2L, null, 9L, "允许欠费放行");

        assertThat(ok).isTrue();
        verify(orderStatusLogService).record(eq(pending), eq(ParkingOrder.STATUS_PENDING_PAY),
                eq(ParkingOrder.STATUS_ARREARS), anyString(), eq(9L), any(), anyString());
    }

    @Test
    @DisplayName("欠费补缴（ARREARS→COMPLETED）")
    void shouldPayArrears() {
        ParkingOrder arrears = order(3L, ParkingOrder.STATUS_ARREARS);
        arrears.setPayableAmount(800);
        when(orderMapper.selectById(3L)).thenReturn(arrears);
        when(orderMapper.update(any(), any())).thenReturn(1);

        boolean ok = service.payArrears(3L, null, 9L);

        assertThat(ok).isTrue();
        verify(orderStatusLogService).record(eq(arrears), eq(ParkingOrder.STATUS_ARREARS),
                eq(ParkingOrder.STATUS_COMPLETED), anyString(), eq(9L), any(), any());
    }

    // ==================== 退款 ====================

    @Test
    @DisplayName("退款成功（PAID→REFUNDED），记录原因/操作人")
    void shouldRefundPaidOrder() {
        ParkingOrder paid = order(4L, ParkingOrder.STATUS_PAID);
        paid.setPayableAmount(500);
        paid.setPaidAmount(500);
        when(orderMapper.selectById(4L)).thenReturn(paid);
        when(orderMapper.update(any(), any())).thenReturn(1);

        boolean ok = service.refund(4L, "重复扣费退款", 9L);

        assertThat(ok).isTrue();
        verify(orderStatusLogService).record(eq(paid), eq(ParkingOrder.STATUS_PAID),
                eq(ParkingOrder.STATUS_REFUNDED), anyString(), eq(9L), any(), contains("重复扣费退款"));
    }

    @Test
    @DisplayName("非 PAID 订单退款被拒（PENDING_PAY）")
    void shouldRejectRefundForNonPaidOrder() {
        ParkingOrder pending = order(5L, ParkingOrder.STATUS_PENDING_PAY);
        when(orderMapper.selectById(5L)).thenReturn(pending);

        assertThatThrownBy(() -> service.refund(5L, "尝试退款", 9L))
                .isInstanceOf(BusinessException.class);

        verify(orderMapper, never()).update(any(), any());
        verify(orderStatusLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("退款原因为空被拒")
    void shouldRejectRefundWithoutReason() {
        assertThatThrownBy(() -> service.refund(6L, "   ", 9L))
                .isInstanceOf(BusinessException.class);
        verify(orderMapper, never()).selectById(any());
    }

    // ==================== 状态机守卫 ====================

    @Test
    @DisplayName("非法流转被拦截：completePay 作用于已取消订单抛异常（CANCELLED→PAID）")
    void shouldRejectIllegalTransitionOnCompletePay() {
        ParkingOrder cancelled = order(7L, ParkingOrder.STATUS_CANCELLED);
        when(orderMapper.selectById(7L)).thenReturn(cancelled);

        assertThatThrownBy(() -> service.completePay(7L, "SERIAL", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class);

        verify(orderMapper, never()).update(any(), any());
    }

    // ==================== 辅助 ====================

    private void doAnswerSetId(long id) {
        when(orderMapper.insert(any(ParkingOrder.class))).thenAnswer(inv -> {
            ParkingOrder o = inv.getArgument(0);
            o.setId(id);
            return 1;
        });
    }

    private ParkingOrder order(Long id, String status) {
        ParkingOrder o = new ParkingOrder();
        o.setId(id);
        o.setTenantId(1L);
        o.setParkingLotId(100L);
        o.setParkingRecordId(10L);
        o.setOrderNo("O10020260717" + String.format("%06d", id));
        o.setPlateNumber("粤B12345");
        o.setPayableAmount(500);
        o.setStatus(status);
        return o;
    }

    private ParkingRecord record(Long id, Long tenantId, Long parkingLotId, String plate) {
        ParkingRecord r = new ParkingRecord();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setParkingLotId(parkingLotId);
        r.setStandardizedPlate(plate);
        r.setStatus(ParkingRecord.STATUS_PARKING);
        r.setEntryTime(LocalDateTime.now().minusHours(2));
        return r;
    }
}
