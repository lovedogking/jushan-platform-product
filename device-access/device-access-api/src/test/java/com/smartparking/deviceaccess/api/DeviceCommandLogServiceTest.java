package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.common.entity.DeviceCommandLog;
import com.smartparking.deviceaccess.common.entity.DeviceCommandLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceCommandLogService 单元测试。
 * <p>
 * 验证命令日志记录：请求记录、响应更新、失败情况、历史查询。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceCommandLogService Unit Tests")
class DeviceCommandLogServiceTest {

    @Mock
    private DeviceCommandLogMapper commandLogMapper;

    @InjectMocks
    private DeviceCommandLogService commandLogService;

    private static final String TEST_DEVICE_ID = "test-device-001";
    private static final String TEST_BRAND = "ZHENSHI";
    private static final String TEST_COMMAND = "OPEN_GATE";
    private static final String TEST_PLATE = "鲁Q12345";
    private static final String TEST_PLATFORM_ID = "plat-001";
    private static final String TEST_TENANT = "tenant-001";
    private static final String TEST_PARKING_LOT = "lot-001";
    private static final String TEST_LANE = "lane-001";

    @BeforeEach
    void setUp() {
        // @InjectMocks 自动注入
    }

    // ──────────────────── 记录请求 ────────────────────

    @Test
    @DisplayName("Should record request with all fields")
    void shouldRecordRequest() {
        when(commandLogMapper.insert(any(DeviceCommandLog.class))).thenAnswer(inv -> {
            DeviceCommandLog log = inv.getArgument(0);
            log.setId(1L);
            return 1;
        });

        DeviceCommandLog result = commandLogService.recordRequest(
                TEST_DEVICE_ID, TEST_BRAND, TEST_COMMAND, TEST_PLATE,
                TEST_PLATFORM_ID, TEST_TENANT, TEST_PARKING_LOT, TEST_LANE);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(result.getBrand()).isEqualTo(TEST_BRAND);
        assertThat(result.getCommandType()).isEqualTo(TEST_COMMAND);
        assertThat(result.getPlateNo()).isEqualTo(TEST_PLATE);
        assertThat(result.getPlatformDeviceId()).isEqualTo(TEST_PLATFORM_ID);
        assertThat(result.getTenantId()).isEqualTo(TEST_TENANT);
        assertThat(result.getParkingLotId()).isEqualTo(TEST_PARKING_LOT);
        assertThat(result.getLaneId()).isEqualTo(TEST_LANE);
        assertThat(result.getSuccess()).isFalse(); // 初始为 false
        assertThat(result.getRequestTime()).isNotNull();

        verify(commandLogMapper).insert(any(DeviceCommandLog.class));
    }

    @Test
    @DisplayName("Should record request with null optional fields")
    void shouldRecordRequestWithNullOptionalFields() {
        when(commandLogMapper.insert(any(DeviceCommandLog.class))).thenAnswer(inv -> {
            DeviceCommandLog log = inv.getArgument(0);
            log.setId(2L);
            return 1;
        });

        DeviceCommandLog result = commandLogService.recordRequest(
                TEST_DEVICE_ID, TEST_BRAND, "SYNC_TIME",
                null, null, null, null, null);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getPlateNo()).isNull();
        assertThat(result.getPlatformDeviceId()).isNull();
        assertThat(result.getTenantId()).isNull();
        assertThat(result.getParkingLotId()).isNull();
        assertThat(result.getLaneId()).isNull();
    }

    // ──────────────────── 记录响应 ────────────────────

    @Test
    @DisplayName("Should record successful response")
    void shouldRecordSuccessfulResponse() {
        when(commandLogMapper.updateById(any(DeviceCommandLog.class))).thenReturn(1);

        commandLogService.recordResponse(1L, true, 200, "Gate opened");

        verify(commandLogMapper).updateById((DeviceCommandLog) argThat(log ->
                ((DeviceCommandLog) log).getId() == 1L &&
                        ((DeviceCommandLog) log).getSuccess() &&
                        ((DeviceCommandLog) log).getDeviceCode() == 200 &&
                        "Gate opened".equals(((DeviceCommandLog) log).getMessage()) &&
                        ((DeviceCommandLog) log).getResponseTime() != null
        ));
    }

    @Test
    @DisplayName("Should record failed response")
    void shouldRecordFailedResponse() {
        when(commandLogMapper.updateById(any(DeviceCommandLog.class))).thenReturn(1);

        commandLogService.recordResponse(2L, false, 500, "Command failed: timeout");

        verify(commandLogMapper).updateById((DeviceCommandLog) argThat(log ->
                ((DeviceCommandLog) log).getId() == 2L &&
                        !((DeviceCommandLog) log).getSuccess() &&
                        ((DeviceCommandLog) log).getDeviceCode() == 500 &&
                        "Command failed: timeout".equals(((DeviceCommandLog) log).getMessage())
        ));
    }

    @Test
    @DisplayName("Should record response with null deviceCode")
    void shouldRecordResponseWithNullDeviceCode() {
        when(commandLogMapper.updateById(any(DeviceCommandLog.class))).thenReturn(1);

        commandLogService.recordResponse(3L, false, null, "Exception: timeout");

        verify(commandLogMapper).updateById((DeviceCommandLog) argThat(log ->
                ((DeviceCommandLog) log).getId() == 3L &&
                        !((DeviceCommandLog) log).getSuccess() &&
                        ((DeviceCommandLog) log).getDeviceCode() == null &&
                        "Exception: timeout".equals(((DeviceCommandLog) log).getMessage())
        ));
    }

    @Test
    @DisplayName("Should not throw when recordResponse mapper fails")
    void shouldNotThrowWhenMapperFails() {
        when(commandLogMapper.updateById(any(DeviceCommandLog.class)))
                .thenThrow(new RuntimeException("DB error"));

        // 不应抛异常
        commandLogService.recordResponse(1L, true, 200, "OK");
    }

    // ──────────────────── 查询历史 ────────────────────

    @Test
    @DisplayName("Should query command history by deviceId")
    void shouldQueryByDeviceId() {
        DeviceCommandLog log1 = new DeviceCommandLog();
        log1.setId(1L);
        log1.setDeviceId(TEST_DEVICE_ID);
        log1.setCommandType("OPEN_GATE");

        DeviceCommandLog log2 = new DeviceCommandLog();
        log2.setId(2L);
        log2.setDeviceId(TEST_DEVICE_ID);
        log2.setCommandType("CLOSE_GATE");

        when(commandLogMapper.selectList(any())).thenReturn(List.of(log1, log2));

        List<DeviceCommandLog> result = commandLogService.queryByDeviceId(TEST_DEVICE_ID, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCommandType()).isEqualTo("OPEN_GATE");
        assertThat(result.get(1).getCommandType()).isEqualTo("CLOSE_GATE");
    }

    @Test
    @DisplayName("Should return empty list when no history found")
    void shouldReturnEmptyListWhenNoHistory() {
        when(commandLogMapper.selectList(any())).thenReturn(List.of());

        List<DeviceCommandLog> result = commandLogService.queryByDeviceId("unknown-device", 10);

        assertThat(result).isEmpty();
    }
}
