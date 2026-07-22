package com.smartparking.deviceaccess.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.adapter.xinlutong.XinlutongMessageHandler;
import com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler;
import com.smartparking.deviceaccess.api.dto.*;
import com.smartparking.deviceaccess.common.enums.DeviceCapability;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import com.smartparking.deviceaccess.common.exception.CapabilityUnsupportedException;
import com.smartparking.deviceaccess.common.exception.DeviceAlreadyExistsException;
import com.smartparking.deviceaccess.common.exception.DeviceNotFoundException;
import com.smartparking.deviceaccess.common.exception.MqttConnectionException;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRelationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DeviceController 集成测试（Web 层切片）。
 * <p>
 * 使用 MockMvc 测试 REST 接口，mock 掉 DeviceService。
 */
@WebMvcTest(DeviceController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("DeviceController API Tests")
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private ZhenshiDeviceCoordinator zhenshiCoordinator;

    @MockBean
    private XinlutongDeviceCoordinator xinlutongCoordinator;

    @MockBean
    private ZhenshiMessageHandler zhenshiMessageHandler;

    @MockBean
    private XinlutongMessageHandler xinlutongMessageHandler;

    @MockBean
    private DeviceProductRegistry productRegistry;

    @MockBean
    private DeviceRelationRegistry relationRegistry;

    @MockBean
    private DeviceMonitorService deviceMonitorService;

    @MockBean
    private DeviceCommandLogService deviceCommandLogService;

    private static final String TEST_DEVICE_ID = "b30113ab-a034d147";

    // ──────────────────── 校时接口 ────────────────────

    @Nested
    @DisplayName("POST /{deviceId}/time/sync")
    class SyncTime {

        @Test
        @DisplayName("Should return 200 on successful time sync")
        void shouldSyncTimeSuccessfully() throws Exception {
            CommandResultDTO result = CommandResultDTO.builder()
                    .success(true)
                    .deviceCode(200)
                    .message("Time synced")
                    .build();
            when(deviceService.syncTime(TEST_DEVICE_ID)).thenReturn(CompletableFuture.completedFuture(result));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/time/sync", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.deviceCode").value(200));
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenDeviceNotFound() throws Exception {
            when(deviceService.syncTime("unknown-device"))
                    .thenThrow(new DeviceNotFoundException("unknown-device"));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/time/sync", "unknown-device")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("Device not found: unknown-device"));
        }

        @Test
        @DisplayName("Should return 503 when MQTT is disconnected")
        void shouldReturn503WhenMqttDisconnected() throws Exception {
            when(deviceService.syncTime(TEST_DEVICE_ID))
                    .thenThrow(new MqttConnectionException("MQTT is not connected"));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/time/sync", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503));
        }

        @Test
        @DisplayName("Should return 500 when command execution fails")
        void shouldReturn500WhenCommandFails() throws Exception {
            CommandResultDTO result = CommandResultDTO.builder()
                    .success(false)
                    .message("Command failed: timeout")
                    .build();
            when(deviceService.syncTime(TEST_DEVICE_ID)).thenReturn(CompletableFuture.completedFuture(result));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/time/sync", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())  // HTTP 200 但业务 code=500
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("Command failed: timeout"));
        }
    }

    // ──────────────────── 开闸 / 关闸接口 ────────────────────

    @Nested
    @DisplayName("POST /{deviceId}/gate/open")
    class OpenGate {

        @Test
        @DisplayName("Should return 200 on successful gate open")
        void shouldOpenGateSuccessfully() throws Exception {
            CommandResultDTO result = CommandResultDTO.builder()
                    .success(true)
                    .deviceCode(0)
                    .message("Gate opened")
                    .build();
            when(deviceService.openGate(TEST_DEVICE_ID)).thenReturn(CompletableFuture.completedFuture(result));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/gate/open", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("Gate opened"));
        }

        @Test
        @DisplayName("Should return 422 when device does not support OPEN_GATE")
        void shouldReturn422WhenCapabilityUnsupported() throws Exception {
            when(deviceService.openGate(TEST_DEVICE_ID))
                    .thenThrow(new CapabilityUnsupportedException(TEST_DEVICE_ID, DeviceCapability.OPEN_GATE));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/gate/open", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(422));
        }

        @Test
        @DisplayName("Should return 500 when command execution fails")
        void shouldReturn500WhenCommandFails() throws Exception {
            CommandResultDTO result = CommandResultDTO.builder()
                    .success(false)
                    .message("Device returned error: timeout")
                    .build();
            when(deviceService.openGate(TEST_DEVICE_ID)).thenReturn(CompletableFuture.completedFuture(result));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/gate/open", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("Device returned error: timeout"));
        }
    }

    @Nested
    @DisplayName("POST /{deviceId}/gate/close")
    class CloseGate {

        @Test
        @DisplayName("Should return 200 on successful gate close")
        void shouldCloseGateSuccessfully() throws Exception {
            CommandResultDTO result = CommandResultDTO.builder()
                    .success(true)
                    .deviceCode(0)
                    .message("Gate closed")
                    .build();
            when(deviceService.closeGate(TEST_DEVICE_ID)).thenReturn(CompletableFuture.completedFuture(result));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/gate/close", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("Gate closed"));
        }

        @Test
        @DisplayName("Should return 422 when device does not support CLOSE_GATE")
        void shouldReturn422WhenCapabilityUnsupported() throws Exception {
            when(deviceService.closeGate(TEST_DEVICE_ID))
                    .thenThrow(new CapabilityUnsupportedException(TEST_DEVICE_ID, DeviceCapability.CLOSE_GATE));

            mockMvc.perform(post("/api/v1/devices/{deviceId}/gate/close", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(422));
        }
    }

    // ──────────────────── 状态查询接口 ────────────────────

    @Nested
    @DisplayName("GET /{deviceId}/status")
    class GetStatus {

        @Test
        @DisplayName("Should return device status when online")
        void shouldReturnOnlineStatus() throws Exception {
            DeviceStatusDTO status = DeviceStatusDTO.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .deviceName("东门入口")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .online(true)
                    .lastOnlineTime("2026-07-10 18:06:00")
                    .build();
            when(deviceService.getStatus(TEST_DEVICE_ID)).thenReturn(status);

            mockMvc.perform(get("/api/v1/devices/{deviceId}/status", TEST_DEVICE_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.deviceId").value(TEST_DEVICE_ID))
                    .andExpect(jsonPath("$.data.online").value(true))
                    .andExpect(jsonPath("$.data.deviceName").value("东门入口"))
                    .andExpect(jsonPath("$.data.brand").value("ZHENSHI"))
                    .andExpect(jsonPath("$.data.model").value("C5H"));
        }

        @Test
        @DisplayName("Should return device status when offline")
        void shouldReturnOfflineStatus() throws Exception {
            DeviceStatusDTO status = DeviceStatusDTO.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .deviceName("东门入口")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .online(false)
                    .lastOnlineTime("2026-07-10 17:00:00")
                    .build();
            when(deviceService.getStatus(TEST_DEVICE_ID)).thenReturn(status);

            mockMvc.perform(get("/api/v1/devices/{deviceId}/status", TEST_DEVICE_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.online").value(false))
                    .andExpect(jsonPath("$.data.lastOnlineTime").value("2026-07-10 17:00:00"));
        }

        @Test
        @DisplayName("Should return null lastOnlineTime when never online")
        void shouldReturnNullLastOnlineTime() throws Exception {
            DeviceStatusDTO status = DeviceStatusDTO.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .deviceName("新设备")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .online(false)
                    .lastOnlineTime(null)
                    .build();
            when(deviceService.getStatus(TEST_DEVICE_ID)).thenReturn(status);

            mockMvc.perform(get("/api/v1/devices/{deviceId}/status", TEST_DEVICE_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lastOnlineTime").doesNotExist());
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenDeviceNotFound() throws Exception {
            when(deviceService.getStatus("unknown-device"))
                    .thenThrow(new DeviceNotFoundException("unknown-device"));

            mockMvc.perform(get("/api/v1/devices/{deviceId}/status", "unknown-device")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("Device not found: unknown-device"));
        }
    }

    // ──────────────────── 边界条件 ────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle device ID with uppercase letters")
        void shouldHandleUppercaseDeviceId() throws Exception {
            DeviceStatusDTO status = DeviceStatusDTO.builder()
                    .deviceId("b30113ab-a034d147")
                    .deviceName("东门入口")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .online(true)
                    .build();
            // Service 内部做了 normalize，所以大写也应该能找到
            when(deviceService.getStatus("B30113AB-A034D147")).thenReturn(status);

            mockMvc.perform(get("/api/v1/devices/{deviceId}/status", "B30113AB-A034D147")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.online").value(true));
        }
    }

    // ═══════════════════════════════════════════
    // v0.2 新增：设备 CRUD 端点
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/devices — Register")
    class RegisterDevice {

        @Test
        @DisplayName("Should register device successfully")
        void shouldRegisterDevice() throws Exception {
            DeviceDTO dto = DeviceDTO.builder()
                    .deviceId("test-001")
                    .deviceName("测试设备")
                    .productId(1L)
                    .brand("ZHENSHI")
                    .model("C5H")
                    .deviceType("CAMERA")
                    .status("OFFLINE")
                    .createTime("2026-07-11 10:00:00")
                    .build();

            when(deviceService.register(any(DeviceRegisterRequest.class))).thenReturn(dto);

            String body = """
                    {"deviceId":"test-001","deviceName":"测试设备","productId":1}""";

            mockMvc.perform(post("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.deviceId").value("test-001"))
                    .andExpect(jsonPath("$.data.deviceName").value("测试设备"))
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        }

        @Test
        @DisplayName("Should return 400 when deviceId is blank")
        void shouldReturn400WhenDeviceIdBlank() throws Exception {
            String body = """
                    {"deviceId":"","deviceName":"测试设备"}""";

            mockMvc.perform(post("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("Should register device with business fields")
        void shouldRegisterDeviceWithBusinessFields() throws Exception {
            DeviceDTO dto = DeviceDTO.builder()
                    .deviceId("test-002")
                    .deviceName("测试设备")
                    .productId(1L)
                    .platformDeviceId("dev_camera_001")
                    .tenantId("tenant_001")
                    .parkingLotId("park_001")
                    .laneId("lane_entry_001")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .deviceType("CAMERA")
                    .status("OFFLINE")
                    .createTime("2026-07-11 10:00:00")
                    .build();

            when(deviceService.register(any(DeviceRegisterRequest.class))).thenReturn(dto);

            String body = """
                    {"deviceId":"test-002","deviceName":"测试设备","productId":1,
                     "platformDeviceId":"dev_camera_001","tenantId":"tenant_001",
                     "parkingLotId":"park_001","laneId":"lane_entry_001"}""";

            mockMvc.perform(post("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.deviceId").value("test-002"))
                    .andExpect(jsonPath("$.data.platformDeviceId").value("dev_camera_001"))
                    .andExpect(jsonPath("$.data.tenantId").value("tenant_001"))
                    .andExpect(jsonPath("$.data.parkingLotId").value("park_001"))
                    .andExpect(jsonPath("$.data.laneId").value("lane_entry_001"));
        }

        @Test
        @DisplayName("Should return 409 when device already exists")
        void shouldReturn409OnDuplicate() throws Exception {
            when(deviceService.register(any(DeviceRegisterRequest.class)))
                    .thenThrow(new DeviceAlreadyExistsException("test-001"));

            String body = """
                    {"deviceId":"test-001","deviceName":"测试设备","productId":1}""";

            mockMvc.perform(post("/api/v1/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("Device already exists: test-001"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/devices — List")
    class ListDevices {

        @Test
        @DisplayName("Should return device list")
        void shouldReturnDeviceList() throws Exception {
            DeviceDTO dto = DeviceDTO.builder()
                    .deviceId("test-001")
                    .deviceName("测试设备")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .status("OFFLINE")
                    .build();

            when(deviceService.listDevices(null, null, null, null, null, null, null)).thenReturn(List.of(dto));

            mockMvc.perform(get("/api/v1/devices")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].deviceId").value("test-001"));
        }

        @Test
        @DisplayName("Should return empty list when no devices")
        void shouldReturnEmptyList() throws Exception {
            when(deviceService.listDevices(null, null, null, null, null, null, null))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/devices")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("Should pass query params to service")
        void shouldPassQueryParams() throws Exception {
            when(deviceService.listDevices("东门", "CAMERA", null, "ONLINE", null, null, null))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/devices")
                            .param("keyword", "东门")
                            .param("deviceType", "CAMERA")
                            .param("status", "ONLINE")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should pass business query params to service")
        void shouldPassBusinessQueryParams() throws Exception {
            when(deviceService.listDevices(null, null, null, null, "tenant_001", "park_001", "lane_001"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/devices")
                            .param("tenantId", "tenant_001")
                            .param("parkingLotId", "park_001")
                            .param("laneId", "lane_001")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/devices/{deviceId} — Detail")
    class GetDeviceDetail {

        @Test
        @DisplayName("Should return device detail")
        void shouldReturnDeviceDetail() throws Exception {
            DeviceDetailDTO dto = DeviceDetailDTO.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .deviceName("东门入口")
                    .productId(1L)
                    .brand("ZHENSHI")
                    .model("C5H")
                    .deviceType("CAMERA")
                    .status("ONLINE")
                    .lastOnlineTime("2026-07-11 10:00:00")
                    .createTime("2026-07-10 09:00:00")
                    .build();

            when(deviceService.getDeviceDetail(TEST_DEVICE_ID)).thenReturn(dto);

            mockMvc.perform(get("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.deviceId").value(TEST_DEVICE_ID))
                    .andExpect(jsonPath("$.data.lastOnlineTime").value("2026-07-11 10:00:00"));
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(deviceService.getDeviceDetail("unknown"))
                    .thenThrow(new DeviceNotFoundException("unknown"));

            mockMvc.perform(get("/api/v1/devices/{deviceId}", "unknown")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/devices/{deviceId} — Update")
    class UpdateDevice {

        @Test
        @DisplayName("Should update device name")
        void shouldUpdateDeviceName() throws Exception {
            DeviceDTO dto = DeviceDTO.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .deviceName("新名称")
                    .brand("ZHENSHI")
                    .model("C5H")
                    .status("OFFLINE")
                    .build();

            when(deviceService.updateDevice(eq(TEST_DEVICE_ID), any(DeviceUpdateRequest.class)))
                    .thenReturn(dto);

            String body = """
                    {"deviceName":"新名称"}""";

            mockMvc.perform(put("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.deviceName").value("新名称"));
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(deviceService.updateDevice(eq("unknown"), any(DeviceUpdateRequest.class)))
                    .thenThrow(new DeviceNotFoundException("unknown"));

            String body = """
                    {"deviceName":"新名称"}""";

            mockMvc.perform(put("/api/v1/devices/{deviceId}", "unknown")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/devices/{deviceId} — Delete")
    class DeleteDevice {

        @Test
        @DisplayName("Should delete device successfully")
        void shouldDeleteDevice() throws Exception {
            mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new DeviceNotFoundException("unknown"))
                    .when(deviceService).deleteDevice("unknown");

            mockMvc.perform(delete("/api/v1/devices/{deviceId}", "unknown")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/devices/{deviceId}/peripheral/display — Peripheral Control")
    class ControlDisplay {

        @Test
        @DisplayName("Should return 200 for SET_MODE")
        void shouldSetDisplayMode() throws Exception {
            PeripheralControlResult result = PeripheralControlResult.builder()
                    .success(true).action("SET_MODE")
                    .message("Display mode set to TWO_LINE").build();

            when(deviceService.controlPeripheral(eq(TEST_DEVICE_ID), any(PeripheralControlRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(result));

            PeripheralControlRequest req = new PeripheralControlRequest();
            req.setAction("SET_MODE");
            req.setMode("TWO_LINE");

            mockMvc.perform(post("/api/v1/devices/{deviceId}/peripheral/display", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("Display mode set to TWO_LINE"));
        }
    }

    // ═══════════════════════════════════════════
    // v0.3 新增：显示内容控制
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("POST /{deviceId}/display/text — Display Text")
    class DisplayText {

        @Test
        @DisplayName("Should return 200 on success")
        void shouldReturn200() throws Exception {
            DisplayResult result = DisplayResult.builder()
                    .success(true).build();
            when(deviceService.displayText(eq(TEST_DEVICE_ID), any(DisplayTextRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(result));

            DisplayTextRequest req = new DisplayTextRequest();
            req.setContent("欢迎光临");
            req.setDirection(DisplayDirection.HORIZONTAL);

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/text", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true));
        }

        @Test
        @DisplayName("Should return 400 when content is blank")
        void shouldReturn400WhenContentBlank() throws Exception {
            String body = """
                    {"content":"","direction":"HORIZONTAL"}""";

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/text", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("Should return 400 when direction is missing")
        void shouldReturn400WhenDirectionMissing() throws Exception {
            String body = """
                    {"content":"欢迎光临"}""";

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/text", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenDeviceNotFound() throws Exception {
            when(deviceService.displayText(eq("unknown"), any(DisplayTextRequest.class)))
                    .thenThrow(new DeviceNotFoundException("unknown"));

            DisplayTextRequest req = new DisplayTextRequest();
            req.setContent("欢迎光临");
            req.setDirection(DisplayDirection.HORIZONTAL);

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/text", "unknown")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("Should return 503 when MQTT disconnected")
        void shouldReturn503WhenMqttDisconnected() throws Exception {
            when(deviceService.displayText(eq(TEST_DEVICE_ID), any(DisplayTextRequest.class)))
                    .thenThrow(new MqttConnectionException("MQTT is not connected"));

            DisplayTextRequest req = new DisplayTextRequest();
            req.setContent("欢迎光临");
            req.setDirection(DisplayDirection.HORIZONTAL);

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/text", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503));
        }
    }

    @Nested
    @DisplayName("POST /{deviceId}/display/save — Save Display")
    class SaveDisplay {

        @Test
        @DisplayName("Should return 200 on success")
        void shouldReturn200() throws Exception {
            DisplayResult result = DisplayResult.builder()
                    .success(true).build();
            when(deviceService.saveDisplay(eq(TEST_DEVICE_ID), any(DisplaySaveRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(result));

            DisplaySaveRequest req = new DisplaySaveRequest();
            req.setContent("欢迎光临\n请减速慢行");
            req.setDirection(DisplayDirection.HORIZONTAL);

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/save", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true));
        }

        @Test
        @DisplayName("Should return 400 when content is blank")
        void shouldReturn400WhenContentBlank() throws Exception {
            String body = """
                    {"content":"  ","direction":"HORIZONTAL"}""";

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/save", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void shouldReturn404WhenDeviceNotFound() throws Exception {
            when(deviceService.saveDisplay(eq("unknown"), any(DisplaySaveRequest.class)))
                    .thenThrow(new DeviceNotFoundException("unknown"));

            DisplaySaveRequest req = new DisplaySaveRequest();
            req.setContent("欢迎光临");
            req.setDirection(DisplayDirection.HORIZONTAL);

            mockMvc.perform(post("/api/v1/devices/{deviceId}/display/save", "unknown")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ═══════════════════════════════════════════
    // v0.4+: 视频流信息查询
}
