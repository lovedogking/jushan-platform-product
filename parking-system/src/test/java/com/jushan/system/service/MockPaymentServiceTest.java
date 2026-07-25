package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.system.constant.ParamKeys;
import com.jushan.platform.modules.miniapp.entity.MockPaymentConfig;
import com.jushan.platform.modules.miniapp.entity.MockPaymentRecord;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.system.mapper.MockPaymentConfigMapper;
import com.jushan.system.mapper.MockPaymentRecordMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MockPaymentService 单元测试（P0 S0-3）。
 * <p>
 * 覆盖：正常支付、超时自动关闭、重复支付幂等、手动标记支付。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class MockPaymentServiceTest {

    @Mock
    private MockPaymentConfigMapper configMapper;
    @Mock
    private MockPaymentRecordMapper recordMapper;
    @Mock
    private ParkingOrderMapper orderMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ParamResolver paramResolver;
    @Mock
    private OrderStatusLogService orderStatusLogService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private ParkingOrderService parkingOrderService;

    private MockPaymentService mockPaymentService;

    private ParkingOrder pendingOrder;
    private MockPaymentConfig defaultConfig;

    @BeforeEach
    void setUp() {
        mockPaymentService = new MockPaymentService(configMapper, recordMapper, orderMapper,
                eventPublisher, paramResolver, orderStatusLogService, deviceService, parkingOrderService);

        defaultConfig = new MockPaymentConfig();
        defaultConfig.setId(1L);
        defaultConfig.setTenantId(1L);
        defaultConfig.setParkingLotId(1L);
        defaultConfig.setTimeoutMinutes(15);
        defaultConfig.setEnabled(true);

        pendingOrder = new ParkingOrder();
        pendingOrder.setId(100L);
        pendingOrder.setTenantId(1L);
        pendingOrder.setParkingLotId(1L);
        pendingOrder.setOrderNo("O100120260716000001");
        pendingOrder.setPlateNumber("京A12345");
        pendingOrder.setPayableAmount(500); // 5元
        pendingOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        pendingOrder.setExpiredAt(LocalDateTime.now().plusMinutes(15));
    }

    // ==================== 准备支付 ====================

    @Test
    @DisplayName("准备支付成功创建模拟支付记录")
    void shouldCreatePaymentRecordOnPrepare() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(defaultConfig);
        doAnswer(invocation -> {
            MockPaymentRecord r = invocation.getArgument(0);
            r.setId(200L); // simulate ID generation after insert
            return 1;
        }).when(recordMapper).insert(any(MockPaymentRecord.class));

        Long recordId = mockPaymentService.preparePay(pendingOrder);

        assertThat(recordId).isNotNull();
        verify(configMapper).selectOne(any(QueryWrapper.class));
        verify(orderMapper).updateOrderExpiry(eq(100L), any(LocalDateTime.class));
        verify(recordMapper).insert(any(MockPaymentRecord.class));
    }

    @Test
    @DisplayName("准备支付时自动创建默认配置（配置不存在）")
    void shouldCreateDefaultConfigWhenMissing() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            MockPaymentRecord r = invocation.getArgument(0);
            r.setId(200L);
            return 1;
        }).when(recordMapper).insert(any(MockPaymentRecord.class));

        Long recordId = mockPaymentService.preparePay(pendingOrder);

        assertThat(recordId).isNotNull();
        verify(configMapper).insert(any(MockPaymentConfig.class));
    }

    // ==================== 确认支付 ====================

    @Test
    @DisplayName("确认支付成功")
    void shouldConfirmPaySuccessfully() {
        when(orderMapper.selectById(100L)).thenReturn(pendingOrder);
        when(orderMapper.markPaidStatus(eq(100L), any(), eq(500), any())).thenReturn(1);

        boolean result = mockPaymentService.confirmPay(100L, "miniapp");

        assertThat(result).isTrue();
        verify(orderMapper).markPaidStatus(eq(100L), any(String.class), eq(500), any(LocalDateTime.class));
        verify(eventPublisher).publishEvent(isA(com.jushan.system.event.PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("确认支付时订单不存在返回 false 且不发布事件")
    void shouldFailWhenOrderNotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        boolean result = mockPaymentService.confirmPay(999L, "miniapp");

        assertThat(result).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("确认支付时订单已过期返回 false")
    void shouldFailWhenOrderExpired() {
        pendingOrder.setExpiredAt(LocalDateTime.now().minusMinutes(5));
        when(orderMapper.selectById(100L)).thenReturn(pendingOrder);

        boolean result = mockPaymentService.confirmPay(100L, "miniapp");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("确认支付时订单状态异常返回 false")
    void shouldFailWhenOrderStatusInvalid() {
        pendingOrder.setStatus(ParkingOrder.STATUS_CANCELLED);
        when(orderMapper.selectById(100L)).thenReturn(pendingOrder);

        boolean result = mockPaymentService.confirmPay(100L, "miniapp");

        assertThat(result).isFalse();
    }

    // ==================== 超时关闭 ====================

    @Test
    @DisplayName("定时任务关闭超时订单")
    void shouldCloseExpiredOrders() {
        // 模拟一个已过期的订单
        pendingOrder.setExpiredAt(LocalDateTime.now().minusMinutes(5));

        // 有一个过期的 mock 支付记录
        MockPaymentRecord mockRecord = new MockPaymentRecord();
        mockRecord.setId(10L);
        mockRecord.setOrderId(100L);
        mockRecord.setStatus(MockPaymentRecord.STATUS_PENDING);

        when(orderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(pendingOrder));
        when(recordMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockRecord);

        mockPaymentService.timeoutClose();

        verify(orderMapper).cancelExpiredOrder(100L);
        verify(recordMapper).updateById(any(MockPaymentRecord.class));
    }

    @Test
    @DisplayName("定时任务没有超时订单时不做任何操作")
    void shouldSkipWhenNoExpiredOrders() {
        when(orderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        mockPaymentService.timeoutClose();

        verify(orderMapper).selectList(any(QueryWrapper.class));
    }

    // ==================== 手动标记支付 ====================

    @Test
    @DisplayName("手动标记支付成功")
    void shouldManualMarkPaidSuccessfully() {
        when(orderMapper.selectById(100L)).thenReturn(pendingOrder);
        when(orderMapper.markPaidStatus(eq(100L), any(), eq(500), any())).thenReturn(1);
        when(recordMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        boolean result = mockPaymentService.manualMarkPaid(100L, "admin-1");

        assertThat(result).isTrue();
        verify(orderMapper).markPaidStatus(eq(100L), any(), eq(500), any(LocalDateTime.class));
        verify(recordMapper).insert(any(MockPaymentRecord.class));
        verify(eventPublisher).publishEvent(isA(com.jushan.system.event.PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("手动标记支付时订单不存在返回 false")
    void shouldManualMarkPaidFailWhenOrderNotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        boolean result = mockPaymentService.manualMarkPaid(999L, "admin-1");

        assertThat(result).isFalse();
    }

    // ==================== 配置管理 ====================

    @Test
    @DisplayName("获取或创建配置，存在则返回")
    void shouldReturnExistingConfig() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(defaultConfig);

        MockPaymentConfig config = mockPaymentService.getOrCreateConfig(1L, 1L);

        assertThat(config.getTimeoutMinutes()).isEqualTo(15);
        assertThat(config.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("获取或创建配置，不存在则创建默认")
    void shouldCreateDefaultWhenConfigMissing() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        MockPaymentConfig config = mockPaymentService.getOrCreateConfig(1L, 1L);

        assertThat(config.getTimeoutMinutes()).isEqualTo(15);
        assertThat(config.getEnabled()).isTrue();
        verify(configMapper).insert(any(MockPaymentConfig.class));
    }

    @Test
    @DisplayName("更新车场配置")
    void shouldUpdateConfig() {
        defaultConfig.setTenantId(1L);
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(defaultConfig);

        MockPaymentConfig updated = mockPaymentService.updateConfig(1L, 1L, 30, true);

        assertThat(updated.getTimeoutMinutes()).isEqualTo(30);
        // 超时时长权威写入车场级参数体系（任务包 1-1）
        verify(paramResolver).setLotParam(eq(1L), eq(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES), eq("30"));
        verify(configMapper).updateById(any(MockPaymentConfig.class));
    }
}
