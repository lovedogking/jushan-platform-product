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
import com.jushan.system.service.DeviceService;
import com.jushan.system.service.ExitResult;
import com.jushan.system.service.ExitService;
import com.jushan.system.service.FixedSpaceService;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private FixedSpaceService fixedSpaceService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private com.jushan.framework.lock.DistributedLock distributedLock;

    @Mock
    private com.jushan.system.mapper.BillingRuleRecalcLogMapper recalcLogMapper;

    @Mock
    private com.jushan.system.mapper.ParkingOrderMapper orderMapper;

    @Mock
    private com.jushan.system.service.ParamResolver paramResolver;

    private ExitService exitService;

    @BeforeEach
    void setUp() {
        exitService = new ExitService(recordMapper, exitRecordMapper, parkingOrderService,
                parkingLotMapper, billingEngine, boothWebSocketPublisher, parkingSessionService,
                prepaidDeductionService, fixedSpaceService, deviceService,
                distributedLock, recalcLogMapper, orderMapper, paramResolver);
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
        when(parkingOrderService.createOrder(any(), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT))).thenReturn(mockOrder);
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isTrue();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);
        assertThat(result.getFeeCents()).isZero();

        verify(parkingOrderService).createOrder(any(ParkingRecord.class), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT));
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
        when(parkingOrderService.createOrder(any(), anyInt(), any(), any())).thenReturn(mockOrder);

        // mock 非储值车跳过扣费
        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any()))
                .thenReturn(PrepaidDeductionService.DeductionResult.skipped());

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
        assertThat(result.getFeeCents()).isEqualTo(500);

        verify(parkingOrderService).createOrder(any(ParkingRecord.class), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT));

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

        verify(parkingOrderService, never()).createOrder(any(), anyInt(), any(), any());
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
        when(parkingOrderService.createOrder(any(), anyInt(), any(), any())).thenReturn(mockOrder);

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
        when(parkingOrderService.createOrder(any(), anyInt(), any(), any())).thenReturn(mockOrder);

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
        when(parkingOrderService.createOrder(any(), anyInt(), any(), any())).thenReturn(mockOrder);

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
        when(parkingOrderService.createOrder(any(), anyInt(), any(), any())).thenReturn(mockOrder);

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
        when(parkingOrderService.createOrder(any(), anyInt(), any(), any())).thenReturn(mockOrder);

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

    // ==================== 预订单出场（任务包 1-2） ====================

    @Test
    @DisplayName("存在预订单且有费用：预订单→待支付，不放行")
    void shouldTransitionPreOrderToPendingWhenFeePositive() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(500);

        ParkingOrder preOrder = new ParkingOrder();
        preOrder.setId(777L);
        preOrder.setStatus(ParkingOrder.STATUS_PRE_ORDER);
        when(parkingOrderService.findReusableOrderForRecord(1L)).thenReturn(preOrder);

        ParkingOrder pending = new ParkingOrder();
        pending.setId(777L);
        pending.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.getById(777L)).thenReturn(pending);

        when(prepaidDeductionService.tryDeduct(any(), anyInt(), any()))
                .thenReturn(PrepaidDeductionService.DeductionResult.skipped());

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
        assertThat(result.getFeeCents()).isEqualTo(500);

        // 走预订单计费路径，不再兼容建单
        verify(parkingOrderService).preOrderToPending(eq(777L), eq(500), any(LocalDateTime.class),
                eq(ParkingOrder.PAY_SCENE_AT_EXIT), any());
        verify(parkingOrderService, never()).createOrder(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("存在预订单且零费用：预订单→已完成，放行")
    void shouldCompletePreOrderWhenFree() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(billingEngine.calculateFee(100L, record.getEntryTime(), FIXED_EXIT_TIME)).thenReturn(0);

        ParkingOrder preOrder = new ParkingOrder();
        preOrder.setId(777L);
        preOrder.setStatus(ParkingOrder.STATUS_PRE_ORDER);
        when(parkingOrderService.findReusableOrderForRecord(1L)).thenReturn(preOrder);

        ParkingOrder completed = new ParkingOrder();
        completed.setId(777L);
        completed.setStatus(ParkingOrder.STATUS_COMPLETED);
        when(parkingOrderService.getById(777L)).thenReturn(completed);

        when(recordMapper.update(any(), any())).thenReturn(1);
        when(parkingLotMapper.update(any(), any())).thenReturn(1);

        ExitResult result = exitService.handleExit(exitPayload(1L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result.isAllowExit()).isTrue();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);

        verify(parkingOrderService).preOrderToCompleted(eq(777L), any(LocalDateTime.class));
        verify(parkingOrderService, never()).createOrder(any(), anyInt(), any(), any());
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

    /**
     * TC-1（任务包 2-2）：超时关单后重算——创建新订单并关联原 CANCELLED 订单。
     */
    @Test
    @DisplayName("超时关单后重算：新建订单并关联原 CANCELLED 订单")
    void shouldCreateNewOrderAndLinkToCancelledWhenRecalcAfterTimeout() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);
        when(parkingOrderService.findReusableOrderForRecord(any())).thenReturn(null);

        ParkingOrder cancelledOrder = new ParkingOrder();
        cancelledOrder.setId(100L);
        cancelledOrder.setOrderNo("O1001-001");
        cancelledOrder.setStatus(ParkingOrder.STATUS_CANCELLED);
        cancelledOrder.setAmountCents(2000);
        cancelledOrder.setPayableAmount(2000);
        when(parkingOrderService.findCancelledOrderForRecord(record.getId())).thenReturn(cancelledOrder);

        when(billingEngine.calculateFee(anyLong(), any(), any())).thenReturn(2500);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any())).thenReturn(true);

        ParkingOrder newOrder = new ParkingOrder();
        newOrder.setId(200L);
        newOrder.setOrderNo("O1001-002");
        newOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        newOrder.setAmountCents(2500);
        newOrder.setPayableAmount(2500);
        newOrder.setRecalcSourceOrderId(100L);
        when(parkingOrderService.createOrderInternal(any(), eq(2500), isNull(),
                eq(ParkingOrder.PAY_SCENE_AT_EXIT), any(), eq(100L), any())).thenReturn(newOrder);

        ExitResult result = exitService.handleExit(
                exitPayload(100L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result).isNotNull();
        verify(parkingOrderService).insertRecalcLog(eq(record), eq(100L), eq(200L),
                eq(2000), eq(2500), eq("TIMEOUT_RECALC"));
        verify(parkingOrderService).createOrderInternal(any(), eq(2500), isNull(),
                eq(ParkingOrder.PAY_SCENE_AT_EXIT), any(), eq(100L), any());
        verify(distributedLock).unlock(anyString());
    }

    /**
     * TC-2（任务包 2-2）：PENDING_PAY 订单重识别时更新金额而非新建。
     */
    @Test
    @DisplayName("PENDING_PAY 重复识别：更新金额不新建订单")
    void shouldUpdateExistingPendingOrderAmountOnRescan() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);

        ParkingOrder pendingOrder = new ParkingOrder();
        pendingOrder.setId(150L);
        pendingOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        pendingOrder.setAmountCents(2000);
        pendingOrder.setPayableAmount(2000);
        when(parkingOrderService.findReusableOrderForRecord(any())).thenReturn(pendingOrder);
        when(billingEngine.calculateFee(anyLong(), any(), any())).thenReturn(2200);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any())).thenReturn(true);
        when(parkingOrderService.updatePendingOrderAmount(eq(150L), eq(2200), any())).thenReturn(true);
        when(parkingOrderService.getById(150L)).thenReturn(pendingOrder);

        ExitResult result = exitService.handleExit(
                exitPayload(100L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result).isNotNull();
        verify(parkingOrderService).updatePendingOrderAmount(eq(150L), eq(2200), any());
        verify(parkingOrderService).insertRecalcLog(eq(record), eq(150L), eq(150L),
                eq(2000), eq(2200), eq("EXIT_RESCAN"));
        verify(parkingOrderService, never()).createOrder(any(), anyInt(), anyString(), anyString());
        verify(distributedLock).unlock(anyString());
    }

    /**
     * TC-3（任务包 2-2）：分布式锁失败时降级不抛异常。
     */
    @Test
    @DisplayName("分布式锁获取失败：降级不抛异常")
    void shouldDegradeGracefullyWhenLockAcquisitionFails() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any())).thenReturn(false);
        when(parkingOrderService.findReusableOrderForRecord(any())).thenReturn(null);
        ParkingOrder newOrder = new ParkingOrder();
        newOrder.setId(999L);
        newOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT)))
                .thenReturn(newOrder);

        assertThatCode(() -> exitService.handleExit(
                exitPayload(100L, 100L, 1L, "粤B12345"), "粤B12345"))
                .doesNotThrowAnyException();
        verify(parkingOrderService).createOrder(any(), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT));
        verify(distributedLock, never()).unlock(anyString());
    }
}
