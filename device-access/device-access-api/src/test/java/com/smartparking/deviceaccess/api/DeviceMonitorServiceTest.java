package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * DeviceMonitorService 单元测试。
 * <p>
 * 验证定时离线检测逻辑：正常设备、超时设备、新设备保护期、不同品牌阈值。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceMonitorService Unit Tests")
class DeviceMonitorServiceTest {

    @Mock
    private DeviceRegistry deviceRegistry;

    @Mock
    private DeviceProductRegistry productRegistry;

    @InjectMocks
    private DeviceMonitorService monitorService;

    private static final String TEST_DEVICE_ID = "test-device-001";

    @BeforeEach
    void setUp() {
        // @InjectMocks 自动注入
    }

    // ──────────────────── 辅助方法 ────────────────────

    private DeviceProduct product(String brand) {
        DeviceProduct p = new DeviceProduct();
        p.setBrand(brand);
        p.setModel("TEST");
        return p;
    }

    private Device onlineDevice(String deviceId, Long productId,
                                 LocalDateTime lastOnline, LocalDateTime createTime) {
        Device d = new Device();
        d.setDeviceId(deviceId);
        d.setDeviceName("测试设备");
        d.setProductId(productId);
        d.setStatus("ONLINE");
        d.setLastOnlineTime(lastOnline);
        d.setCreateTime(createTime);
        return d;
    }

    // ──────────────────── 正常设备不被标记离线 ────────────────────

    @Test
    @DisplayName("Should not mark device offline when heartbeat is within threshold")
    void shouldNotMarkOfflineWhenHeartbeatAlive() {
        // 臻识设备，10 秒前有心跳（阈值 90 秒）
        Device device = onlineDevice(TEST_DEVICE_ID, 1L,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));
        when(productRegistry.getById(1L)).thenReturn(product("ZHENSHI"));

        monitorService.checkOfflineDevices();

        // 不应调用 update 标记 OFFLINE
        verify(deviceRegistry, never()).update(anyString(), any(Device.class));
    }

    // ──────────────────── 超时设备被标记离线 ────────────────────

    @Test
    @DisplayName("Should mark ZHENSHI device offline when heartbeat exceeds 90s")
    void shouldMarkZhenshiOfflineAfter90s() {
        // 臻识设备，100 秒前有心跳（阈值 90 秒）
        Device device = onlineDevice(TEST_DEVICE_ID, 1L,
                LocalDateTime.now().minusSeconds(100),
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));
        when(productRegistry.getById(1L)).thenReturn(product("ZHENSHI"));

        monitorService.checkOfflineDevices();

        // 应调用 update 标记 OFFLINE
        verify(deviceRegistry).update(eq(TEST_DEVICE_ID), argThat(d ->
                "OFFLINE".equals(d.getStatus())));
    }

    @Test
    @DisplayName("Should mark Xinlutong device offline when heartbeat exceeds 270s")
    void shouldMarkXinlutongOfflineAfter270s() {
        // 信路通设备，280 秒前有心跳（阈值 270 秒）
        Device device = onlineDevice(TEST_DEVICE_ID, 2L,
                LocalDateTime.now().minusSeconds(280),
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));
        when(productRegistry.getById(2L)).thenReturn(product("信路通"));

        monitorService.checkOfflineDevices();

        verify(deviceRegistry).update(eq(TEST_DEVICE_ID), argThat(d ->
                "OFFLINE".equals(d.getStatus())));
    }

    @Test
    @DisplayName("Should NOT mark Xinlutong device offline at 260s (within 270s threshold)")
    void shouldNotMarkXinlutongOfflineAt260s() {
        // 信路通设备，260 秒前有心跳（阈值 270 秒，未超时）
        Device device = onlineDevice(TEST_DEVICE_ID, 2L,
                LocalDateTime.now().minusSeconds(260),
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));
        when(productRegistry.getById(2L)).thenReturn(product("信路通"));

        monitorService.checkOfflineDevices();

        verify(deviceRegistry, never()).update(anyString(), any(Device.class));
    }

    // ──────────────────── 刚注册设备不被检查 ────────────────────

    @Test
    @DisplayName("Should skip newly registered devices within 5-minute grace period")
    void shouldSkipNewDevice() {
        // 刚注册 1 分钟的设备，即使心跳超时也不检查
        Device device = onlineDevice(TEST_DEVICE_ID, 1L,
                LocalDateTime.now().minusSeconds(200),
                LocalDateTime.now().minusMinutes(1));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));

        monitorService.checkOfflineDevices();

        // 不应查询 productRegistry（因为被跳过），也不应 update
        verify(productRegistry, never()).getById(anyLong());
        verify(deviceRegistry, never()).update(anyString(), any(Device.class));
    }

    @Test
    @DisplayName("Should check device registered 6 minutes ago (past grace period)")
    void shouldCheckDevicePastGracePeriod() {
        // 注册 6 分钟的设备，超过 5 分钟保护期，应检查
        Device device = onlineDevice(TEST_DEVICE_ID, 1L,
                LocalDateTime.now().minusSeconds(100),
                LocalDateTime.now().minusMinutes(6));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));
        when(productRegistry.getById(1L)).thenReturn(product("ZHENSHI"));

        monitorService.checkOfflineDevices();

        verify(deviceRegistry).update(eq(TEST_DEVICE_ID), argThat(d ->
                "OFFLINE".equals(d.getStatus())));
    }

    // ──────────────────── 无心跳设备视为超时 ────────────────────

    @Test
    @DisplayName("Should mark device offline when lastOnlineTime is null")
    void shouldMarkOfflineWhenNoHeartbeat() {
        Device device = onlineDevice(TEST_DEVICE_ID, 1L,
                null, // 从未上报心跳
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(List.of(device));
        when(productRegistry.getById(1L)).thenReturn(product("ZHENSHI"));

        monitorService.checkOfflineDevices();

        verify(deviceRegistry).update(eq(TEST_DEVICE_ID), argThat(d ->
                "OFFLINE".equals(d.getStatus())));
    }

    // ──────────────────── 空列表处理 ────────────────────

    @Test
    @DisplayName("Should handle empty ONLINE device list gracefully")
    void shouldHandleEmptyList() {
        when(deviceRegistry.list(null, null, null, "ONLINE", null, null, null))
                .thenReturn(Collections.emptyList());

        monitorService.checkOfflineDevices();

        verify(deviceRegistry, never()).update(anyString(), any(Device.class));
    }

    // ──────────────────── 健康状态查询 ────────────────────

    @Test
    @DisplayName("getHealth: should return healthy=true for alive device")
    void getHealthShouldReturnHealthyForAliveDevice() {
        Device device = onlineDevice(TEST_DEVICE_ID, 1L,
                LocalDateTime.now().minusSeconds(30),
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.getByDeviceId(TEST_DEVICE_ID)).thenReturn(device);
        when(productRegistry.getById(1L)).thenReturn(product("ZHENSHI"));

        var health = monitorService.getHealth(TEST_DEVICE_ID);

        assertThat(health.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(health.getStatus()).isEqualTo("ONLINE");
        assertThat(health.getHealthy()).isTrue();
        assertThat(health.getHeartbeatTimeoutSeconds()).isEqualTo(90L);
    }

    @Test
    @DisplayName("getHealth: should return healthy=false for timed-out device")
    void getHealthShouldReturnUnhealthyForTimedOutDevice() {
        Device device = onlineDevice(TEST_DEVICE_ID, 2L,
                LocalDateTime.now().minusSeconds(300),
                LocalDateTime.now().minusHours(1));

        when(deviceRegistry.getByDeviceId(TEST_DEVICE_ID)).thenReturn(device);
        when(productRegistry.getById(2L)).thenReturn(product("信路通"));

        var health = monitorService.getHealth(TEST_DEVICE_ID);

        assertThat(health.getHealthy()).isFalse();
        assertThat(health.getHeartbeatTimeoutSeconds()).isEqualTo(270L);
    }
}
