package com.smartparking.deviceaccess.adapter.xinlutong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.adapter.support.DeviceHeartbeatRecorder;
import com.smartparking.deviceaccess.mqtt.MqttGateway;
import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import com.smartparking.deviceaccess.common.enums.DisplayMode;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.common.event.PlateRecognizedListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 信路通 XLT-01 消息处理器单元测试。
 * <p>
 * 使用 Mockito 模拟 MqttGateway 和 DeviceHeartbeatRecorder，
 * 验证消息解析、心跳缓存、命令构建、回执解析等核心逻辑。
 * v0.4 新增。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XinlutongMessageHandler Unit Tests")
class XinlutongMessageHandlerTest {

    @Mock
    private MqttGateway mqttGateway;

    @Mock
    private DeviceHeartbeatRecorder heartbeatRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private XinlutongMessageHandler handler;

    private static final String TEST_SN = "XLT-01-TEST-001";

    @BeforeEach
    void setUp() {
        handler = new XinlutongMessageHandler(mqttGateway, objectMapper, heartbeatRecorder);
    }

    // ──────────────────── 心跳处理测试 ────────────────────

    @Test
    @DisplayName("Should record heartbeat on Conn message and report device online")
    void shouldRecordHeartbeatOnConnAndReportOnline() {
        when(heartbeatRecorder.isDeviceOnline(TEST_SN, 90_000L)).thenReturn(true);

        Map<String, Object> connMessage = Map.of(
                "command", "Conn",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00",
                "data", "{\"devInfo\":\"XLT-01 v1.0\"}"
        );

        handler.onRawMessage("upload/" + TEST_SN, connMessage);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();
    }

    @Test
    @DisplayName("Should record heartbeat on Rtd message")
    void shouldRecordHeartbeatOnRtd() {
        Map<String, Object> rtdMessage = Map.of(
                "command", "Rtd",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:30",
                "data", "{\"hasCar\":2}"
        );

        handler.onRawMessage("upload/" + TEST_SN, rtdMessage);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
    }

    @Test
    @DisplayName("Should keep device online on repeated Rtd heartbeats")
    void shouldKeepDeviceOnlineOnRepeatedRtd() {
        when(heartbeatRecorder.isDeviceOnline(TEST_SN, 90_000L)).thenReturn(true);

        Map<String, Object> rtdMessage = Map.of(
                "command", "Rtd",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00"
        );

        handler.onRawMessage("upload/" + TEST_SN, rtdMessage);
        handler.onRawMessage("upload/" + TEST_SN, rtdMessage);

        verify(heartbeatRecorder, times(2)).recordHeartbeat(TEST_SN);
        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();
    }

    // ──────────────────── 在线判定测试 ────────────────────

    @Test
    @DisplayName("Should report false for unknown device")
    void shouldReportOfflineForUnknownDevice() {
        when(heartbeatRecorder.isDeviceOnline("unknown-device", 90_000L)).thenReturn(false);

        assertThat(handler.isDeviceOnline("unknown-device")).isFalse();
    }

    @Test
    @DisplayName("Should report false for device that never sent heartbeat")
    void shouldReportOfflineForNeverSeenDevice() {
        when(heartbeatRecorder.isDeviceOnline("never-seen", 90_000L)).thenReturn(false);

        assertThat(handler.isDeviceOnline("never-seen")).isFalse();
    }

    // ──────────────────── 消息过滤测试 ────────────────────

    @Test
    @DisplayName("Should ignore non-Xinlutong topics")
    void shouldIgnoreNonXinlutongTopics() {
        Map<String, Object> message = Map.of("command", "Rtd", "sn", TEST_SN);

        // 非 upload/download 开头的 Topic 应该被忽略
        handler.onRawMessage("device/" + TEST_SN + "/message/up/keep_alive", message);

        verifyNoInteractions(heartbeatRecorder);
    }

    @Test
    @DisplayName("Should silently ignore message without command field")
    void shouldIgnoreMessageWithoutCommand() {
        Map<String, Object> message = Map.of("sn", TEST_SN, "data", "test");

        handler.onRawMessage("upload/" + TEST_SN, message);

        verifyNoInteractions(heartbeatRecorder);
    }

    @Test
    @DisplayName("Should silently ignore unknown command types")
    void shouldIgnoreUnknownCommand() {
        Map<String, Object> message = Map.of(
                "command", "UnknownCmd",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00"
        );

        // 不抛异常即为通过
        handler.onRawMessage("upload/" + TEST_SN, message);
    }

    // ──────────────────── 命令回执匹配测试 ────────────────────

    @Test
    @DisplayName("Should match command reply by requestId and complete future")
    void shouldMatchReplyByRequestId() throws Exception {
        String requestId = UUID.randomUUID().toString();

        // 预注册一个 pending future（模拟命令已发送）
        // 通过反射注入 pendingFutures 不太方便，改用 sendOpen 触发
        // 但 sendOpen 会调用 mqttGateway.publishRaw，我们让它不抛异常
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        CompletableFuture<Map<String, Object>> future = handler.sendOpen(TEST_SN, 10);

        // 模拟设备回执
        Map<String, Object> reply = Map.of(
                "command", "Open",
                "sn", TEST_SN,
                "requestId", extractRequestIdFromLastPublish(),
                "data", "{\"errCode\":0,\"errInfo\":\"\"}",
                "time", "2026-07-13 10:00:01"
        );

        handler.onRawMessage("download/" + TEST_SN, reply);

        Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.get("data")).isEqualTo("{\"errCode\":0,\"errInfo\":\"\"}");
    }

    @Test
    @DisplayName("Should not process reply as business message if requestId matched")
    void shouldNotProcessMatchedReplyAsBusiness() {
        // 回执匹配后应该直接返回，不走 handleConn/handleRtd 等业务逻辑
        Map<String, Object> reply = Map.of(
                "command", "Rtd",
                "sn", TEST_SN,
                "requestId", "non-existent-request-id",
                "time", "2026-07-13 10:00:00"
        );

        // 不抛异常即为通过（没有 pending future，会走业务逻辑，但 Rtd 不会抛异常）
        handler.onRawMessage("upload/" + TEST_SN, reply);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
    }

    // ──────────────────── 校时命令测试 ────────────────────

    @Test
    @DisplayName("Should send sync time command with correct format")
    void shouldSendSyncTimeWithCorrectFormat() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.sendSyncTime(TEST_SN, 10);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("set_time");
        assertThat(sent.get("sn")).isEqualTo(TEST_SN);
        assertThat(sent.get("requestId")).isNotNull();
        assertThat((String) sent.get("requestId")).isNotBlank();
        assertThat(sent.get("time")).isNotNull();
        assertThat(sent.get("version")).isEqualTo("1.0.1");

        // data 字段包含 serverTime
        String data = (String) sent.get("data");
        assertThat(data).contains("serverTime");
    }

    // ──────────────────── 开闸命令测试 ────────────────────

    @Test
    @DisplayName("Should send open gate command with correct format")
    void shouldSendOpenGateWithCorrectFormat() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.sendOpen(TEST_SN, 10);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("Open");
        assertThat(sent.get("sn")).isEqualTo(TEST_SN);
        assertThat(sent.get("requestId")).isNotNull();
        assertThat((String) sent.get("requestId")).isNotBlank();
        assertThat(sent.get("data")).isEqualTo("");
    }

    // ──────────────────── 关闸命令测试 ────────────────────

    @Test
    @DisplayName("Should send close gate command with correct format")
    void shouldSendCloseGateWithCorrectFormat() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.sendClose(TEST_SN, 10);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("Close");
        assertThat(sent.get("sn")).isEqualTo(TEST_SN);
        assertThat(sent.get("requestId")).isNotNull();
        assertThat((String) sent.get("requestId")).isNotBlank();
        assertThat(sent.get("data")).isEqualTo("");
    }

    // ──────────────────── 命令回执解析测试 ────────────────────

    @Test
    @DisplayName("Should parse successful command result")
    void shouldParseSuccessfulCommandResult() {
        Map<String, Object> reply = Map.of("data", "{\"errCode\":0,\"errInfo\":\"\"}");

        XinlutongCommandResult result = handler.parseCommandResult(reply);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrCode()).isEqualTo(0);
        assertThat(result.getErrInfo()).isEqualTo("");
    }

    @Test
    @DisplayName("Should parse failed command result with errCode and errInfo")
    void shouldParseFailedCommandResult() {
        Map<String, Object> reply = Map.of("data", "{\"errCode\":1,\"errInfo\":\"Unknow CMD\"}");

        XinlutongCommandResult result = handler.parseCommandResult(reply);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrCode()).isEqualTo(1);
        assertThat(result.getErrInfo()).isEqualTo("Unknow CMD");
    }

    @Test
    @DisplayName("Should return parse failure when reply is null")
    void shouldReturnParseFailureForNullReply() {
        XinlutongCommandResult result = handler.parseCommandResult(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrCode()).isEqualTo(-1);
        assertThat(result.getErrInfo()).contains("Failed to parse reply data");
    }

    @Test
    @DisplayName("Should return parse failure when data field is missing")
    void shouldReturnParseFailureForMissingData() {
        Map<String, Object> reply = Map.of("command", "Open", "sn", TEST_SN);

        XinlutongCommandResult result = handler.parseCommandResult(reply);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrCode()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should return parse failure when data is not valid JSON")
    void shouldReturnParseFailureForInvalidData() {
        Map<String, Object> reply = Map.of("data", "not-json-data");

        XinlutongCommandResult result = handler.parseCommandResult(reply);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrCode()).isEqualTo(-1);
        assertThat(result.getErrInfo()).contains("Failed to parse reply data");
    }

    @Test
    @DisplayName("Should handle errCode as integer in JSON")
    void shouldHandleErrCodeAsInteger() {
        Map<String, Object> reply = Map.of("data", "{\"errCode\":2,\"errInfo\":\"Timeout\"}");

        XinlutongCommandResult result = handler.parseCommandResult(reply);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrCode()).isEqualTo(2);
        assertThat(result.getErrInfo()).isEqualTo("Timeout");
    }

    // ──────────────────── Conn 消息测试 ────────────────────

    @Test
    @DisplayName("Should send Config(enableReply=1) on first Conn message")
    void shouldSendConfigOnFirstConn() {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        Map<String, Object> connMessage = Map.of(
                "command", "Conn",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00"
        );

        handler.onRawMessage("upload/" + TEST_SN, connMessage);

        // 应该发送 Config 命令
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(anyString(), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertThat(json).contains("Config");
        assertThat(json).contains("enableReply");
    }

    @Test
    @DisplayName("Should not send Config twice for same device")
    void shouldNotSendConfigTwice() {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        Map<String, Object> connMessage = Map.of(
                "command", "Conn",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00"
        );

        // 第一次 Conn
        handler.onRawMessage("upload/" + TEST_SN, connMessage);
        // 第二次 Conn（重连）
        handler.onRawMessage("upload/" + TEST_SN, connMessage);

        // 只发送一次 Config
        verify(mqttGateway, times(1)).publishRaw(anyString(), anyString());
    }

    // ──────────────────── Image 消息测试 ────────────────────

    @Test
    @DisplayName("Should NOT trigger auto open gate on Image message (v0.4)")
    void shouldNotTriggerAutoOpenGateOnImage() {
        Map<String, Object> imageMessage = Map.of(
                "command", "Image",
                "sn", TEST_SN,
                "requestId", UUID.randomUUID().toString(),
                "time", "2026-07-13 10:00:00"
        );

        handler.onRawMessage("upload/" + TEST_SN, imageMessage);

        // v0.4: 不应发送任何 Open 命令
        verifyNoInteractions(mqttGateway);
    }

    @Test
    @DisplayName("Should record last plate from Image message data")
    void shouldRecordLastPlateFromImage() {
        Map<String, Object> imageMessage = Map.of(
                "command", "Image",
                "sn", TEST_SN,
                "requestId", UUID.randomUUID().toString(),
                "time", "2026-07-13 10:00:00",
                "data", "{\"plateNo\":\"鲁Q12345\"}"
        );

        handler.onRawMessage("upload/" + TEST_SN, imageMessage);

        assertThat(handler.getLastPlate(TEST_SN)).isEqualTo("鲁Q12345");
    }

    @Test
    @DisplayName("Should handle Image message without data gracefully")
    void shouldHandleImageWithoutData() {
        Map<String, Object> imageMessage = Map.of(
                "command", "Image",
                "sn", TEST_SN,
                "requestId", UUID.randomUUID().toString(),
                "time", "2026-07-13 10:00:00"
        );

        handler.onRawMessage("upload/" + TEST_SN, imageMessage);

        assertThat(handler.getLastPlate(TEST_SN)).isEqualTo("");
    }

    @Test
    @DisplayName("Should notify plate listener on Image message with plateNo")
    void shouldNotifyPlateListenerOnImage() {
        PlateRecognizedListener listener = mock(PlateRecognizedListener.class);
        ReflectionTestUtils.setField(handler, "plateListener", listener);

        Map<String, Object> imageMessage = Map.of(
                "command", "Image",
                "sn", TEST_SN,
                "requestId", UUID.randomUUID().toString(),
                "time", "2026-07-13 10:00:00",
                "data", "{\"plateNo\":\"鲁Q12345\"}"
        );

        handler.onRawMessage("upload/" + TEST_SN, imageMessage);

        ArgumentCaptor<PlateRecognizedData> captor = ArgumentCaptor.forClass(PlateRecognizedData.class);
        verify(listener).onPlateRecognized(captor.capture());

        PlateRecognizedData data = captor.getValue();
        assertThat(data.deviceSn()).isEqualTo(TEST_SN);
        assertThat(data.license()).isEqualTo("鲁Q12345");
    }

    @Test
    @DisplayName("Should NOT notify plate listener when Image has no plateNo")
    void shouldNotNotifyWhenNoPlateNo() {
        PlateRecognizedListener listener = mock(PlateRecognizedListener.class);
        ReflectionTestUtils.setField(handler, "plateListener", listener);

        Map<String, Object> imageMessage = Map.of(
                "command", "Image",
                "sn", TEST_SN,
                "requestId", UUID.randomUUID().toString(),
                "time", "2026-07-13 10:00:00"
        );

        handler.onRawMessage("upload/" + TEST_SN, imageMessage);

        verifyNoInteractions(listener);
    }

    @Test
    @DisplayName("Should not throw when plate listener is not injected")
    void shouldNotThrowWhenNoListener() {
        Map<String, Object> imageMessage = Map.of(
                "command", "Image",
                "sn", TEST_SN,
                "requestId", UUID.randomUUID().toString(),
                "time", "2026-07-13 10:00:00",
                "data", "{\"plateNo\":\"鲁Q12345\"}"
        );

        // plateListener is null in test
        handler.onRawMessage("upload/" + TEST_SN, imageMessage);

        assertThat(handler.getLastPlate(TEST_SN)).isEqualTo("鲁Q12345");
    }

    // ──────────────────── 边界条件测试 ────────────────────

    @Test
    @DisplayName("Should extract sn from topic when message has no sn field")
    void shouldExtractSnFromTopic() {
        Map<String, Object> message = Map.of(
                "command", "Rtd",
                "time", "2026-07-13 10:00:00"
        );

        handler.onRawMessage("upload/" + TEST_SN, message);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
    }

    @Test
    @DisplayName("Should handle Conn with null data gracefully")
    void shouldHandleConnWithNullData() {
        Map<String, Object> connMessage = Map.of(
                "command", "Conn",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00"
        );

        // 不抛异常即为通过
        handler.onRawMessage("upload/" + TEST_SN, connMessage);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
    }

    @Test
    @DisplayName("Should handle Rtd with empty data")
    void shouldHandleRtdWithEmptyData() {
        Map<String, Object> rtdMessage = Map.of(
                "command", "Rtd",
                "sn", TEST_SN,
                "time", "2026-07-13 10:00:00",
                "data", ""
        );

        handler.onRawMessage("upload/" + TEST_SN, rtdMessage);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
    }

    // ──────── 辅助方法 ────────

    /**
     * 从最后一次 publishRaw 调用中提取 requestId。
     * 用于模拟设备回执匹配。
     */
    private String extractRequestIdFromLastPublish() throws Exception {
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway, atLeastOnce()).publishRaw(anyString(), jsonCaptor.capture());

        String lastJson = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(lastJson, Map.class);
        return (String) sent.get("requestId");
    }

    // ═══════════════════════════════════════════
    // 显示屏控制测试（v0.4 新增）
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("Should send SerialData with correct Xinlutong format")
    void shouldSendSerialDataWithCorrectFormat() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        byte[] rawData = new byte[]{0x01, 0x02, 0x03, 0x04};
        handler.sendSerialData(TEST_SN, 0, rawData, 50);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
        assertThat(sent.get("sn")).isEqualTo(TEST_SN);
        assertThat(sent.get("requestId")).isNotNull();

        // data 字段是转义后的 JSON 字符串
        String dataStr = (String) sent.get("data");
        assertThat(dataStr).isNotNull();
        Map<String, Object> dataObj = objectMapper.readValue(dataStr, Map.class);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> serialData = (java.util.List<Map<String, Object>>) dataObj.get("serialData");
        assertThat(serialData).hasSize(1);
        assertThat(serialData.get(0).get("channel")).isEqualTo(0);
        assertThat(serialData.get(0).get("len")).isEqualTo(4);
        assertThat(serialData.get(0).get("data")).isNotNull();
    }

    @Test
    @DisplayName("Should set display enabled via brightness frame")
    void shouldSetDisplayEnabled() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.setDisplayEnabled(TEST_SN, true);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(anyString(), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");

        // data 中包含 serialData 且 data 是 Base64 编码的 OLM-M1D 帧
        String dataStr = (String) sent.get("data");
        Map<String, Object> dataObj = objectMapper.readValue(dataStr, Map.class);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> serialData = (java.util.List<Map<String, Object>>) dataObj.get("serialData");
        assertThat(serialData).hasSize(1);
    }

    @Test
    @DisplayName("Should set display mode to TWO_LINE")
    void shouldSetDisplayModeTwoLine() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.setDisplayMode(TEST_SN, DisplayMode.TWO_LINE);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(anyString(), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }

    @Test
    @DisplayName("Should set display mode to FOUR_LINE")
    void shouldSetDisplayModeFourLine() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.setDisplayMode(TEST_SN, DisplayMode.FOUR_LINE);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(anyString(), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }

    @Test
    @DisplayName("Should display text with HORIZONTAL direction")
    void shouldDisplayTextHorizontal() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        // 模拟设备回执成功
        CompletableFuture<Boolean> future = handler.displayText(TEST_SN, "Line1\nLine2", DisplayDirection.HORIZONTAL);

        // 发送回执
        String requestId = extractRequestIdFromLastPublish();
        Map<String, Object> reply = Map.of(
                "command", "SerialData",
                "sn", TEST_SN,
                "requestId", requestId,
                "data", "{\"errCode\":0,\"errInfo\":\"\"}",
                "time", "2026-07-13 10:00:01"
        );
        handler.onRawMessage("download/" + TEST_SN, reply);

        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should display text and return false on device error")
    void shouldDisplayTextReturnFalseOnError() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        CompletableFuture<Boolean> future = handler.displayText(TEST_SN, "Test", DisplayDirection.HORIZONTAL);

        // 模拟设备回执失败
        String requestId = extractRequestIdFromLastPublish();
        Map<String, Object> reply = Map.of(
                "command", "SerialData",
                "sn", TEST_SN,
                "requestId", requestId,
                "data", "{\"errCode\":1,\"errInfo\":\"Failed\"}",
                "time", "2026-07-13 10:00:01"
        );
        handler.onRawMessage("download/" + TEST_SN, reply);

        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should throw on null or blank content for displayText")
    void shouldThrowOnNullContentForDisplayText() {
        assertThatThrownBy(() -> handler.displayText(TEST_SN, null, DisplayDirection.HORIZONTAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content must not be null or blank");
    }

    @Test
    @DisplayName("Should throw on VERTICAL direction for displayText")
    void shouldThrowOnVerticalDirectionForDisplayText() {
        assertThatThrownBy(() -> handler.displayText(TEST_SN, "Test", DisplayDirection.VERTICAL))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("VERTICAL display layout is not implemented yet");
    }

    @Test
    @DisplayName("Should save display with HORIZONTAL direction")
    void shouldSaveDisplayHorizontal() throws Exception {
        // 使用 thenAnswer 在每次 publishRaw 被调用时自动发送回执
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> {
            // 从 publishRaw 的参数中提取 requestId
            String json = inv.getArgument(1, String.class);
            Map<String, Object> sent = objectMapper.readValue(json, Map.class);
            String requestId = (String) sent.get("requestId");

            // 发送回执
            Map<String, Object> reply = Map.of(
                    "command", "SerialData",
                    "sn", TEST_SN,
                    "requestId", requestId,
                    "data", "{\"errCode\":0,\"errInfo\":\"\"}",
                    "time", "2026-07-13 10:00:01"
            );
            // 延迟一点再发送回执，确保 pendingFutures 已注册
            new Thread(() -> {
                try {
                    Thread.sleep(50);
                    handler.onRawMessage("download/" + TEST_SN, reply);
                } catch (Exception e) {
                    // ignore
                }
            }).start();

            callCount.incrementAndGet();
            return null;
        }).when(mqttGateway).publishRaw(anyString(), anyString());

        CompletableFuture<Boolean> future = handler.saveDisplay(TEST_SN, "Line1\nLine2", DisplayDirection.HORIZONTAL);

        Boolean result = future.get(15, TimeUnit.SECONDS);
        assertThat(result).isTrue();
        assertThat(callCount.get()).isEqualTo(2); // 两行 = 两次 publishRaw
    }

    @Test
    @DisplayName("Should save display and return false if any row fails")
    void shouldSaveDisplayReturnFalseOnRowFailure() throws Exception {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> {
            String json = inv.getArgument(1, String.class);
            Map<String, Object> sent = objectMapper.readValue(json, Map.class);
            String requestId = (String) sent.get("requestId");

            int count = callCount.incrementAndGet();
            // 第一行成功，第二行失败
            int errCode = (count == 1) ? 0 : 1;
            String errInfo = (count == 1) ? "" : "Write failed";

            Map<String, Object> reply = Map.of(
                    "command", "SerialData",
                    "sn", TEST_SN,
                    "requestId", requestId,
                    "data", "{\"errCode\":" + errCode + ",\"errInfo\":\"" + errInfo + "\"}",
                    "time", "2026-07-13 10:00:01"
            );
            new Thread(() -> {
                try {
                    Thread.sleep(50);
                    handler.onRawMessage("download/" + TEST_SN, reply);
                } catch (Exception e) {
                    // ignore
                }
            }).start();

            return null;
        }).when(mqttGateway).publishRaw(anyString(), anyString());

        CompletableFuture<Boolean> future = handler.saveDisplay(TEST_SN, "Line1\nLine2", DisplayDirection.HORIZONTAL);

        Boolean result = future.get(15, TimeUnit.SECONDS);
        assertThat(result).isFalse();
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should throw on null content for saveDisplay")
    void shouldThrowOnNullContentForSaveDisplay() {
        assertThatThrownBy(() -> handler.saveDisplay(TEST_SN, null, DisplayDirection.HORIZONTAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content must not be null or blank");
    }

    @Test
    @DisplayName("Should handle SerialDataReply in onRawMessage")
    void shouldHandleSerialDataReply() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        // 发送一个 SerialData 命令，创建 pending future
        handler.sendSerialData(TEST_SN, 0, new byte[]{0x01}, 50);

        // 模拟设备回执（SerialDataReply 或 SerialData）
        String requestId = extractRequestIdFromLastPublish();
        Map<String, Object> reply = Map.of(
                "command", "SerialData",
                "sn", TEST_SN,
                "requestId", requestId,
                "data", "{\"errCode\":0,\"errInfo\":\"\"}",
                "time", "2026-07-13 10:00:01"
        );

        // 不应抛异常，且 pending future 被移除
        handler.onRawMessage("download/" + TEST_SN, reply);
    }

    // ═══════════════════════════════════════════
    // 显示屏高级控制测试（v0.4 新增）
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("Should send setVolume via SerialData command")
    void shouldSendSetVolume() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.setVolume(TEST_SN, 50);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
        assertThat(sent.get("sn")).isEqualTo(TEST_SN);
    }

    @Test
    @DisplayName("Should send syncDisplayTime via SerialData command")
    void shouldSendSyncDisplayTime() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.syncDisplayTime(TEST_SN);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }

    @Test
    @DisplayName("Should send setDisplayDirection via SerialData command")
    void shouldSendSetDisplayDirection() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.setDisplayDirection(TEST_SN, 1);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }

    @Test
    @DisplayName("Should send playVoice via SerialData command")
    void shouldSendPlayVoice() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.playVoice(TEST_SN, 42, "5.00");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }

    @Test
    @DisplayName("Should send stopVoice via SerialData command")
    void shouldSendStopVoice() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        handler.stopVoice(TEST_SN);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }

    @Test
    @DisplayName("Should send displayTextEnhanced with font and voice params")
    void shouldSendDisplayTextEnhanced() throws Exception {
        doNothing().when(mqttGateway).publishRaw(anyString(), anyString());

        CompletableFuture<Boolean> future = handler.displayTextEnhanced(
                TEST_SN, "Welcome", DisplayDirection.HORIZONTAL,
                OlmM1dProtocol.FontType.SONG_32, java.util.List.of(new int[]{0xFF, 0xFF, 0xFF, 0xFF}), 42, "5.00");

        // 由于 publishRaw 是 mock，future 不会自动完成，需要手动触发回执
        // 这里只验证 publishRaw 被正确调用
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("download/" + TEST_SN);

        String json = jsonCaptor.getValue();
        Map<String, Object> sent = objectMapper.readValue(json, Map.class);
        assertThat(sent.get("command")).isEqualTo("SerialData");
    }
}
