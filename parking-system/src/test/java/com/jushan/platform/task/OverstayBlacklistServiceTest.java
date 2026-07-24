package com.jushan.platform.task;

import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.AccessPolicyMapper;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
^import com.jushan.platform.modules.log.entity.SysAuditLog;
import com.jushan.system.mapper.SysAuditLogMapper;
import com.jushan.system.service.MonitorAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时停放自动拉黑服务单元测试。
 * <p>
 * 使用 Mockito 隔离数据库与下游服务，通过传入固定 {@link LocalDateTime} 模拟时间，
 * 覆盖：超阈值拉黑、未超阈值跳过、已有黑名单防重、固定车跳过、按停车场阈值、
 * 默认阈值边界、由临时车记录转换拉黑等场景。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class OverstayBlacklistServiceTest {

    @Mock
    private ParkingSessionMapper sessionMapper;

    @Mock
    private SysVehicleMapper vehicleMapper;

    @Mock
    private AccessPolicyMapper policyMapper;

    @Mock
    private MonitorAlertService monitorAlertService;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    private OverstayBlacklistService service;

    private final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);

    @BeforeEach
    void setUp() {
        service = new OverstayBlacklistService(sessionMapper, vehicleMapper, policyMapper,
                monitorAlertService, auditLogMapper);
    }

    @Test
    @DisplayName("临时车停放超过默认 168 小时：自动创建黑名单、告警与审计")
    void shouldBlacklistTempVehicleOverstayingDefaultThreshold() {
        ParkingSession session = inSession("京A12345", "TEMP", NOW.minusHours(169));
        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(null);
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn(null);
        when(vehicleMapper.insert((SysVehicle) any())).thenReturn(1);
        when(auditLogMapper.insert((SysAuditLog) any())).thenReturn(1);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<SysVehicle> captor = ArgumentCaptor.forClass(SysVehicle.class);
        verify(vehicleMapper).insert(captor.capture());
        SysVehicle saved = captor.getValue();
        assertThat(saved.getVehicleType()).isEqualTo(SysVehicle.TYPE_BLACKLIST);
        assertThat(saved.getPlateNumber()).isEqualTo("京A12345");
        assertThat(saved.getParkingLotId()).isEqualTo(10L);
        assertThat(saved.getRemark()).contains("系统自动").contains("超时停放");
        verify(monitorAlertService).createOverstayAlert(eq(1L), eq(10L), eq("京A12345"), any());
        verify(auditLogMapper).insert((SysAuditLog) any());
    }

    @Test
    @DisplayName("临时车停放未超过阈值：跳过")
    void shouldSkipTempVehicleWithinThreshold() {
        ParkingSession session = inSession("京B22222", "TEMP", NOW.minusHours(1));
        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn(null);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(0);
        verify(vehicleMapper, never()).insert((SysVehicle) any());
        verify(vehicleMapper, never()).updateById((SysVehicle) any());
        verify(monitorAlertService, never()).createOverstayAlert(any(), any(), any(), any());
    }

    @Test
    @DisplayName("默认阈值边界：正好 168 小时不拉黑，169 小时拉黑")
    void shouldHandleDefaultThresholdBoundary() {
        ParkingSession exactly168 = inSession("京C33333", "TEMP", NOW.minusHours(168));
        ParkingSession over169 = inSession("京C44444", "TEMP", NOW.minusHours(169));
        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(exactly168, over169));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn(null);
        when(vehicleMapper.selectByPlateNumber("京C44444", 1L)).thenReturn(null);
        when(vehicleMapper.insert((SysVehicle) any())).thenReturn(1);
        when(auditLogMapper.insert((SysAuditLog) any())).thenReturn(1);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(1);
        verify(vehicleMapper).insert((SysVehicle) any());
        verify(vehicleMapper, never()).updateById((SysVehicle) any());
    }

    @Test
    @DisplayName("同车已有生效黑名单：防重跳过，不重复创建")
    void shouldSkipWhenActiveBlacklistExists() {
        ParkingSession session = inSession("京D55555", "TEMP", NOW.minusHours(200));
        SysVehicle existingBlacklist = new SysVehicle();
        existingBlacklist.setId(99L);
        existingBlacklist.setVehicleType(SysVehicle.TYPE_BLACKLIST);
        existingBlacklist.setStatus(SysVehicle.STATUS_ACTIVE);

        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn(null);
        when(vehicleMapper.selectByPlateNumber("京D55555", 1L)).thenReturn(existingBlacklist);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(0);
        verify(vehicleMapper, never()).insert((SysVehicle) any());
        verify(vehicleMapper, never()).updateById((SysVehicle) any());
        verify(monitorAlertService, never()).createOverstayAlert(any(), any(), any(), any());
    }

    @Test
    @DisplayName("固定车（VIP）超时停放：跳过，不拉黑（MONTHLY 已改由 MonthlyPassExpiryJob 处理）")
    void shouldSkipFixedVehicle() {
        ParkingSession session = inSession("京E66666", SysVehicle.TYPE_VIP, NOW.minusHours(300));
        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn(null);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(0);
        verify(vehicleMapper, never()).insert((SysVehicle) any());
        verify(vehicleMapper, never()).updateById((SysVehicle) any());
    }

    @Test
    @DisplayName("停车场配置较短阈值（24 小时）：按停车场策略拉黑")
    void shouldUseParkingLotConfiguredThreshold() {
        ParkingSession session = inSession("京F77777", "TEMP", NOW.minusHours(30));
        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn("24");
        when(vehicleMapper.selectByPlateNumber("京F77777", 1L)).thenReturn(null);
        when(vehicleMapper.insert((SysVehicle) any())).thenReturn(1);
        when(auditLogMapper.insert((SysAuditLog) any())).thenReturn(1);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(1);
        verify(vehicleMapper).insert((SysVehicle) any());
    }

    @Test
    @DisplayName("阈值配置非法：回退默认 168 小时，未超时则跳过")
    void shouldFallbackToDefaultOnInvalidPolicy() {
        ParkingSession session = inSession("京G88888", "TEMP", NOW.minusHours(30));
        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn("not-a-number");

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(0);
        verify(vehicleMapper, never()).insert((SysVehicle) any());
    }

    @Test
    @DisplayName("已存在临时车记录：转换为黑名单而非新增（避免唯一键冲突）")
    void shouldConvertExistingTempRecordToBlacklist() {
        ParkingSession session = inSession("京H99999", "TEMP", NOW.minusHours(200));
        SysVehicle existingTemp = new SysVehicle();
        existingTemp.setId(77L);
        existingTemp.setVehicleType("TEMP");
        existingTemp.setStatus(SysVehicle.STATUS_ACTIVE);

        when(sessionMapper.selectAllInSessions()).thenReturn(List.of(session));
        when(policyMapper.selectTimeoutHoursValue(10L, 1L)).thenReturn(null);
        when(vehicleMapper.selectByPlateNumber("京H99999", 1L)).thenReturn(existingTemp);
        when(vehicleMapper.updateById((SysVehicle) any())).thenReturn(1);
        when(auditLogMapper.insert((SysAuditLog) any())).thenReturn(1);

        int count = service.detectAndBlacklist(NOW);

        assertThat(count).isEqualTo(1);
        verify(vehicleMapper, never()).insert((SysVehicle) any());
        ArgumentCaptor<SysVehicle> captor = ArgumentCaptor.forClass(SysVehicle.class);
        verify(vehicleMapper).updateById(captor.capture());
        SysVehicle updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(77L);
        assertThat(updated.getVehicleType()).isEqualTo(SysVehicle.TYPE_BLACKLIST);
        assertThat(updated.getRemark()).contains("系统自动");
        verify(monitorAlertService).createOverstayAlert(eq(1L), eq(10L), eq("京H99999"), any());
    }

    @Test
    @DisplayName("无在场车辆：直接返回 0")
    void shouldReturnZeroWhenNoInSessions() {
        when(sessionMapper.selectAllInSessions()).thenReturn(Collections.emptyList());
        assertThat(service.detectAndBlacklist(NOW)).isEqualTo(0);
        verify(vehicleMapper, never()).insert((SysVehicle) any());
    }

    // ==================== 辅助方法 ====================

    private ParkingSession inSession(String plate, String vehicleType, LocalDateTime entryTime) {
        ParkingSession session = new ParkingSession();
        session.setId(1L);
        session.setTenantId(1L);
        session.setParkingLotId(10L);
        session.setPlateNumber(plate);
        session.setVehicleType(vehicleType);
        session.setStatus(ParkingSession.STATUS_IN);
        session.setEntryTime(entryTime);
        return session;
    }
}
