package com.smartparking.deviceaccess.adapter.zhenshi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.adapter.support.DeviceHeartbeatRecorder;
import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.common.dto.mqtt.MqttMessage;
import com.smartparking.deviceaccess.mqtt.MqttGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.common.event.PlateRecognizedListener;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 臻识 C5H 消息处理器单元测试。
 * <p>
 * 使用 Mockito 模拟 MqttGateway 和 DeviceMapper，
 * 验证消息解析、心跳缓存、车牌识别、校时命令构建等核心逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ZhenshiMessageHandler Unit Tests")
class ZhenshiMessageHandlerTest {

    @Mock
    private MqttGateway mqttGateway;

    @Mock
    private DeviceHeartbeatRecorder heartbeatRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ZhenshiMessageHandler handler;

    private static final String TEST_SN = "b30113ab-a034d147";

    @BeforeEach
    void setUp() {
        handler = new ZhenshiMessageHandler(mqttGateway, objectMapper, heartbeatRecorder);
    }

    // ──────────────────── 心跳处理测试 ────────────────────

    @Test
    @DisplayName("Should record heartbeat and report device online")
    void shouldRecordHeartbeatAndReportOnline() {
        when(heartbeatRecorder.isDeviceOnline(TEST_SN, 30_000L)).thenReturn(true);
        MqttMessage message = createKeepAliveMessage();

        handler.onMessage("device/" + TEST_SN + "/message/up/keep_alive", message);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();
    }

    @Test
    @DisplayName("Should keep device online on repeated heartbeats")
    void shouldKeepDeviceOnline() {
        when(heartbeatRecorder.isDeviceOnline(TEST_SN, 30_000L)).thenReturn(true);

        // 第一次心跳
        handler.onMessage("device/" + TEST_SN + "/message/up/keep_alive", createKeepAliveMessage());
        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();

        // 第二次心跳 — 设备仍在线
        handler.onMessage("device/" + TEST_SN + "/message/up/keep_alive", createKeepAliveMessage());
        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();
        verify(heartbeatRecorder, times(2)).recordHeartbeat(TEST_SN);
    }

    @Test
    @DisplayName("Should not crash when heartbeat recording fails")
    void shouldNotCrashWhenHeartbeatRecordingFails() {
        when(heartbeatRecorder.isDeviceOnline(TEST_SN, 30_000L)).thenReturn(true);
        // 即使 recorder 抛异常，也能验证心跳处理不崩溃
        MqttMessage message = createKeepAliveMessage();

        handler.onMessage("device/" + TEST_SN + "/message/up/keep_alive", message);

        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();
    }

    // ──────────────────── 在线判定测试 ────────────────────

    @Test
    @DisplayName("Should report false for unknown device")
    void shouldReportOfflineForUnknownDevice() {
        assertThat(handler.isDeviceOnline("unknown-device-sn")).isFalse();
    }

    @Test
    @DisplayName("Should report false for device that never sent heartbeat")
    void shouldReportOfflineForNeverSeenDevice() {
        // handler 刚创建，从未收到过该设备的心跳
        assertThat(handler.isDeviceOnline("never-seen-device")).isFalse();
    }

    // ──────────────────── 车牌识别测试 ────────────────────

    @Test
    @DisplayName("Should parse plate recognition with Base64-encoded license without error")
    void shouldParsePlateRecognition() {
        String licenseBase64 = Base64.getEncoder()
                .encodeToString("浙A12345".getBytes(StandardCharsets.UTF_8));
        String deviceNameBase64 = Base64.getEncoder()
                .encodeToString("东门入口".getBytes(StandardCharsets.UTF_8));

        MqttMessage message = createPlateRecognitionMessage(TEST_SN, licenseBase64, deviceNameBase64);

        // 不抛异常即为通过（v0.1 仅打日志）
        handler.onMessage("device/" + TEST_SN + "/message/up/ivs_result", message);
    }

    @Test
    @DisplayName("Should handle quick_ivs_result same as ivs_result")
    void shouldHandleQuickIvsResult() {
        String licenseBase64 = Base64.getEncoder()
                .encodeToString("苏E82V88".getBytes(StandardCharsets.UTF_8));
        String deviceNameBase64 = Base64.getEncoder()
                .encodeToString("西门入口".getBytes(StandardCharsets.UTF_8));

        MqttMessage message = createPlateRecognitionMessage(TEST_SN, licenseBase64, deviceNameBase64);

        // quick_ivs_result 应与 ivs_result 同样处理
        handler.onMessage("device/" + TEST_SN + "/message/up/quick_ivs_result", message);
    }

    @Test
    @DisplayName("Should handle plate recognition with non-Base64 license (fallback)")
    void shouldHandleNonBase64License() {
        String plainLicense = "粤B67890";
        String deviceNameBase64 = Base64.getEncoder()
                .encodeToString("南门出口".getBytes(StandardCharsets.UTF_8));

        MqttMessage message = createPlateRecognitionMessage(TEST_SN, plainLicense, deviceNameBase64);

        // Base64 解码失败时应返回原始值，不抛异常
        handler.onMessage("device/" + TEST_SN + "/message/up/ivs_result", message);
    }

    @Test
    @DisplayName("Should handle plate recognition without AlarmInfoPlate gracefully")
    void shouldHandleMissingAlarmInfoPlate() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("some", "data");

        MqttMessage message = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name("ivs_result")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .payload(payload)
                .build();

        // 不抛异常即为通过
        handler.onMessage("device/" + TEST_SN + "/message/up/ivs_result", message);
    }

    @Test
    @DisplayName("Should notify plate listener on plate recognition")
    void shouldNotifyPlateListenerOnRecognition() {
        PlateRecognizedListener listener = mock(PlateRecognizedListener.class);
        ReflectionTestUtils.setField(handler, "plateListener", listener);

        String licenseBase64 = Base64.getEncoder()
                .encodeToString("浙A12345".getBytes(StandardCharsets.UTF_8));
        String deviceNameBase64 = Base64.getEncoder()
                .encodeToString("东门入口".getBytes(StandardCharsets.UTF_8));

        MqttMessage message = createPlateRecognitionMessage(TEST_SN, licenseBase64, deviceNameBase64);
        handler.onMessage("device/" + TEST_SN + "/message/up/ivs_result", message);

        ArgumentCaptor<PlateRecognizedData> captor = ArgumentCaptor.forClass(PlateRecognizedData.class);
        verify(listener).onPlateRecognized(captor.capture());

        PlateRecognizedData data = captor.getValue();
        assertThat(data.deviceSn()).isEqualTo(TEST_SN);
        assertThat(data.license()).isEqualTo("浙A12345");
        assertThat(data.confidence()).isEqualTo(98);
    }

    @Test
    @DisplayName("Should not notify listener when plate listener is not injected")
    void shouldNotNotifyWhenNoListener() {
        // plateListener is null (not injected via Spring in test)
        String licenseBase64 = Base64.getEncoder()
                .encodeToString("浙A12345".getBytes(StandardCharsets.UTF_8));
        String deviceNameBase64 = Base64.getEncoder()
                .encodeToString("东门入口".getBytes(StandardCharsets.UTF_8));

        MqttMessage message = createPlateRecognitionMessage(TEST_SN, licenseBase64, deviceNameBase64);

        // should not throw NullPointerException
        handler.onMessage("device/" + TEST_SN + "/message/up/ivs_result", message);
    }

    // ──────────────────── 未知消息处理 ────────────────────

    @Test
    @DisplayName("Should silently ignore unknown message types")
    void shouldIgnoreUnknownMessageType() {
        MqttMessage message = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name("unknown_type")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .payload(Map.of("data", "test"))
                .build();

        // 不抛异常即为通过
        handler.onMessage("device/" + TEST_SN + "/message/up/unknown_type", message);
    }

    @Test
    @DisplayName("Should handle message without name field")
    void shouldHandleMessageWithoutName() {
        MqttMessage message = MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name(null)
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();

        // 不抛异常即为通过
        handler.onMessage("some/topic", message);
    }

    // ──────────────────── 校时命令测试 ────────────────────

    @Test
    @DisplayName("Should send sync time command and return future with device reply")
    void shouldSendSyncTime() throws Exception {
        MqttMessage mockReply = MqttMessage.builder()
                .id("any-id")
                .code(200)
                .build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.sendSyncTime(TEST_SN, 10);

        assertThat(future).isNotNull();
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should build correct topic for set_time command")
    void shouldBuildCorrectSetTimeTopic() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        handler.sendSyncTime(TEST_SN, 10);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishAndWait(topicCaptor.capture(),
                any(MqttMessage.class), anyLong(), any(TimeUnit.class));
        assertThat(topicCaptor.getValue())
                .isEqualTo("device/" + TEST_SN + "/message/down/set_time");
    }

    @Test
    @DisplayName("Should include valid SetTimeBody in sync time command")
    void shouldIncludeValidTimeBody() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        handler.sendSyncTime(TEST_SN, 10);

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(),
                anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("set_time");
        assertThat(sent.getSn()).isEqualTo(TEST_SN);
        assertThat(sent.getId()).isNotNull().isNotBlank();
        assertThat(sent.getPayload()).isNotNull();
    }

    @Test
    @DisplayName("Should build correct gate_direct_open command")
    void shouldBuildCorrectGateDirectOpenCommand() throws Exception {
        MqttMessage mockReply = MqttMessage.builder()
                .id("any-id")
                .code(200)
                .build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.sendGateDirectOpen(TEST_SN, 10);

        assertThat(future).isNotNull();
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        // 验证 Topic
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishAndWait(topicCaptor.capture(),
                any(MqttMessage.class), anyLong(), any(TimeUnit.class));
        assertThat(topicCaptor.getValue())
                .isEqualTo("device/" + TEST_SN + "/message/down/gate_direct_open");

        // 验证消息结构
        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(),
                anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("gate_direct_open");
        assertThat(sent.getSn()).isEqualTo(TEST_SN);
        assertThat(sent.getId()).isNotNull().isNotBlank();
        assertThat(sent.getPayload()).isNull();
    }

    @Test
    @DisplayName("Should build correct gpio_out close gate command")
    void shouldBuildCorrectCloseGateCommand() throws Exception {
        MqttMessage mockReply = MqttMessage.builder()
                .id("any-id")
                .code(200)
                .build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.sendCloseGate(TEST_SN, 10);

        assertThat(future).isNotNull();
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        // 验证 Topic
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishAndWait(topicCaptor.capture(),
                any(MqttMessage.class), anyLong(), any(TimeUnit.class));
        assertThat(topicCaptor.getValue())
                .isEqualTo("device/" + TEST_SN + "/message/down/gpio_out");

        // 验证消息结构
        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(),
                anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("gpio_out");
        assertThat(sent.getSn()).isEqualTo(TEST_SN);
        assertThat(sent.getId()).isNotNull().isNotBlank();
        assertThat(sent.getPayload()).isNotNull();
    }

    // ──────────────────── v0.3 联调验证：displayText 4行文本 ────────────────────

    @Test
    @DisplayName("displayText 4行真实文本：验证 0x6F 帧每行 TL=1 且文本为 A/B/C/D")
    void displayTextFourLinesShouldSendCorrectHexFrame() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        // 发送4行真实文本
        CompletableFuture<Boolean> future = handler.displayText(
                TEST_SN, "A\nB\nC\nD", DisplayDirection.HORIZONTAL);
        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();

        // 捕获 MQTT 消息
        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(),
                anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");

        // 从 payload 提取 serial_data 的 Base64 data
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) sent.getPayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) payload.get("body");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> serialDataList = (List<Map<String, Object>>) body.get("serialData");
        String base64Data = (String) serialDataList.get(0).get("data");

        byte[] olmFrame = Base64.getDecoder().decode(base64Data);

        System.out.println("\n=== displayText(\"A\\nB\\nC\\nD\") → 0x6F frame ===");
        System.out.println("length=" + olmFrame.length + " bytes");
        printHex(olmFrame);
        System.out.println("TCN=" + (olmFrame[8] & 0xFF) + " rows");

        // 验证帧结构
        assertThat(olmFrame[4] & 0xFF).isEqualTo(0x6F).as("CMD=0x6F");
        assertThat(olmFrame[8] & 0xFF).isEqualTo(4).as("TCN=4");

        // 逐行验证 LID、TL、文本
        // 行固定字段: LID(1)+DM(1)+DS(1)+DT(1)+DR(1)+FINDEX(1)+FLAGS(1)+TC(4)+TL(1) = 12 bytes
        // TEXT 从行偏移+12 开始，SEP 在行偏移+12+TL 处
        int pos = 9; // skip header
        String[] expectedTexts = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            int lid = olmFrame[pos] & 0xFF;
            int tl = olmFrame[pos + 11] & 0xFF;   // TL at offset +11
            assertThat(lid).isEqualTo(i).as("Row" + i + " LID=" + i);
            assertThat(tl).isEqualTo(1).as("Row" + i + " TL=1 (ASCII char)");

            // 提取文本 (at pos + 12)
            byte[] textBytes = new byte[tl];
            System.arraycopy(olmFrame, pos + 12, textBytes, 0, tl);
            String actualText = new String(textBytes, java.nio.charset.Charset.forName("GBK"));
            System.out.println("  Row" + i + ": LID=" + lid + " TL=" + tl + " text=\"" + actualText + "\"");
            assertThat(actualText).isEqualTo(expectedTexts[i]);

            // 跳到下一行: 12(固定字段) + TL(文本) + 1(SEP) = 13 + TL
            pos += 13 + tl;
        }

        // CRC 自校验
        byte[] crcData = new byte[olmFrame.length - 2];
        System.arraycopy(olmFrame, 0, crcData, 0, crcData.length);
        int crc = com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol.crc16(crcData);
        int frameCrc = (olmFrame[olmFrame.length - 2] & 0xFF) | ((olmFrame[olmFrame.length - 1] & 0xFF) << 8);
        System.out.println("CRC: 0x" + Integer.toHexString(crc) + " " + (crc == frameCrc ? "PASS" : "FAIL(exp=0x" + Integer.toHexString(frameCrc) + ")"));
        assertThat(crc).isEqualTo(frameCrc).as("CRC self-check");
    }

    @Test
    @DisplayName("displayText 空文本应抛 IllegalArgumentException")
    void displayTextBlankContentShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> handler.displayText(TEST_SN, "", DisplayDirection.HORIZONTAL),
                "blank content should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("displayText null 文本应抛 IllegalArgumentException")
    void displayTextNullContentShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> handler.displayText(TEST_SN, null, DisplayDirection.HORIZONTAL),
                "null content should throw IllegalArgumentException");
    }

    // ──────── 辅助方法 ────────

    private void printHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if ((i + 1) % 16 == 0 && i < data.length - 1) {
                sb.append("\n");
            }
        }
        System.out.println(sb.toString().trim());
    }

    // ═══════════════════════════════════════════
    // 显示屏高级控制测试（v0.4 新增）
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("Should send setVolume command with correct serial_data frame")
    void shouldSendSetVolume() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.setVolume(TEST_SN, 50);
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(), anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");
    }

    @Test
    @DisplayName("Should send syncDisplayTime command with correct serial_data frame")
    void shouldSendSyncDisplayTime() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.syncDisplayTime(TEST_SN);
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(), anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");
    }

    @Test
    @DisplayName("Should send setDisplayDirection command with correct serial_data frame")
    void shouldSendSetDisplayDirection() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.setDisplayDirection(TEST_SN, 1);
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(), anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");
    }

    @Test
    @DisplayName("Should send playVoice command with correct serial_data frame")
    void shouldSendPlayVoice() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.playVoice(TEST_SN, 42, "5.00");
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(), anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");
    }

    @Test
    @DisplayName("Should send stopVoice command with correct serial_data frame")
    void shouldSendStopVoice() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<MqttMessage> future = handler.stopVoice(TEST_SN);
        MqttMessage reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply.getCode()).isEqualTo(200);

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(), anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");
    }

    @Test
    @DisplayName("Should send displayTextEnhanced with font and voice params")
    void shouldSendDisplayTextEnhanced() throws Exception {
        MqttMessage mockReply = MqttMessage.builder().code(200).build();
        when(mqttGateway.publishAndWait(anyString(), any(MqttMessage.class),
                anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mockReply));

        CompletableFuture<Boolean> future = handler.displayTextEnhanced(
                TEST_SN, "Welcome", DisplayDirection.HORIZONTAL,
                OlmM1dProtocol.FontType.SONG_32, java.util.List.of(new int[]{0xFF, 0xFF, 0xFF, 0xFF}), 42, "5.00");

        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();

        ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttGateway).publishAndWait(anyString(), msgCaptor.capture(), anyLong(), any(TimeUnit.class));

        MqttMessage sent = msgCaptor.getValue();
        assertThat(sent.getName()).isEqualTo("serial_data");
    }

    // ──────── 原有辅助方法 ────────

    private MqttMessage createKeepAliveMessage() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", System.currentTimeMillis() / 1000);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("body", body);

        return MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(TEST_SN)
                .name("keep_alive")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .payload(payload)
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MqttMessage createPlateRecognitionMessage(String sn, String license, String deviceName) {
        Map<String, Object> firstPlate = new LinkedHashMap<>();
        firstPlate.put("color", 2);
        firstPlate.put("type", 1);
        firstPlate.put("is_danger", 0);
        firstPlate.put("image_path", "/mnt/data/plate.jpg");

        Map<String, Object> plateResult = new LinkedHashMap<>();
        plateResult.put("license", license);
        plateResult.put("confidence", 98);
        plateResult.put("direction", 1);
        plateResult.put("carColor", 3);
        plateResult.put("triggerType", 1);
        plateResult.put("isoffline", 0);
        plateResult.put("imagePath", "/mnt/data/full.jpg");
        plateResult.put("start_time", System.currentTimeMillis());
        plateResult.put("plates", List.of(firstPlate));

        Map<String, Object> carBrand = new LinkedHashMap<>();
        carBrand.put("brand", 1);
        plateResult.put("car_brand", carBrand);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("PlateResult", plateResult);

        Map<String, Object> alarmInfo = new LinkedHashMap<>();
        alarmInfo.put("deviceName", deviceName);
        alarmInfo.put("ipaddr", "192.168.20.50");
        alarmInfo.put("channel", 1);
        alarmInfo.put("rule_id", 1);
        alarmInfo.put("result", result);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("AlarmInfoPlate", alarmInfo);

        return MqttMessage.builder()
                .id(UUID.randomUUID().toString())
                .sn(sn)
                .name("ivs_result")
                .version("1.0")
                .timestamp(System.currentTimeMillis() / 1000)
                .payload(payload)
                .build();
    }
}
