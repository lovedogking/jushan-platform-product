package com.jushan.platform.modules.vehicle.service.impl;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMultiPlateMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletMapper;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import com.jushan.system.service.FixedSpaceService;
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

    private VehicleTypeDecisionServiceImpl decisionService;

    private SysVehicle vehicle;

    @BeforeEach
    void setUp() {
        decisionService = new VehicleTypeDecisionServiceImpl(
                vehicleMapper, multiPlateMapper, walletMapper, fixedSpaceService);

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
    @DisplayName("月卡车辆不触发固定车位检查")
    void shouldNotCheckFixedSpaceForMonthly() {
        vehicle.setVehicleType(SysVehicle.TYPE_MONTHLY);
        vehicle.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());

        VehicleTypeDecisionVO result = decisionService.decide("京A12345");

        assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
        assertThat(result.getNeedCharge()).isFalse();
        verify(fixedSpaceService, never()).hasActiveBindingByVehicleId(any(), any(), any());
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
