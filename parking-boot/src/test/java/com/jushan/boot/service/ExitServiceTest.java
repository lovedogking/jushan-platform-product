package com.jushan.boot.service;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.ExitRecord;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.event.EventSource;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.ExitRecordMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.service.BillingEngine;
import com.jushan.system.service.ExitResult;
import com.jushan.system.service.ExitService;
import com.jushan.system.service.PrepaidDeductionService;
import com.jushan.system.ws.BoothWebSocketPublisher;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出口处理服务单元测试（P004）。
 * <p>
 * 使用 SpringBootTest 加载 MyBatis-Plus 全局配置，确保 Lambda 缓存已初始化。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class ExitServiceTest extends TestcontainersBaseTest {

    @Mock
    private ParkingRecordMapper recordMapper;

    @Mock
    private ExitRecordMapper exitRecordMapper;

    @Mock
    private ParkingOrderService parkingOrderService;

    @Mock
    private ParkingLotMapper parkingLotMapper;

    @Mock
    private BillingEngine billingEngine;

    @Mock
    private BoothWebSocketPublisher boothWebSocketPublisher;

    @Mock
    private ParkingSessionService parkingSessionService;

    @Mock
    private PrepaidDeductionService prepaidDeductionService;

    private ExitService exitService;

    @BeforeEach
    void setUp() {
        exitService = new ExitService(recordMapper, exitRecordMapper, parkingOrderService, parkingLotMapper, billingEngine, boothWebSocketPublisher, parkingSessionService, prepaidDeductionService);
    }

    @Test
    @DisplayName("零费订单：放行，完成停车记录和订单")
    void shouldReleaseForZeroFee() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(0);
        when(recordMapper.update(any(), any())).thenReturn(1);
        when(parkingLotMapper.update(any(), any())).thenReturn(1);
        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_COMPLETED);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isTrue();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);
        assertThat(result.getFeeCents()).isZero();

        verify(parkingOrderService).createOrder(any(ParkingRecord.class), anyInt(), isNull());
        verify(parkingOrderService).completeOrder(any(Long.class), any(LocalDateTime.class));

        verify(recordMapper).update(any(), any());
        verify(parkingLotMapper).update(any(), any());

        ArgumentCaptor<ExitRecord> exitCaptor = ArgumentCaptor.forClass(ExitRecord.class);
        verify(exitRecordMapper).insert(exitCaptor.capture());
        assertThat(exitCaptor.getValue().getReleaseDecision()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);
    }

    @Test
    @DisplayName("有费用订单：待支付，不放行，不完成停车记录")
    void shouldNotReleaseWhenPendingPayment() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(500);
        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        // mock 非储值车跳过扣费
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any()))
                .thenReturn(PrepaidDeductionService.DeductionResult.skipped());

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
        assertThat(result.getFeeCents()).isEqualTo(500);

        verify(parkingOrderService).createOrder(any(ParkingRecord.class), anyInt(), isNull());

        verify(recordMapper, never()).update(any(), any());
        verify(parkingLotMapper, never()).update(any(), any());
        verify(parkingOrderService, never()).completeOrder(any(), any());

        ArgumentCaptor<ExitRecord> exitCaptor = ArgumentCaptor.forClass(ExitRecord.class);
        verify(exitRecordMapper).insert(exitCaptor.capture());
        assertThat(exitCaptor.getValue().getReleaseDecision()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
    }

    @Test
    @DisplayName("无在场记录：创建 NO_RECORD 出场记录")
    void shouldCreateNoRecordWhenNoActiveParking() {
        when(recordMapper.selectList(any())).thenReturn(Collections.emptyList());

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_NO_RECORD);

        verify(parkingOrderService, never()).createOrder(any(), anyInt(), any());
        verify(recordMapper, never()).update(any(), any());

        ArgumentCaptor<ExitRecord> exitCaptor = ArgumentCaptor.forClass(ExitRecord.class);
        verify(exitRecordMapper).insert(exitCaptor.capture());
        assertThat(exitCaptor.getValue().getReleaseDecision()).isEqualTo(ExitRecord.DECISION_NO_RECORD);
    }

    @Test
    @DisplayName("并发下停车记录状态已变更：仍创建出场记录但不重复减容量")
    void shouldHandleConcurrentRecordCompletion() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(0);
        when(recordMapper.update(any(), any())).thenReturn(0);
        ParkingOrder mockOrder3 = new ParkingOrder();
        mockOrder3.setId(999L);
        mockOrder3.setStatus(ParkingOrder.STATUS_COMPLETED);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder3);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isTrue();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);

        verify(recordMapper).update(any(), any());
        verify(parkingLotMapper, never()).update(any(), any());
    }

    // ==================== 储值车余额扣费测试 ====================

    @Test
    @DisplayName("储值车余额充足：全额扣费，订单标记 PAID，放行")
    void shouldDeductFullBalanceForPrepaidVehicle() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(500);

        // 创建初始订单（PENDING_PAY）
        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        // mock 储值车余额充足：全额扣除 500 分
        PrepaidDeductionService.DeductionResult deduction =
                PrepaidDeductionService.DeductionResult.fullCoverage(500, 1001L);
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any())).thenReturn(deduction);
        when(parkingOrderService.applyBalancePayment(eq(999L), eq(500), eq(true))).thenReturn(true);

        // 扣费后订单变为 PAID
        ParkingOrder paidOrder = new ParkingOrder();
        paidOrder.setId(999L);
        paidOrder.setStatus(ParkingOrder.STATUS_PAID);
        when(parkingOrderService.getById(999L)).thenReturn(paidOrder);

        when(recordMapper.update(any(), any())).thenReturn(1);
        when(parkingLotMapper.update(any(), any())).thenReturn(1);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        // 验证：余额充足应允许出场
        assertThat(result.isAllowExit()).isTrue();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PAID);
        assertThat(result.getFeeCents()).isEqualTo(500);

        // 验证扣费流程被调用
        verify(prepaidDeductionService).tryDeduct(any(ParkingRecord.class), eq(500), any(ParkingOrder.class));
        verify(parkingOrderService).applyBalancePayment(eq(999L), eq(500), eq(true));

        // 验证停车记录完成
        verify(recordMapper).update(any(), any());
        verify(parkingLotMapper).update(any(), any());
        verify(parkingOrderService).completeOrder(eq(999L), any(LocalDateTime.class));

        // 验证出场记录 paidCents 正确
        ArgumentCaptor<ExitRecord> exitCaptor = ArgumentCaptor.forClass(ExitRecord.class);
        verify(exitRecordMapper).insert(exitCaptor.capture());
        assertThat(exitCaptor.getValue().getPaidCents()).isEqualTo(500);
        assertThat(exitCaptor.getValue().getReleaseDecision()).isEqualTo(ExitRecord.DECISION_PAID);
    }

    @Test
    @DisplayName("储值车余额不足：部分扣费，订单保持 PENDING_PAY，不放行")
    void shouldDeductPartialBalanceForPrepaidVehicle() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(1500);

        // 创建初始订单（PENDING_PAY）
        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        // mock 储值车余额不足：仅扣除 1000 分，剩余 500 分
        PrepaidDeductionService.DeductionResult deduction =
                PrepaidDeductionService.DeductionResult.partialCoverage(1000, 500, 1002L);
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any())).thenReturn(deduction);
        when(parkingOrderService.applyBalancePayment(eq(999L), eq(1000), eq(false))).thenReturn(true);

        // 扣费后订单仍为 PENDING_PAY
        ParkingOrder partialPaidOrder = new ParkingOrder();
        partialPaidOrder.setId(999L);
        partialPaidOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.getById(999L)).thenReturn(partialPaidOrder);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        // 验证：余额不足不放行
        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
        assertThat(result.getFeeCents()).isEqualTo(1500);

        // 验证扣费流程被调用
        verify(prepaidDeductionService).tryDeduct(any(ParkingRecord.class), eq(1500), any(ParkingOrder.class));
        verify(parkingOrderService).applyBalancePayment(eq(999L), eq(1000), eq(false));

        // 验证未完成停车记录（因为余额不足，不放行）
        verify(recordMapper, never()).update(any(), any());
        verify(parkingLotMapper, never()).update(any(), any());
        verify(parkingOrderService, never()).completeOrder(any(), any());

        // 验证出场记录 paidCents 为部分扣除金额
        ArgumentCaptor<ExitRecord> exitCaptor = ArgumentCaptor.forClass(ExitRecord.class);
        verify(exitRecordMapper).insert(exitCaptor.capture());
        assertThat(exitCaptor.getValue().getPaidCents()).isEqualTo(1000);
        assertThat(exitCaptor.getValue().getFeeCents()).isEqualTo(1500);
        assertThat(exitCaptor.getValue().getReleaseDecision()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
    }

    @Test
    @DisplayName("非储值车：跳过余额扣费，正常流程")
    void shouldSkipDeductionForNonPrepaidVehicle() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(500);

        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        // mock 非储值车：跳过扣费
        PrepaidDeductionService.DeductionResult skipped =
                PrepaidDeductionService.DeductionResult.skipped();
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any())).thenReturn(skipped);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        // 验证：正常待支付流程
        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);

        // 验证未调用余额支付
        verify(prepaidDeductionService).tryDeduct(any(ParkingRecord.class), eq(500), any(ParkingOrder.class));
        verify(parkingOrderService, never()).applyBalancePayment(any(), anyInt(), any(Boolean.class));

        // 验证出场记录 paidCents 为 0
        ArgumentCaptor<ExitRecord> exitCaptor = ArgumentCaptor.forClass(ExitRecord.class);
        verify(exitRecordMapper).insert(exitCaptor.capture());
        assertThat(exitCaptor.getValue().getPaidCents()).isZero();
    }

    @Test
    @DisplayName("储值车钱包余额为零：触发告警，跳过扣费，不放行")
    void shouldAlertWhenPrepaidWalletHasNoBalance() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(500);

        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        // mock 余额为零
        PrepaidDeductionService.DeductionResult noBalance =
                PrepaidDeductionService.DeductionResult.noBalance();
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any())).thenReturn(noBalance);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        // 验证：待支付，不放行
        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);

        // 验证未调用余额支付
        verify(prepaidDeductionService).tryDeduct(any(ParkingRecord.class), eq(500), any(ParkingOrder.class));
        verify(parkingOrderService, never()).applyBalancePayment(any(), anyInt(), any(Boolean.class));
    }

    @Test
    @DisplayName("储值车扣费并发冲突：抛出异常，事务回滚")
    void shouldThrowOnConcurrentDeductionConflict() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(500);

        ParkingOrder mockOrder = new ParkingOrder();
        mockOrder.setId(999L);
        mockOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), any())).thenReturn(mockOrder);

        // mock 并发冲突：乐观锁版本不匹配
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.CONFLICT, "扣费失败，请重试（并发冲突）"));

        try {
            exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT);
            assertThat(e.getMessage()).contains("并发冲突");
        }

        // 验证：余额支付未被调用（因为 deduction 本身抛异常）
        verify(prepaidDeductionService).tryDeduct(any(ParkingRecord.class), eq(500), any(ParkingOrder.class));
        verify(parkingOrderService, never()).applyBalancePayment(any(), anyInt(), any(Boolean.class));

        // 验证：出场记录未创建（事务回滚）
        verify(exitRecordMapper, never()).insert(any(ExitRecord.class));
    }

    private ParkingRecord activeRecord(Long id, Long tenantId, Long parkingLotId, String plate) {
        ParkingRecord record = new ParkingRecord();
        record.setId(id);
        record.setTenantId(tenantId);
        record.setParkingLotId(parkingLotId);
        record.setStandardizedPlate(plate);
        record.setStatus("PARKING");
        record.setEntryTime(FIXED_EXIT_TIME.minusHours(2));
        return record;
    }

    private RecognitionEventPayload exitPayload(Long tenantId, Long parkingLotId, Long deviceId, String plate) {
        return RecognitionEventPayload.of(UUID.randomUUID().toString(), plate, "EXIT", EventSource.MOCK)
                .tenantId(tenantId)
                .parkingLotId(parkingLotId)
                .deviceId(deviceId)
                .laneId(2L)
                .eventTime(FIXED_EXIT_TIME);
    }

    private static final LocalDateTime FIXED_EXIT_TIME = LocalDateTime.of(2026, 7, 12, 12, 0, 0);
}
