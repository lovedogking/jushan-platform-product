package com.jushan.platform.modules.vehicle.service.impl;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMultiPlateMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletMapper;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
^import com.jushan.platform.modules.miniapp.entity.MonthlyPass;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.service.FixedSpaceService;
import com.jushan.system.service.VehicleListService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link VehicleTypeDecisionServiceImpl} 单元测试（Phase 1 A2 FIXED_SPACE 支持）。
 * <p>
 * 覆盖：固定车位绑定车辆 booth 路径的类型判定。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class VehicleTypeDecisionServiceImplTest {

    @Mock
    private SysVehicleMapper vehicleMapper;
    @Mock
    private SysVehicleMultiPlateMapper multiPlateMapper;
    @Mock
    private SysVehicleWalletMapper walletMapper;
    @Mock
    private FixedSpaceService fixedSpaceService;
    @Mock
    private MonthlyPassMapper monthlyPassMapper;
    @Mock
    private VehicleListService vehicleListService;

    private VehicleTypeDecisionServiceImpl decisionService;

    private SysVehicle vehicle;

    @BeforeEach
    void setUp() {
        decisionService = new VehicleTypeDecisionServiceImpl(
                vehicleMapper, multiPlateMapper, walletMapper, fixedSpaceService, monthlyPassMapper, vehicleListService);

        TenantContext.set(new TenantContext.Snapshot(1L, 100L, "TENANT", "", ""));

        vehicle = new SysVehicle();
        vehicle.setId(10L);
        vehicle.setTenantId(1L);
        vehicle.setParkingLotId(1L);
        vehicle.setPlateNumber("京A12345");
        vehicle.setPlateColor("蓝");
        vehicle.setVehicleType(SysVehicle.TYPE_FREE);
        vehicle.setStatus(SysVehicle.STATUS_ACTIVE);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== FIXED_SPACE 判定 ====================

    @Test
    @DisplayName("固定车位绑定车辆判定为 FIXED_SPACE（免计费）")
    void shouldDecideFixedSpaceWhenActiveBinding() {
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());
        when(fixedSpaceService.hasActiveBindingByVehicleId(10L, 1L, 1L)).thenReturn(true);

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("FIXED_SPACE");
        assertThat(result.getTypeDescription()).isEqualTo("固定车位车辆");
        assertThat(result.getNeedCharge()).isFalse();
        assertThat(result.getAllowEntry()).isTrue();
        assertThat(result.getAllowExit()).isTrue();
        verify(fixedSpaceService).hasActiveBindingByVehicleId(10L, 1L, 1L);
    }

    @Test
    @DisplayName("无固定车位绑定车辆按原始类型判定（FREE→免费车）")
    void shouldFallbackToOriginalTypeWhenNoBinding() {
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());
        when(fixedSpaceService.hasActiveBindingByVehicleId(10L, 1L, 1L)).thenReturn(false);

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("FREE");
        assertThat(result.getNeedCharge()).isFalse();
        assertThat(result.getTypeDescription()).isEqualTo("免费车");
    }

    @Test
    @DisplayName("临时车（无 sys_vehicle 记录）不触发固定车位检查")
    void shouldNotCheckFixedSpaceWhenNoVehicle() {
        when(vehicleMapper.selectByPlateNumber("京X99999", 1L)).thenReturn(null);
        when(multiPlateMapper.selectByPlateNumber("京X99999", 1L)).thenReturn(List.of());

        VehicleTypeDecisionVO result = decisionService.decide("京X99999");

        assertThat(result.getVehicleType()).isEqualTo("TEMP");
        assertThat(result.getNeedCharge()).isTrue();
        verify(fixedSpaceService, never()).hasActiveBindingByVehicleId(any(), any(), any());
    }

    @Test
    @DisplayName("月卡生效中车辆不触发固定车位检查（monthly_pass 短路）")
    void shouldNotCheckFixedSpaceForMonthly() {
        MonthlyPass pass = new MonthlyPass();
        pass.setId(1L);
        pass.setPlateNumber("京A12345");
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setValidStartDate(java.time.LocalDate.now().minusDays(5));
        pass.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

        when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京A12345"), any()))
                .thenReturn(pass);

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
        assertThat(result.getNeedCharge()).isFalse();
        verifyNoInteractions(fixedSpaceService);
        verifyNoInteractions(vehicleMapper);
    }

    // ==================== MONTHLY_PASS 判定（任务包 3-1） ====================

    @Test
    @DisplayName("生效中月卡命中 → 返回 MONTHLY 判定（免费放行）")
    void shouldDecideMonthlyWhenActiveMonthlyPassFound() {
        MonthlyPass pass = new MonthlyPass();
        pass.setId(1L);
        pass.setPlateNumber("京A12345");
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setValidStartDate(java.time.LocalDate.now().minusDays(5));
        pass.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

        when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京A12345"), any()))
                .thenReturn(pass);

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
        assertThat(result.getNeedCharge()).isFalse();
        assertThat(result.getAllowEntry()).isTrue();
        assertThat(result.getAllowExit()).isTrue();
        assertThat(result.getExpired()).isFalse();
        assertThat(result.getDecisionReason()).contains("月卡在有效期内");
    }

    @Test
    @DisplayName("无生效月卡 → 回退 sys_vehicle 类型链判断")
    void shouldFallbackToSysVehicleWhenNoMonthlyPass() {
        when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京A12345"), any()))
                .thenReturn(null);
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());
        when(fixedSpaceService.hasActiveBindingByVehicleId(10L, 1L, 1L)).thenReturn(false);

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("FREE");
        assertThat(result.getNeedCharge()).isFalse();
    }

    @Test
    @DisplayName("月卡命中但车牌不在 sys_vehicle → 仍返回 MONTHLY（月卡短路）")
    void shouldReturnMonthlyEvenWithoutSysVehicleRecord() {
        MonthlyPass pass = new MonthlyPass();
        pass.setId(2L);
        pass.setPlateNumber("京B88888");
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setValidStartDate(java.time.LocalDate.now().minusDays(5));
        pass.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

        when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京B88888"), any()))
                .thenReturn(pass);

        VehicleTypeDecisionVO result = decisionService.decide("京B88888");

        assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
        assertThat(result.getNeedCharge()).isFalse();
        verifyNoInteractions(vehicleMapper);
    }

    @Test
    @DisplayName("黑名单车辆不触发固定车位检查")
    void shouldNotCheckFixedSpaceForBlacklist() {
        vehicle.setVehicleType(SysVehicle.TYPE_BLACKLIST);

        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("BLACKLIST");
        assertThat(result.getAllowEntry()).isFalse();
        verify(fixedSpaceService, never()).hasActiveBindingByVehicleId(any(), any(), any());
    }

    @Test
    @DisplayName("未知类型的车辆有固定车位绑定时判定为 FIXED_SPACE")
    void shouldDecideFixedSpaceWhenUnknownTypeWithBinding() {
        vehicle.setVehicleType(null); // 自动创建记录时未设置类型

        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());
        when(fixedSpaceService.hasActiveBindingByVehicleId(10L, 1L, 1L)).thenReturn(true);

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("FIXED_SPACE");
        assertThat(result.getNeedCharge()).isFalse();
        assertThat(result.getAllowEntry()).isTrue();
        assertThat(result.getAllowExit()).isTrue();
    }
}
