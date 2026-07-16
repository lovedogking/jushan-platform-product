package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.dto.VehicleRenewalCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.service.VehicleRenewalService;
import com.jushan.platform.modules.vehicle.vo.RenewalOrderVO;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.ParkingLotMapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MonthlyPassService} 单元测试（Phase 1 A1）。
 * <p>
 * 覆盖：列表查询、到期预警、登记、续期、注销、重复校验。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class MonthlyPassServiceTest {

    @Mock
    private SysVehicleMapper vehicleMapper;
    @Mock
    private ParkingLotMapper parkingLotMapper;
    @Mock
    private VehicleRenewalService renewalService;
    @Mock
    private VehicleRenewalLogMapper renewalLogMapper;

    private MonthlyPassService monthlyPassService;

    private SysVehicle monthlyVehicle;
    private ParkingLot parkingLot;

    @BeforeEach
    void setUp() {
        monthlyPassService = new MonthlyPassService(
                vehicleMapper, parkingLotMapper, renewalService, renewalLogMapper);

        // 设置测试租户上下文
        TenantContext.set(new TenantContext.Snapshot(1L, 100L, "TENANT", "", ""));

        monthlyVehicle = new SysVehicle();
        monthlyVehicle.setId(10L);
        monthlyVehicle.setTenantId(1L);
        monthlyVehicle.setParkingLotId(1L);
        monthlyVehicle.setPlateNumber("京A12345");
        monthlyVehicle.setVehicleType(SysVehicle.TYPE_MONTHLY);
        monthlyVehicle.setValidStartDate(LocalDate.of(2026, 1, 1));
        monthlyVehicle.setValidEndDate(LocalDate.of(2026, 12, 31));
        monthlyVehicle.setStatus(SysVehicle.STATUS_ACTIVE);
        monthlyVehicle.setOwnerName("张三");
        monthlyVehicle.setCreatedAt(LocalDateTime.now());

        parkingLot = new ParkingLot();
        parkingLot.setId(1L);
        parkingLot.setName("测试车场");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 列表查询 ====================

    @Test
    @DisplayName("分页查询月卡列表")
    void shouldPageListMonthlyPasses() {
        when(vehicleMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<SysVehicle> page = invocation.getArgument(0);
                    page.setRecords(List.of(monthlyVehicle));
                    page.setTotal(1);
                    return page;
                });
        when(parkingLotMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(parkingLot));

        IPage<MonthlyPassVO> result = monthlyPassService.pageList(null, null, null, null, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getPlateNumber()).isEqualTo("京A12345");
        assertThat(result.getRecords().get(0).getParkingLotName()).isEqualTo("测试车场");
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("分页查询返回空列表")
    void shouldReturnEmptyPageWhenNoData() {
        when(vehicleMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<SysVehicle> page = invocation.getArgument(0);
                    page.setRecords(List.of());
                    page.setTotal(0);
                    return page;
                });

        IPage<MonthlyPassVO> result = monthlyPassService.pageList(null, null, null, null, null, 1, 10);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    // ==================== 到期预警 ====================

    @Test
    @DisplayName("到期预警列表展示即将到期的月卡")
    void shouldReturnExpiringList() {
        monthlyVehicle.setValidEndDate(LocalDate.now().plusDays(3));
        when(vehicleMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<SysVehicle> page = invocation.getArgument(0);
                    page.setRecords(List.of(monthlyVehicle));
                    page.setTotal(1);
                    return page;
                });
        when(parkingLotMapper.selectBatchIds(any())).thenReturn(List.of(parkingLot));

        IPage<MonthlyPassVO> result = monthlyPassService.expiringList(7, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        verify(vehicleMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("没有即将到期的月卡时返回空列表")
    void shouldReturnEmptyIfNoExpiring() {
        when(vehicleMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<SysVehicle> page = invocation.getArgument(0);
                    page.setRecords(List.of());
                    page.setTotal(0);
                    return page;
                });

        IPage<MonthlyPassVO> result = monthlyPassService.expiringList(7, 1, 10);

        assertThat(result.getRecords()).isEmpty();
    }

    // ==================== 登记 ====================

    @Test
    @DisplayName("登记月卡成功")
    void shouldCreateMonthlyPass() {
        when(vehicleMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);

        MonthlyPassCreateRequest request = new MonthlyPassCreateRequest();
        request.setPlateNumber("京B67890");
        request.setParkingLotId(1L);
        request.setValidStartDate(LocalDate.of(2026, 7, 1));
        request.setValidEndDate(LocalDate.of(2027, 6, 30));
        request.setOwnerName("李四");
        request.setAmountCents(0);

        MonthlyPassVO result = monthlyPassService.create(request);

        assertThat(result.getPlateNumber()).isEqualTo("京B67890");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getParkingLotName()).isEqualTo("测试车场");
        verify(vehicleMapper).insert(any(SysVehicle.class));
    }

    @Test
    @DisplayName("同一车场同一车牌重复登记时抛出异常")
    void shouldRejectDuplicateMonthlyPass() {
        when(vehicleMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        MonthlyPassCreateRequest request = new MonthlyPassCreateRequest();
        request.setPlateNumber("京A12345");
        request.setParkingLotId(1L);
        request.setValidStartDate(LocalDate.of(2026, 7, 1));
        request.setValidEndDate(LocalDate.of(2027, 6, 30));

        assertThatThrownBy(() -> monthlyPassService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复登记");
    }

    // ==================== 续期 ====================

    @Test
    @DisplayName("月卡续期成功")
    void shouldRenewMonthlyPass() {
        when(vehicleMapper.selectById(10L)).thenReturn(monthlyVehicle);
        when(renewalService.createRenewalOrder(eq(10L), any(VehicleRenewalCmd.class)))
                .thenAnswer(invocation -> {
                    RenewalOrderVO vo = new RenewalOrderVO();
                    vo.setOrderId(200L);
                    vo.setOrderNo("MR120260716000001");
                    return vo;
                });
        when(renewalService.applyRenewalEffect(eq(200L), anyString())).thenReturn(new RenewalOrderVO());

        // After renewal, vehicle's validEndDate is extended
        monthlyVehicle.setValidEndDate(LocalDate.of(2027, 1, 31));
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);

        MonthlyPassRenewRequest request = new MonthlyPassRenewRequest();
        request.setRenewalMonths(1);
        request.setAmountCents(50000); // 500元

        MonthlyPassVO result = monthlyPassService.renew(10L, request);

        assertThat(result).isNotNull();
        verify(renewalService).createRenewalOrder(eq(10L), any(VehicleRenewalCmd.class));
        verify(renewalService).applyRenewalEffect(eq(200L), anyString());
        verify(renewalLogMapper).insert(any(VehicleRenewalLog.class));
    }

    @Test
    @DisplayName("非月卡类型车辆续期抛出异常")
    void shouldRejectRenewForNonMonthly() {
        monthlyVehicle.setVehicleType(SysVehicle.TYPE_FREE);
        when(vehicleMapper.selectById(10L)).thenReturn(monthlyVehicle);

        MonthlyPassRenewRequest request = new MonthlyPassRenewRequest();
        request.setRenewalMonths(1);
        request.setAmountCents(0);

        assertThatThrownBy(() -> monthlyPassService.renew(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是月卡");
    }

    @Test
    @DisplayName("跨租户车辆续期抛出异常")
    void shouldRejectRenewForDifferentTenant() {
        monthlyVehicle.setTenantId(999L);
        when(vehicleMapper.selectById(10L)).thenReturn(monthlyVehicle);

        MonthlyPassRenewRequest request = new MonthlyPassRenewRequest();
        request.setRenewalMonths(1);
        request.setAmountCents(0);

        assertThatThrownBy(() -> monthlyPassService.renew(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    // ==================== 注销 ====================

    @Test
    @DisplayName("月卡注销成功")
    void shouldCancelMonthlyPass() {
        when(vehicleMapper.selectById(10L)).thenReturn(monthlyVehicle);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);

        MonthlyPassVO result = monthlyPassService.cancel(10L);

        assertThat(result.getVehicleType()).isEqualTo("FREE");
        assertThat(result.getStatus()).isEqualTo("DISABLED");
        verify(vehicleMapper).updateById(any(SysVehicle.class));
    }

    @Test
    @DisplayName("非月卡注销抛出异常")
    void shouldRejectCancelForNonMonthly() {
        monthlyVehicle.setVehicleType(SysVehicle.TYPE_FREE);
        when(vehicleMapper.selectById(10L)).thenReturn(monthlyVehicle);

        assertThatThrownBy(() -> monthlyPassService.cancel(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是月卡");
    }
}
