package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DeviceService.resolveGateDevice 解析优先级单测。
 * <p>
 * 覆盖场景：
 * TC-1: gate_device_id 优先级最高
 * TC-2: gate_device_id=NULL → GATE 设备（1台）
 * TC-3: GATE 无 → CAMERA+OPEN_GATE（1台）
 * TC-4: CAMERA 0 候选 → 抛 GATE_DEVICE_NOT_FOUND
 * TC-5: GATE 多候选（2台）→ 抛 AMBIGUOUS_GATE_DEVICE
 * TC-6: CAMERA+OPEN_GATE 多候选（2台）→ 抛 AMBIGUOUS_GATE_DEVICE
 * TC-7: 双向车道 gate_device_id 指向 entry 相机
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceResolveGateDeviceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private ParkingLaneMapper laneMapper;

    @Mock
    private com.jushan.system.mapper.DeviceModelMapper modelMapper;

    @Mock
    private com.jushan.system.mapper.DeviceVendorMapper vendorMapper;

    @Mock
    private com.jushan.system.mapper.ParkingLotMapper parkingLotMapper;

    @Mock
    private com.jushan.system.client.DeviceAccessClient deviceAccessClient;

    @Mock
    private com.jushan.system.mapper.DeviceStatusSnapshotMapper snapshotMapper;

    @Mock
    private com.jushan.system.mapper.SysAuditLogMapper auditLogMapper;

    @Mock
    private com.jushan.system.mapper.DeviceCommandAuditMapper commandAuditMapper;

    @Mock
    private ParkingLotScopeResolver scopeResolver;

    @Mock
    private CameraFailoverService cameraFailoverService;

    @InjectMocks
    private DeviceService deviceService;

    private static final Long LANE_ID = 100L;
    private static final Long GATE_DEVICE_ID = 201L;
    private static final Long CAMERA_A_ID = 301L;
    private static final Long CAMERA_B_ID = 302L;
    private static final Long GATE_DEVICE_ID_2 = 202L;

    @BeforeEach
    void setUp() {
        // Each test sets up its own lane mock via stubLane().
        // Default: gateDeviceId = null.
        stubLane(null);
    }

    /** Stub the lane with given gateDeviceId. Returns the stubbed lane. */
    private ParkingLane stubLane(Long gateDeviceId) {
        ParkingLane lane = new ParkingLane();
        lane.setId(LANE_ID);
        lane.setName("测试车道");
        lane.setLotId(1L);
        lane.setGateDeviceId(gateDeviceId);
        when(laneMapper.selectByIdIgnoreTenant(LANE_ID)).thenReturn(lane);
        return lane;
    }

    // ==================== TC-1: gate_device_id 最高优先级 ====================

    @Test
    @DisplayName("TC-1: gate_device_id 指定时直接返回该设备，忽略 GATE/CAMERA 候选")
    void shouldReturnGateDeviceWhenGateDeviceIdSet() {
        stubLane(GATE_DEVICE_ID);

        Device gateDevice = buildDevice(GATE_DEVICE_ID, "GATE", "ENABLED", LANE_ID, "");
        when(deviceMapper.selectById(GATE_DEVICE_ID)).thenReturn(gateDevice);

        Device result = deviceService.resolveGateDevice(LANE_ID);
        assertThat(result.getId()).isEqualTo(GATE_DEVICE_ID);
    }

    // ==================== TC-2: gate_device_id=NULL → GATE 设备 ====================

    @Test
    @DisplayName("TC-2: gate_device_id=NULL 且本车道有 1 台 GATE 设备 → 返回该 GATE")
    void shouldReturnGateDeviceWhenNoGateDeviceId() {
        List<Device> gateDevices = new ArrayList<>();
        gateDevices.add(buildDevice(GATE_DEVICE_ID, "GATE", "ENABLED", LANE_ID, ""));
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(gateDevices);

        Device result = deviceService.resolveGateDevice(LANE_ID);
        assertThat(result.getId()).isEqualTo(GATE_DEVICE_ID);
        assertThat(result.getDeviceType()).isEqualTo("GATE");
    }

    // ==================== TC-3: GATE 无 → CAMERA+OPEN_GATE ====================

    @Test
    @DisplayName("TC-3: GATE 无且本车道有 1 台带 OPEN_GATE 的 CAMERA → 返回该 CAMERA")
    void shouldReturnCameraWithOpenGateWhenNoGateAndNoGateDeviceId() {
        // First selectList call → empty GATE list; second → CAMERA list
        List<Device> emptyGates = new ArrayList<>();
        List<Device> cameras = new ArrayList<>();
        cameras.add(buildDevice(CAMERA_A_ID, "CAMERA", "ENABLED", LANE_ID, "OPEN_GATE,CAPTURE"));

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(emptyGates)
                .thenReturn(cameras);

        Device result = deviceService.resolveGateDevice(LANE_ID);
        assertThat(result.getId()).isEqualTo(CAMERA_A_ID);
        assertThat(result.getDeviceType()).isEqualTo("CAMERA");
    }

    // ==================== TC-4: 0 候选 → 报错 ====================

    @Test
    @DisplayName("TC-4: GATE 和 CAMERA+OPEN_GATE 都没有 → 抛 BusinessException(GATE_DEVICE_NOT_FOUND)")
    void shouldThrowWhenNoGateDeviceFound() {
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>())  // GATE
                .thenReturn(new ArrayList<>()); // CAMERA

        assertThatThrownBy(() -> deviceService.resolveGateDevice(LANE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到控闸设备");
    }

    // ==================== TC-5: GATE 多候选 → 报错 ====================

    @Test
    @DisplayName("TC-5: 本车道有 2 台 GATE 设备 → 抛 AMBIGUOUS_GATE_DEVICE")
    void shouldThrowWhenMultipleGates() {
        List<Device> gates = new ArrayList<>();
        gates.add(buildDevice(GATE_DEVICE_ID, "GATE", "ENABLED", LANE_ID, ""));
        gates.add(buildDevice(GATE_DEVICE_ID_2, "GATE", "ENABLED", LANE_ID, ""));

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(gates);

        assertThatThrownBy(() -> deviceService.resolveGateDevice(LANE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多台道闸设备");
    }

    // ==================== TC-6: CAMERA+OPEN_GATE 多候选 → 报错 ====================

    @Test
    @DisplayName("TC-6: 本车道有 2 台带 OPEN_GATE 的 CAMERA → 抛 AMBIGUOUS_GATE_DEVICE")
    void shouldThrowWhenMultipleCamerasWithOpenGate() {
        List<Device> emptyGates = new ArrayList<>();
        List<Device> cameras = new ArrayList<>();
        cameras.add(buildDevice(CAMERA_A_ID, "CAMERA", "ENABLED", LANE_ID, "OPEN_GATE,CAPTURE"));
        cameras.add(buildDevice(CAMERA_B_ID, "CAMERA", "ENABLED", LANE_ID, "OPEN_GATE,CAPTURE"));

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(emptyGates)
                .thenReturn(cameras);

        assertThatThrownBy(() -> deviceService.resolveGateDevice(LANE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多台可开闸相机");
    }

    // ==================== TC-7: gate_device_id 指向已停用设备 → 报错 ====================

    @Test
    @DisplayName("TC-7: gate_device_id 指向 DISABLED 设备 → 抛 BusinessException")
    void shouldThrowWhenGateDeviceDisabled() {
        stubLane(GATE_DEVICE_ID);

        Device disabled = buildDevice(GATE_DEVICE_ID, "GATE", "DISABLED", LANE_ID, "");
        when(deviceMapper.selectById(GATE_DEVICE_ID)).thenReturn(disabled);

        assertThatThrownBy(() -> deviceService.resolveGateDevice(LANE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
    }

    // ==================== helpers ====================

    private static Device buildDevice(Long id, String type, String status, Long laneId, String capabilities) {
        Device d = new Device();
        d.setId(id);
        d.setDeviceType(type);
        d.setStatus(status);
        d.setLaneId(laneId);
        d.setDeviceSn("SN-" + id);
        d.setCapabilities(capabilities);
        d.setParkingLotId(1L);
        return d;
    }
}
