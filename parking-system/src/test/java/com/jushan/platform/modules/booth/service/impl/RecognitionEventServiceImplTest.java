package com.jushan.platform.modules.booth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.parking.vo.ParkingSessionVO;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.entity.Device;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.service.BillingEngine;
import com.jushan.system.service.MonitorAlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RecognitionEventServiceImpl 开闸单元测试（v0.4）。
 * <p>
 * 使用 Mockito 隔离数据库与 Device Access Client，覆盖：
 * <ul>
 *   <li>入场开闸成功 / 失败 / 异常</li>
 *   <li>出场开闸成功 / 失败 / 异常</li>
 *   <li>人工开闸成功 / 失败 / 异常 / 无设备</li>
 *   <li>开闸失败不回滚业务记录</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecognitionEventServiceImpl 开闸测试（v0.4）")
class RecognitionEventServiceImplTest {

    @Mock
    private VehicleTypeDecisionService vehicleTypeDecisionService;

    @Mock
    private ParkingSessionService parkingSessionService;

    @Mock
    private BillingEngine billingEngine;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceAccessClient deviceAccessClient;

    @Mock
    private MonitorAlertService monitorAlertService;

    private RecognitionEventServiceImpl service;

    private static final Long TENANT_ID = 1L;
    private static final Long PARKING_LOT_ID = 10L;
    private static final Long LANE_ID = 100L;
    private static final String PLATE = "京A12345";
    private static final String DEVICE_SN = "GATE-SN-001";

    @BeforeEach
    void setUp() {
        service = new RecognitionEventServiceImpl(
                vehicleTypeDecisionService, parkingSessionService, billingEngine,
                deviceMapper, deviceAccessClient, monitorAlertService);
        // 设置租户上下文
        TenantContext.set(new TenantContext.Snapshot(TENANT_ID, 1L, "tenant", null, null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 入场开闸 ====================

    @Test
    @DisplayName("入场开闸成功 → gateOpened=null, gateDeviceAck=true, 三层状态区分")
    void shouldOpenGateOnEntrySuccess() {
        // given
        RecognitionEventCmd cmd = entryCmd();
        VehicleTypeDecisionVO decision = allowEntryDecision();
        ParkingSessionVO sessionVO = sessionVO(1001L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.entry(any(ParkingSessionEntryCmd.class))).thenReturn(sessionVO);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN)).thenReturn(commandResult(true, 200, "gate opened"));

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(1001L);
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isTrue();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("开闸成功");
        assertThat(result.getException()).isFalse();
        assertThat(result.getFeeAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(deviceAccessClient).openGate(DEVICE_SN);
        verify(parkingSessionService).entry(any(ParkingSessionEntryCmd.class));
    }

    @Test
    @DisplayName("入场开闸设备返回失败 → gateOpened=null, 入场记录仍创建")
    void shouldNotRollbackEntryOnGateOpenFailure() {
        // given
        RecognitionEventCmd cmd = entryCmd();
        VehicleTypeDecisionVO decision = allowEntryDecision();
        ParkingSessionVO sessionVO = sessionVO(1002L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.entry(any(ParkingSessionEntryCmd.class))).thenReturn(sessionVO);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenReturn(commandResult(false, 500, "device motor error"));

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then — 入场记录不因开闸失败而回滚
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(1002L);
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("开闸失败");
        assertThat(result.getException()).isFalse(); // 入场业务不标记异常

        verify(deviceAccessClient).openGate(DEVICE_SN);
        verify(parkingSessionService).entry(any(ParkingSessionEntryCmd.class));
    }

    @Test
    @DisplayName("入场开闸网络超时 → gateOpened=null, UNCERTAIN, 入场记录仍创建")
    void shouldNotRollbackEntryOnGateOpenException() {
        // given
        RecognitionEventCmd cmd = entryCmd();
        VehicleTypeDecisionVO decision = allowEntryDecision();
        ParkingSessionVO sessionVO = sessionVO(1003L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.entry(any(ParkingSessionEntryCmd.class))).thenReturn(sessionVO);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                        "Device Access 网络异常（UNCERTAIN）: deviceSn=GATE-SN-001, error=Read timed out"));

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then — 入场记录不因开闸异常而回滚
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(1003L);
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("UNCERTAIN");
        assertThat(result.getException()).isFalse();

        verify(deviceAccessClient).openGate(DEVICE_SN);
        verify(parkingSessionService).entry(any(ParkingSessionEntryCmd.class));
    }

    @Test
    @DisplayName("入场车道无 GATE 设备 → gateOpened=null, gateCommandSent=false")
    void shouldSkipGateOpenOnEntryWhenNoGateDevice() {
        // given
        RecognitionEventCmd cmd = entryCmd();
        VehicleTypeDecisionVO decision = allowEntryDecision();
        ParkingSessionVO sessionVO = sessionVO(1004L);

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.entry(any(ParkingSessionEntryCmd.class))).thenReturn(sessionVO);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(1004L);
        assertThat(result.getGateCommandSent()).isFalse();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("未找到");

        verify(deviceAccessClient, never()).openGate(any());
    }

    // ==================== 出场开闸 ====================

    @Test
    @DisplayName("出场开闸成功 → gateOpened=null, gateDeviceAck=true")
    void shouldOpenGateOnExitSuccess() {
        // given
        RecognitionEventCmd cmd = exitCmd();
        VehicleTypeDecisionVO decision = allowExitDecision(true);
        ParkingSessionVO inSession = sessionVO(2001L);
        ParkingSessionVO outSession = sessionVO(2001L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.getInByPlateNumber(PLATE)).thenReturn(inSession);
        when(billingEngine.calculateFee(eq(PARKING_LOT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(500); // 5 元
        when(parkingSessionService.exit(any(ParkingSessionExitCmd.class))).thenReturn(outSession);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN)).thenReturn(commandResult(true, 200, "gate opened"));

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getFeeAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isTrue();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("开闸成功");
        assertThat(result.getException()).isFalse();

        verify(deviceAccessClient).openGate(DEVICE_SN);
        verify(parkingSessionService).exit(any(ParkingSessionExitCmd.class));
    }

    @Test
    @DisplayName("出场开闸设备返回失败 → gateOpened=null, 出场记录仍更新")
    void shouldNotRollbackExitOnGateOpenFailure() {
        // given
        RecognitionEventCmd cmd = exitCmd();
        VehicleTypeDecisionVO decision = allowExitDecision(true);
        ParkingSessionVO inSession = sessionVO(2002L);
        ParkingSessionVO outSession = sessionVO(2002L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.getInByPlateNumber(PLATE)).thenReturn(inSession);
        when(billingEngine.calculateFee(eq(PARKING_LOT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(800);
        when(parkingSessionService.exit(any(ParkingSessionExitCmd.class))).thenReturn(outSession);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenReturn(commandResult(false, 501, "gate motor stuck"));

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then — 出场记录不因开闸失败而回滚
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("开闸失败");
        assertThat(result.getException()).isFalse();

        verify(deviceAccessClient).openGate(DEVICE_SN);
        verify(parkingSessionService).exit(any(ParkingSessionExitCmd.class));
    }

    @Test
    @DisplayName("出场开闸网络超时 → gateOpened=null, UNCERTAIN, 出场记录仍更新")
    void shouldNotRollbackExitOnGateOpenException() {
        // given
        RecognitionEventCmd cmd = exitCmd();
        VehicleTypeDecisionVO decision = allowExitDecision(true);
        ParkingSessionVO inSession = sessionVO(2003L);
        ParkingSessionVO outSession = sessionVO(2003L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.getInByPlateNumber(PLATE)).thenReturn(inSession);
        when(billingEngine.calculateFee(eq(PARKING_LOT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1000);
        when(parkingSessionService.exit(any(ParkingSessionExitCmd.class))).thenReturn(outSession);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                        "Device Access 网络异常（UNCERTAIN）: deviceSn=GATE-SN-001, error=Read timed out"));

        // when
        RecognitionResultVO result = service.handleEvent(cmd);

        // then
        assertThat(result.getFeeAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("UNCERTAIN");
        assertThat(result.getException()).isFalse();

        verify(deviceAccessClient).openGate(DEVICE_SN);
        verify(parkingSessionService).exit(any(ParkingSessionExitCmd.class));
    }

    // ==================== 人工开闸 ====================

    @Test
    @DisplayName("人工开闸成功 → gateOpened=null, 审计信息完整")
    void shouldManualOpenGateSuccess() {
        // given
        Device gateDevice = gateDevice();
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN)).thenReturn(commandResult(true, 200, "gate opened"));

        // when
        RecognitionResultVO result = service.manualOpenGate(LANE_ID, 99L, "岗亭人工放行");

        // then
        assertThat(result.getAllowPass()).isTrue();
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isTrue();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("人工开闸成功");
        assertThat(result.getResultMessage()).contains("岗亭人工放行");
        assertThat(result.getException()).isFalse();

        verify(deviceAccessClient).openGate(DEVICE_SN);
    }

    @Test
    @DisplayName("人工开闸设备返回失败 → gateOpened=null, exception=true")
    void shouldManualOpenGateFailure() {
        // given
        Device gateDevice = gateDevice();
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenReturn(commandResult(false, 500, "device motor error"));

        // when
        RecognitionResultVO result = service.manualOpenGate(LANE_ID, 99L, "紧急放行");

        // then
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("人工开闸失败");
        assertThat(result.getException()).isTrue();
        assertThat(result.getExceptionType()).isEqualTo("GATE_OPEN_FAILED");

        verify(deviceAccessClient).openGate(DEVICE_SN);
    }

    @Test
    @DisplayName("人工开闸网络超时 → gateOpened=null, UNCERTAIN")
    void shouldManualOpenGateException() {
        // given
        Device gateDevice = gateDevice();
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                        "Device Access 网络异常（UNCERTAIN）: deviceSn=GATE-SN-001, error=Read timed out"));

        // when
        RecognitionResultVO result = service.manualOpenGate(LANE_ID, 99L, "系统故障后人工放行");

        // then
        assertThat(result.getGateCommandSent()).isTrue();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("UNCERTAIN");
        assertThat(result.getException()).isTrue();
        assertThat(result.getExceptionType()).isEqualTo("GATE_OPEN_UNCERTAIN");

        verify(deviceAccessClient).openGate(DEVICE_SN);
    }

    @Test
    @DisplayName("人工开闸无 GATE 设备 → gateOpened=null, exception=GATE_DEVICE_NOT_FOUND")
    void shouldManualOpenGateNoDevice() {
        // given
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // when
        RecognitionResultVO result = service.manualOpenGate(LANE_ID, 99L, "测试");

        // then
        assertThat(result.getGateCommandSent()).isFalse();
        assertThat(result.getGateDeviceAck()).isFalse();
        assertThat(result.getGateOpened()).isNull(); // 一期无法确认闸杆实际状态
        assertThat(result.getGateResult()).contains("未找到");
        assertThat(result.getException()).isTrue();
        assertThat(result.getExceptionType()).isEqualTo("GATE_DEVICE_NOT_FOUND");

        verify(deviceAccessClient, never()).openGate(any());
    }

    // ==================== 无自动重试 ====================

    @Test
    @DisplayName("入场开闸失败后无自动重试 → 仅 1 次 openGate 调用")
    void shouldNotAutoRetryOnEntryFailure() {
        // given
        RecognitionEventCmd cmd = entryCmd();
        VehicleTypeDecisionVO decision = allowEntryDecision();
        ParkingSessionVO sessionVO = sessionVO(3001L);
        Device gateDevice = gateDevice();

        when(vehicleTypeDecisionService.decide(PLATE)).thenReturn(decision);
        when(parkingSessionService.entry(any(ParkingSessionEntryCmd.class))).thenReturn(sessionVO);
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gateDevice));
        when(deviceAccessClient.openGate(DEVICE_SN))
                .thenReturn(commandResult(false, 503, "MQTT unavailable"));

        // when
        service.handleEvent(cmd);

        // then — 只调用一次，无自动重试
        verify(deviceAccessClient, times(1)).openGate(DEVICE_SN);
    }

    // ==================== 辅助方法 ====================

    private RecognitionEventCmd entryCmd() {
        RecognitionEventCmd cmd = new RecognitionEventCmd();
        cmd.setParkingLotId(PARKING_LOT_ID);
        cmd.setLaneId(LANE_ID);
        cmd.setPlateNumber(PLATE);
        cmd.setDirection("ENTRY");
        cmd.setCaptureImage("http://img/capture.jpg");
        return cmd;
    }

    private RecognitionEventCmd exitCmd() {
        RecognitionEventCmd cmd = new RecognitionEventCmd();
        cmd.setParkingLotId(PARKING_LOT_ID);
        cmd.setLaneId(LANE_ID);
        cmd.setPlateNumber(PLATE);
        cmd.setDirection("EXIT");
        cmd.setCaptureImage("http://img/exit_capture.jpg");
        return cmd;
    }

    private VehicleTypeDecisionVO allowEntryDecision() {
        VehicleTypeDecisionVO vo = new VehicleTypeDecisionVO();
        vo.setVehicleType("TEMP");
        vo.setAllowEntry(true);
        vo.setNeedCharge(true);
        vo.setDecisionReason("临时车辆，允许入场");
        return vo;
    }

    private VehicleTypeDecisionVO allowExitDecision(boolean needCharge) {
        VehicleTypeDecisionVO vo = new VehicleTypeDecisionVO();
        vo.setVehicleType("TEMP");
        vo.setAllowEntry(true);
        vo.setNeedCharge(needCharge);
        vo.setDecisionReason("临时车辆，允许出场");
        return vo;
    }

    private ParkingSessionVO sessionVO(Long id) {
        ParkingSessionVO vo = new ParkingSessionVO();
        vo.setId(id);
        vo.setPlateNumber(PLATE);
        vo.setParkingLotId(PARKING_LOT_ID);
        vo.setEntryTime(LocalDateTime.now().minusHours(2));
        return vo;
    }

    private Device gateDevice() {
        Device device = new Device();
        device.setId(5001L);
        device.setLaneId(LANE_ID);
        device.setParkingLotId(PARKING_LOT_ID);
        device.setDeviceType("GATE");
        device.setStatus("ENABLED");
        device.setDeviceSn(DEVICE_SN);
        device.setName("入口道闸");
        return device;
    }

    private CommandResultDTO commandResult(boolean success, int deviceCode, String message) {
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(success);
        dto.setDeviceCode(deviceCode);
        dto.setMessage(message);
        return dto;
    }
}
