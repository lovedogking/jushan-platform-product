package com.jushan.platform.modules.device.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.platform.modules.device.dto.DeviceWebhookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DeviceWebhookController 单元测试。
 * <p>
 * 使用 MockMvc standalone 模式测试 Webhook 接收端点，
 * 覆盖正常事件、幂等、设备不存在、方向不匹配等场景。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DeviceWebhookControllerTest {

    @Mock
    private DeviceWebhookService deviceWebhookService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // standalone 模式，不加载 Spring Security 过滤器
        DeviceWebhookController controller = new DeviceWebhookController(deviceWebhookService);
        // 注入 @Value 字段（standalone setup 不处理 Spring 注解）
        ReflectionTestUtils.setField(controller, "allowedIps", "*");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== 正常事件推送 ====================

    @Test
    @DisplayName("正常车牌识别事件：返回 200，事件被处理")
    void shouldAcceptValidPlateRecognizedEvent() throws Exception {
        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEnvelope("京A12345", "ENTRY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        ArgumentCaptor<DeviceWebhookEvent> captor = ArgumentCaptor.forClass(DeviceWebhookEvent.class);
        verify(deviceWebhookService, times(1)).processEvent(captor.capture());
        DeviceWebhookEvent captured = captor.getValue();
        assertThat(captured.getEventId()).isEqualTo("evt-001");
        assertThat(captured.getPlateNumber()).isEqualTo("京A12345");
        assertThat(captured.getDirection()).isEqualTo("ENTRY");
    }

    @Test
    @DisplayName("出场事件：返回 200")
    void shouldAcceptExitEvent() throws Exception {
        DeviceWebhookEvent event = createValidEvent("京B88888", "EXIT");

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceWebhookService, times(1)).processEvent(any(DeviceWebhookEvent.class));
    }

    @Test
    @DisplayName("带车牌颜色和置信度的事件：返回 200")
    void shouldAcceptEventWithPlateColorAndConfidence() throws Exception {
        java.util.Map<String, Object> envelope = createEnvelope("沪C12345", "ENTRY");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) envelope.get("payload");
        payload.put("plateColor", "BLUE");
        payload.put("confidence", 0.95);

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isOk());

        ArgumentCaptor<DeviceWebhookEvent> captor = ArgumentCaptor.forClass(DeviceWebhookEvent.class);
        verify(deviceWebhookService).processEvent(captor.capture());
        assertThat(captor.getValue().getPlateColor()).isEqualTo("BLUE");
        assertThat(captor.getValue().getConfidence()).isEqualTo(0.95);
    }

    // ==================== 幂等性 ====================

    @Test
    @DisplayName("重复 eventId：仍然返回 200（幂等由 Service 保证）")
    void shouldReturnOkForDuplicateEventId() throws Exception {
        DeviceWebhookEvent event = createValidEvent("京A12345", "ENTRY");
        String json = objectMapper.writeValueAsString(event);

        // 第一次请求
        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        // 第二次请求（相同 eventId）
        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        // 两次都调用了 Service，幂等性由 Service 内部处理
        verify(deviceWebhookService, times(2)).processEvent(any(DeviceWebhookEvent.class));
    }

    // ==================== deviceSn 不存在 ====================

    @Test
    @DisplayName("deviceSn 不存在：返回 200（Service 内部记录错误并静默返回）")
    void shouldReturnOkWhenDeviceSnNotFound() throws Exception {
        DeviceWebhookEvent event = createValidEvent("京Z99999", "ENTRY");
        event.setDeviceSn("SN-NONEXISTENT");

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceWebhookService).processEvent(any(DeviceWebhookEvent.class));
    }

    // ==================== 方向不匹配 ====================

    @Test
    @DisplayName("方向不匹配：返回 200（Service 内部拒绝并记录审计）")
    void shouldReturnOkWhenDirectionMismatch() throws Exception {
        DeviceWebhookEvent event = createValidEvent("京D11111", "EXIT");
        // 车道方向为 ENTRY，事件方向为 EXIT——Service 内部拒绝

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceWebhookService).processEvent(any(DeviceWebhookEvent.class));
    }

    // ==================== 异常处理 ====================

    @Test
    @DisplayName("Service 内部异常：返回 200（异常被内部消化）")
    void shouldReturnOkWhenServiceThrowsException() throws Exception {
        DeviceWebhookEvent event = createValidEvent("京E22222", "ENTRY");
        doThrow(new RuntimeException("模拟内部异常"))
                .when(deviceWebhookService).processEvent(any(DeviceWebhookEvent.class));

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 字段缺失 ====================

    @Test
    @DisplayName("缺少车牌号：返回 200（Service 内部跳过）")
    void shouldReturnOkWhenPlateNumberMissing() throws Exception {
        DeviceWebhookEvent event = new DeviceWebhookEvent();
        event.setEventId("evt-no-plate");
        event.setEventType("PLATE_RECOGNIZED");
        event.setDeviceSn("SN-001");
        event.setDirection("ENTRY");
        // plateNumber 为 null

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceWebhookService).processEvent(any(DeviceWebhookEvent.class));
    }

    @Test
    @DisplayName("缺少 deviceSn：返回 200（Service 内部跳过）")
    void shouldReturnOkWhenDeviceSnMissing() throws Exception {
        DeviceWebhookEvent event = new DeviceWebhookEvent();
        event.setEventId("evt-no-sn");
        event.setEventType("PLATE_RECOGNIZED");
        event.setPlateNumber("京F33333");
        event.setDirection("ENTRY");
        // deviceSn 为 null

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk());

        verify(deviceWebhookService).processEvent(any(DeviceWebhookEvent.class));
    }

    @Test
    @DisplayName("缺少 eventId：返回 200（不做幂等校验但正常处理）")
    void shouldReturnOkWhenEventIdMissing() throws Exception {
        DeviceWebhookEvent event = new DeviceWebhookEvent();
        event.setEventId(null);
        event.setEventType("PLATE_RECOGNIZED");
        event.setDeviceSn("SN-001");
        event.setPlateNumber("京G44444");
        event.setDirection("ENTRY");

        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk());

        verify(deviceWebhookService).processEvent(any(DeviceWebhookEvent.class));
    }

    // ==================== 空请求体 ====================

    @Test
    @DisplayName("空请求体：返回 200（Service 内部处理）")
    void shouldReturnOkForEmptyBody() throws Exception {
        mockMvc.perform(post("/api/v1/device-webhook/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建一个合法的测试事件。
     */
    /**
     * 构建 DA v0.4 信封格式事件（Controller 按嵌套 payload 解析）。
     */
    private java.util.Map<String, Object> createEnvelope(String plateNo, String direction) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("plateNo", plateNo);
        payload.put("plateColor", "BLUE");
        payload.put("confidence", 98);
        payload.put("direction", direction);
        payload.put("imagePath", "http://device-access:8081/images/capture.jpg");
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("eventId", "evt-001");
        envelope.put("eventType", "PLATE_RECOGNIZED");
        envelope.put("deviceSn", "SN-TEST-001");
        envelope.put("occurredAt", "2026-07-15T10:30:00");
        envelope.put("payload", payload);
        return envelope;
    }

    private DeviceWebhookEvent createValidEvent(String plateNumber, String direction) {
        DeviceWebhookEvent event = new DeviceWebhookEvent();
        event.setEventId("evt-001");
        event.setEventType("PLATE_RECOGNIZED");
        event.setDeviceSn("SN-TEST-001");
        event.setPlateNumber(plateNumber);
        event.setPlateColor("BLUE");
        event.setConfidence(0.98);
        event.setCaptureTime("2026-07-15T10:30:00");
        event.setImageUrl("http://device-access:8081/images/capture.jpg");
        event.setDirection(direction);
        // 以下字段为 Device Access 推送值，不可信任
        event.setTenantId(1L);
        event.setParkingLotId(100L);
        event.setLaneId(10L);
        return event;
    }
}
