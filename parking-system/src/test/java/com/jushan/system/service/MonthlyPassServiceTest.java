package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.VehicleRenewalLogMapper;
import com.jushan.system.vo.MonthlyPassVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * {@link MonthlyPassService} 单元测试（任务包 3-1：基于 monthly_pass 实体）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class MonthlyPassServiceTest {

    @Mock private MonthlyPassMapper monthlyPassMapper;
    @Mock private ParkingLotMapper parkingLotMapper;
    @Mock private ParkingOrderMapper parkingOrderMapper;
    @Mock private VehicleRenewalLogMapper renewalLogMapper;
    @Mock private ParamResolver paramResolver;

    private MonthlyPassService service;

    private static final Long TENANT_ID = 1L;
    private static final Long PARKING_LOT_ID = 10L;
    private static final String PLATE = "京A12345";

    @BeforeEach
    void setUp() {
        service = new MonthlyPassService(monthlyPassMapper, parkingLotMapper,
                parkingOrderMapper, renewalLogMapper, paramResolver);
        TenantContext.set(new TenantContext.Snapshot(TENANT_ID, 100L, "TENANT", "", ""));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 创建月卡 ====================

    @Test
    @DisplayName("录入月卡：创建 monthly_pass 并生成已支付 MONTHLY_PASS 订单")
    void shouldCreateMonthlyPassAndPaidOrder() {
        MonthlyPassCreateRequest req = buildCreateRequest();
        when(monthlyPassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(PARKING_LOT_ID)).thenReturn(lot("测试车场"));

        MonthlyPassVO result = service.create(req);

        assertThat(result.getPlateNumber()).isEqualTo(PLATE);
        assertThat(result.getPassStatus()).isEqualTo("ACTIVE");
        assertThat(result.getSource()).isEqualTo("ADMIN");
        assertThat(result.getPayMethod()).isEqualTo("CASH");
        assertThat(result.getPaidAmountCents()).isEqualTo(30000);

        ArgumentCaptor<MonthlyPass> passCaptor = ArgumentCaptor.forClass(MonthlyPass.class);
        verify(monthlyPassMapper).insert(passCaptor.capture());
        MonthlyPass inserted = passCaptor.getValue();
        assertThat(inserted.getPlateNumber()).isEqualTo(PLATE);
        assertThat(inserted.getPassStatus()).isEqualTo(MonthlyPass.STATUS_ACTIVE);

        ArgumentCaptor<ParkingOrder> orderCaptor = ArgumentCaptor.forClass(ParkingOrder.class);
        verify(parkingOrderMapper).insert(orderCaptor.capture());
        ParkingOrder order = orderCaptor.getValue();
        assertThat(order.getOrderType()).isEqualTo(ParkingOrder.ORDER_TYPE_MONTHLY_PASS);
        assertThat(order.getStatus()).isEqualTo(ParkingOrder.STATUS_PAID);
    }

    @Test
    @DisplayName("重复办理同车场同车牌生效中月卡 → 拒绝")
    void shouldRejectDuplicateActiveMonthlyPass() {
        MonthlyPassCreateRequest req = buildCreateRequest();
        when(monthlyPassMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复登记");
    }

    // ==================== 续期 ====================

    @Test
    @DisplayName("续期：有效期正确顺延，生成续费订单和日志")
    void shouldRenewAndCreateOrderAndLog() {
        MonthlyPass existing = buildMonthlyPass();
        when(monthlyPassMapper.selectById(existing.getId())).thenReturn(existing);
        when(monthlyPassMapper.updateById(any(MonthlyPass.class))).thenReturn(1);
        when(parkingLotMapper.selectById(PARKING_LOT_ID)).thenReturn(lot("测试车场"));

        LocalDate oldEnd = existing.getValidEndDate();

        MonthlyPassRenewRequest renewReq = new MonthlyPassRenewRequest();
        renewReq.setRenewalMonths(3);
        renewReq.setAmountCents(9000);
        renewReq.setRemark("续费3个月");

        MonthlyPassVO result = service.renew(existing.getId(), renewReq);

        assertThat(result.getValidEndDate()).isEqualTo(oldEnd.plusMonths(3));

        ArgumentCaptor<ParkingOrder> orderCaptor = ArgumentCaptor.forClass(ParkingOrder.class);
        verify(parkingOrderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo(ParkingOrder.ORDER_TYPE_MONTH_RENEW);

        ArgumentCaptor<VehicleRenewalLog> logCaptor = ArgumentCaptor.forClass(VehicleRenewalLog.class);
        verify(renewalLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getMonthlyPassId()).isEqualTo(existing.getId());
        assertThat(logCaptor.getValue().getRenewalMonths()).isEqualTo(3);
    }

    // ==================== 注销 ====================

    @Test
    @DisplayName("注销：状态置为 CANCELLED")
    void shouldCancelMonthlyPass() {
        MonthlyPass existing = buildMonthlyPass();
        when(monthlyPassMapper.selectById(existing.getId())).thenReturn(existing);
        when(monthlyPassMapper.updateById(any(MonthlyPass.class))).thenReturn(1);
        when(parkingLotMapper.selectById(PARKING_LOT_ID)).thenReturn(lot("测试车场"));

        MonthlyPassVO result = service.cancel(existing.getId());

        assertThat(result.getPassStatus()).isEqualTo(MonthlyPass.STATUS_CANCELLED);
    }

    // ==================== 列表查询 ====================

    @Test
    @DisplayName("分页列表：查询 monthly_pass 表")
    void shouldPageListMonthlyPasses() {
        MonthlyPass pass = buildMonthlyPass();
        Page<MonthlyPass> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(pass));
        mockPage.setTotal(1);

        when(monthlyPassMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);
        when(parkingLotMapper.selectBatchIds(anyList())).thenReturn(List.of(lot("测试车场")));

        IPage<MonthlyPassVO> result = service.pageList(null, null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getPlateNumber()).isEqualTo(PLATE);
    }

    // ==================== helpers ====================

    private MonthlyPassCreateRequest buildCreateRequest() {
        MonthlyPassCreateRequest req = new MonthlyPassCreateRequest();
        req.setPlateNumber(PLATE);
        req.setParkingLotId(PARKING_LOT_ID);
        req.setValidStartDate(LocalDate.now());
        req.setValidEndDate(LocalDate.now().plusMonths(1));
        req.setPaidAmountCents(30000);
        req.setPayMethod("CASH");
        req.setOwnerName("张三");
        req.setOwnerPhone("13800001111");
        return req;
    }

    private MonthlyPass buildMonthlyPass() {
        MonthlyPass pass = new MonthlyPass();
        pass.setId(1L);
        pass.setTenantId(TENANT_ID);
        pass.setParkingLotId(PARKING_LOT_ID);
        pass.setPlateNumber(PLATE);
        pass.setValidStartDate(LocalDate.now().minusDays(5));
        pass.setValidEndDate(LocalDate.now().plusMonths(1));
        pass.setPaidAmountCents(30000);
        pass.setPayMethod("CASH");
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setSource(MonthlyPass.SOURCE_ADMIN);
        pass.setOwnerName("张三");
        pass.setOwnerPhone("13800001111");
        pass.setCreatedAt(LocalDateTime.now());
        pass.setUpdatedAt(LocalDateTime.now());
        return pass;
    }

    private ParkingLot lot(String name) {
        ParkingLot lot = new ParkingLot();
        lot.setId(PARKING_LOT_ID);
        lot.setName(name);
        return lot;
    }
}
