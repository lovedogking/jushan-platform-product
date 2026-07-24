package com.jushan.platform.modules.vehicle.service.impl;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.vehicle.dto.VehicleRenewalCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.vo.RenewalOrderVO;
import com.jushan.platform.modules.vehicle.vo.RenewalPreviewVO;
^import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import com.jushan.common.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月卡/固定车续费闭环单元测试。
 * <p>
 * 使用 Mockito 隔离数据库，验证：续费订单创建、有效期预览（跨月/跨年/过期）、
 * 支付成功后有效期延长、过期月卡在场车辆类型回写为固定车、审计记录、幂等。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class VehicleRenewalServiceImplTest {

    @Mock
    private SysVehicleMapper vehicleMapper;
    @Mock
    private ParkingOrderMapper orderMapper;
    @Mock
    private ParkingSessionMapper sessionMapper;
    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysVehicle>> vehicleUwCaptor;
    @Captor
    private ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ParkingSession>> sessionUwCaptor;

    private VehicleRenewalServiceImpl service;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 5L;
    private static final long VEHICLE_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new VehicleRenewalServiceImpl(vehicleMapper, orderMapper, sessionMapper, auditService, new ObjectMapper());
        TenantContext.set(new TenantContext.Snapshot(TENANT_ID, USER_ID, TenantContext.USER_TYPE_TENANT, "", ""));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SysVehicle monthlyVehicle(LocalDate validEnd, String status) {
        SysVehicle v = new SysVehicle();
        v.setId(VEHICLE_ID);
        v.setTenantId(TENANT_ID);
        v.setPlateNumber("京A12345");
        v.setVehicleType(SysVehicle.TYPE_MONTHLY);
        v.setParkingLotId(10L);
        v.setValidEndDate(validEnd);
        v.setStatus(status);
        return v;
    }

    private ParkingOrder renewalOrder(int months) {
        ParkingOrder o = new ParkingOrder();
        o.setId(999L);
        o.setTenantId(TENANT_ID);
        o.setParkingLotId(10L);
        o.setRefId(VEHICLE_ID);
        o.setOrderNo("MR10" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "000001");
        o.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        o.setPlateNumber("京A12345");
        o.setPayableAmount(90000);
        o.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        o.setPayChannel("PYUN");
        o.setRenewalMonths(months);
        o.setOperatorId(USER_ID);
        return o;
    }

    @Test
    @DisplayName("续费预览：正确处理跨月、跨年")
    void previewRenewal_shouldHandleCrossMonthAndYear() {
        // 未来有效期（未过期）：从原有效期顺延
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(monthlyVehicle(LocalDate.of(2027, 1, 31), SysVehicle.STATUS_ACTIVE));

        RenewalPreviewVO vo = service.previewRenewal(VEHICLE_ID, 1);
        assertThat(vo.getNewEndDate()).isEqualTo(LocalDate.of(2027, 2, 28)); // 1.31 + 1 月 = 2.28

        RenewalPreviewVO vo2 = service.previewRenewal(VEHICLE_ID, 2);
        assertThat(vo2.getNewEndDate()).isEqualTo(LocalDate.of(2027, 3, 31));

        // 跨年
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(monthlyVehicle(LocalDate.of(2027, 12, 15), SysVehicle.STATUS_ACTIVE));
        RenewalPreviewVO vo3 = service.previewRenewal(VEHICLE_ID, 2);
        assertThat(vo3.getNewEndDate()).isEqualTo(LocalDate.of(2028, 2, 15));
    }

    @Test
    @DisplayName("续费预览：原有效期已过期则从未过期当天起算")
    void previewRenewal_expiredShouldStartFromToday() {
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(monthlyVehicle(LocalDate.of(2020, 1, 1), SysVehicle.STATUS_EXPIRED));

        RenewalPreviewVO vo = service.previewRenewal(VEHICLE_ID, 3);
        assertThat(vo.getCurrentEndDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(vo.getNewEndDate()).isEqualTo(LocalDate.now().plusMonths(3));
    }

    @Test
    @DisplayName("发起续费：创建 PENDING_PAY 的 MONTH_RENEW 订单")
    void createRenewalOrder_shouldCreatePendingMonthRenewOrder() {
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(monthlyVehicle(LocalDate.of(2026, 8, 1), SysVehicle.STATUS_ACTIVE));

        VehicleRenewalCmd cmd = new VehicleRenewalCmd();
        cmd.setRenewalMonths(3);
        cmd.setPayChannel("PYUN");
        cmd.setAmountCents(90000);

        RenewalOrderVO vo = service.createRenewalOrder(VEHICLE_ID, cmd);

        verify(orderMapper).insert(any(ParkingOrder.class));
        assertThat(vo.getStatus()).isEqualTo(ParkingOrder.STATUS_PENDING_PAY);
        assertThat(vo.getVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(vo.getRenewalMonths()).isEqualTo(3);
        assertThat(vo.getAmountCents()).isEqualTo(90000);
        assertThat(vo.getOrderNo()).startsWith("MR");
        assertThat(vo.getNewValidEndDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("发起续费：非月租/储值车应被拒绝")
    void createRenewalOrder_shouldRejectNonMonthly() {
        SysVehicle free = monthlyVehicle(LocalDate.of(2026, 8, 1), SysVehicle.STATUS_ACTIVE);
        free.setVehicleType(SysVehicle.TYPE_FREE);
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(free);

        VehicleRenewalCmd cmd = new VehicleRenewalCmd();
        cmd.setRenewalMonths(3);
        cmd.setPayChannel("PYUN");
        cmd.setAmountCents(90000);

        Assertions.assertThrows(BusinessException.class, () -> service.createRenewalOrder(VEHICLE_ID, cmd));
        verify(orderMapper, never()).insert(any(ParkingOrder.class));
    }

    @Test
    @DisplayName("支付成功生效：延长有效期、过期转ACTIVE、在场车辆类型回写为固定车、记录审计")
    void applyRenewalEffect_shouldExtendAndUpdateInParkAndAudit() {
        ParkingOrder order = renewalOrder(3);
        ParkingOrder completed = renewalOrder(3);
        completed.setStatus(ParkingOrder.STATUS_COMPLETED);
        // 首次读取为 PENDING_PAY（进入生效分支），最终回读为 COMPLETED（模拟 DB 已更新）
        when(orderMapper.selectById(999L)).thenReturn(order, completed);
        when(orderMapper.update(isNull(), any())).thenReturn(1); // 幂等守卫通过

        SysVehicle vehicle = monthlyVehicle(LocalDate.of(2026, 1, 1), SysVehicle.STATUS_EXPIRED); // 已过期
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle);

        ParkingSession session = new ParkingSession();
        session.setId(777L);
        session.setStatus(ParkingSession.STATUS_IN);
        when(sessionMapper.selectInByPlateAndLot("京A12345", 10L, TENANT_ID)).thenReturn(session);

        LocalDate expectedNewEnd = LocalDate.now().plusMonths(3);
        when(vehicleMapper.update(isNull(), any())).thenReturn(1);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);

        RenewalOrderVO vo = service.applyRenewalEffect(999L, "PS-123");

        // 订单置为 COMPLETED
        assertThat(vo.getStatus()).isEqualTo(ParkingOrder.STATUS_COMPLETED);

        // 车辆有效期延长（过期场景从今天起算）
        verify(vehicleMapper).update(isNull(), vehicleUwCaptor.capture());
        String vehicleSql = vehicleUwCaptor.getValue().getSqlSet();
        assertThat(vehicleSql).contains("valid_end_date");
        assertThat(vehicleSql).contains("status");
        assertThat(vehicleUwCaptor.getValue().getParamNameValuePairs().values()).contains(expectedNewEnd);

        // 在场车辆类型回写为固定车（MONTHLY）
        verify(sessionMapper).update(isNull(), sessionUwCaptor.capture());
        assertThat(sessionUwCaptor.getValue().getSqlSet()).contains("vehicle_type");

        // 审计日志
        verify(auditService).writeAuditLog(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq("vehicle"),
                org.mockito.ArgumentMatchers.eq(String.valueOf(VEHICLE_ID)),
                org.mockito.ArgumentMatchers.eq("vehicle_renew"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AuditService.RESULT_SUCCESS),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("支付成功生效：幂等，重复调用不重复延长/写审计")
    void applyRenewalEffect_shouldBeIdempotent() {
        ParkingOrder order = renewalOrder(3);
        order.setStatus(ParkingOrder.STATUS_COMPLETED); // 已生效，重复调用应幂等
        when(orderMapper.selectById(999L)).thenReturn(order, order);
        when(orderMapper.update(isNull(), any())).thenReturn(0); // 已被处理

        RenewalOrderVO vo = service.applyRenewalEffect(999L, "PS-123");

        assertThat(vo.getStatus()).isEqualTo(ParkingOrder.STATUS_COMPLETED);
        verify(vehicleMapper, never()).update(isNull(), any());
        verify(sessionMapper, never()).update(isNull(), any());
        verify(auditService, never()).writeAuditLog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
