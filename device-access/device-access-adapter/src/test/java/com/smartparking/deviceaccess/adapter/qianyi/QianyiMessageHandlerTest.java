package com.smartparking.deviceaccess.adapter.qianyi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.adapter.support.DeviceHeartbeatRecorder;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.common.event.PlateRecognizedListener;
import com.smartparking.deviceaccess.mqtt.MqttGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 芊熠消息处理器单元测试。
 * <p>
 * 使用 Mockito 模拟 MqttGateway 和 DeviceHeartbeatRecorder，
 * 验证注册/心跳/识别消息解析、命令构建、应答关联等核心逻辑。
 * v0.5 新增。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QianyiMessageHandler Unit Tests")
class QianyiMessageHandlerTest {

    @Mock
    private MqttGateway mqttGateway;

    @Mock
    private DeviceHeartbeatRecorder heartbeatRecorder;

    @Mock
    private PlateRecognizedListener plateListener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private QianyiMessageHandler handler;

    private static final String TEST_SN = "1b7e812009cad088";
    private static final String TEST_SUBTOPIC = "device/plate/" + TEST_SN;
    private static final String TEST_PUBTOPIC = "aiot/plate/" + TEST_SN;

    @BeforeEach
    void setUp() {
        handler = new QianyiMessageHandler(mqttGateway, objectMapper, heartbeatRecorder);
        ReflectionTestUtils.setField(handler, "plateListener", plateListener);
    }

    // ──────────────────── 注册处理测试 ────────────────────

    @Test
    @DisplayName("Should cache topics, reply register_rsp and record heartbeat on camera_register")
    void shouldHandleCameraRegister() throws Exception {
        Map<String, Object> register = new HashMap<>();
        register.put("cmd", "camera_register");
        register.put("msg_id", "1684361017822x5fvIq0");
        register.put("sn", TEST_SN);
        register.put("subtopic", TEST_SUBTOPIC);
        register.put("pubtopic", TEST_PUBTOPIC);
        register.put("model", "S8_2");

        handler.onRawMessage("/serverAll", register);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);

        // 默认 pubtopic（aiot/plate/ 前缀）已被通配订阅覆盖，不应再动态订阅
        verify(mqttGateway, never()).subscribe(anyString());

        // 应向 subtopic 回 camera_register_rsp
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(TEST_SUBTOPIC);
        Map<String, Object> rsp = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(rsp.get("cmd")).isEqualTo("camera_register_rsp");
        assertThat(rsp.get("status")).isEqualTo("ok");
        assertThat(rsp.get("msg_id")).isEqualTo("1684361017822x5fvIq0");
    }

    @Test
    @DisplayName("Should dynamically subscribe non-default pubtopic on camera_register")
    void shouldSubscribeNonDefaultPubtopic() {
        String customPub = "/cadevice/" + TEST_SN + "/sub";
        Map<String, Object> register = new HashMap<>();
        register.put("cmd", "camera_register");
        register.put("msg_id", "1684361017822x5fvIq0");
        register.put("sn", TEST_SN);
        register.put("subtopic", "/cadevice/" + TEST_SN + "/pub");
        register.put("pubtopic", customPub);

        handler.onRawMessage("/serverAll", register);

        verify(mqttGateway).subscribe(customPub);
    }

    // ──────────────────── 心跳处理测试 ────────────────────

    @Test
    @DisplayName("Should record heartbeat on mqtt_herat message")
    void shouldRecordHeartbeatOnMqttHerat() {
        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("cmd", "mqtt_herat");
        heartbeat.put("msg_id", "1642056493874N7EKuR4");
        heartbeat.put("sn", TEST_SN);

        handler.onRawMessage(TEST_PUBTOPIC, heartbeat);

        verify(heartbeatRecorder).recordHeartbeat(TEST_SN);
    }

    @Test
    @DisplayName("Should report device online via heartbeat recorder")
    void shouldReportOnlineViaRecorder() {
        when(heartbeatRecorder.isDeviceOnline(TEST_SN, 90_000L)).thenReturn(true);

        assertThat(handler.isDeviceOnline(TEST_SN)).isTrue();
    }

    // ──────────────────── 识别事件测试 ────────────────────

    @Test
    @DisplayName("Should reply result_rsp and notify plate listener on result message")
    void shouldHandleResultMessage() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("cmd", "result");
        result.put("msg_id", "1562566753001402b681");
        result.put("type", "online");
        result.put("plate_num", "京A12345");
        result.put("plate_color", "蓝色");
        result.put("confidence", 28);
        result.put("utc_ts", 1562566751);
        result.put("inout", "in");
        result.put("trigger_type", "video");
        result.put("full_pic_path", "/picture/A000001/test.jpg");
        result.put("sn", TEST_SN);

        handler.onRawMessage(TEST_PUBTOPIC, result);

        // 应回 result_rsp
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(TEST_SUBTOPIC); // 未注册 → 默认模板
        Map<String, Object> rsp = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(rsp.get("cmd")).isEqualTo("result_rsp");
        assertThat(rsp.get("status")).isEqualTo("ok");
        assertThat(rsp.get("msg_id")).isEqualTo("1562566753001402b681");

        // 应回调识别事件
        ArgumentCaptor<PlateRecognizedData> dataCaptor = ArgumentCaptor.forClass(PlateRecognizedData.class);
        verify(plateListener).onPlateRecognized(dataCaptor.capture());
        PlateRecognizedData data = dataCaptor.getValue();
        assertThat(data.deviceSn()).isEqualTo(TEST_SN);
        assertThat(data.license()).isEqualTo("京A12345");
        assertThat(data.confidence()).isEqualTo(28);
        assertThat(data.imagePath()).isEqualTo("/picture/A000001/test.jpg");
        assertThat(data.occurredAtMillis()).isEqualTo(1562566751_000L);

        assertThat(handler.getLastPlate(TEST_SN)).isEqualTo("京A12345");
    }

    @Test
    @DisplayName("Should treat plate_num 'null' string as no-plate and skip listener")
    void shouldSkipListenerForNoPlate() {
        Map<String, Object> result = new HashMap<>();
        result.put("cmd", "result");
        result.put("msg_id", "1562566753001402b681");
        result.put("type", "online");
        result.put("plate_num", "null");  // 协议：无牌车为字符串 "null"
        result.put("utc_ts", 1562566751);
        result.put("sn", TEST_SN);

        handler.onRawMessage(TEST_PUBTOPIC, result);

        verify(plateListener, never()).onPlateRecognized(any());
        // result_rsp 仍应回复
        verify(mqttGateway).publishRaw(anyString(), contains("result_rsp"));
    }

    @Test
    @DisplayName("Should process offline retransmission result the same as online")
    void shouldProcessOfflineTypeResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("cmd", "result");
        result.put("msg_id", "1562566753001402b681");
        result.put("type", "offline");  // 断网续传
        result.put("plate_num", "粤B12345");
        result.put("sn", TEST_SN);

        handler.onRawMessage(TEST_PUBTOPIC, result);

        verify(plateListener).onPlateRecognized(argThat(d -> "粤B12345".equals(d.license())));
    }

    // ──────────────────── 消息过滤测试 ────────────────────

    @Test
    @DisplayName("Should ignore non-Qianyi topics")
    void shouldIgnoreNonQianyiTopics() {
        Map<String, Object> message = Map.of("cmd", "mqtt_herat", "sn", TEST_SN);

        handler.onRawMessage("device/" + TEST_SN + "/message/up/keep_alive", message);
        handler.onRawMessage("upload/" + TEST_SN, message);

        verifyNoInteractions(heartbeatRecorder);
    }

    @Test
    @DisplayName("Should ignore message without cmd field")
    void shouldIgnoreMessageWithoutCmd() {
        Map<String, Object> message = Map.of("sn", TEST_SN, "msg_id", "abc");

        handler.onRawMessage(TEST_PUBTOPIC, message);

        verifyNoInteractions(heartbeatRecorder);
    }

    // ──────────────────── 下行命令与应答关联测试 ────────────────────

    @Test
    @DisplayName("Should build open gate iooutput command and correlate reply by msg_id")
    void shouldSendOpenGateAndCorrelateReply() throws Exception {
        CompletableFuture<Map<String, Object>> future = handler.sendOpenGate(TEST_SN, 10);

        // 校验下行 JSON 字段
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(topicCaptor.capture(), jsonCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(TEST_SUBTOPIC); // 未注册 → 默认模板
        Map<String, Object> cmd = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(cmd.get("cmd")).isEqualTo("iooutput");
        assertThat(cmd.get("ionum")).isEqualTo(0);
        assertThat(cmd.get("action")).isEqualTo("on");
        assertThat(cmd.get("utc_ts")).isNotNull();
        String msgId = (String) cmd.get("msg_id");
        assertThat(msgId).hasSize(20);

        assertThat(future).isNotDone();

        // 模拟设备应答（原样回传 msg_id）
        Map<String, Object> reply = new HashMap<>();
        reply.put("cmd", "iooutput_rsp");
        reply.put("status", "ok");
        reply.put("msg_id", msgId);
        reply.put("type", "on");
        reply.put("sn", TEST_SN);
        handler.onRawMessage(TEST_PUBTOPIC, reply);

        Map<String, Object> replyMap = future.get(1, TimeUnit.SECONDS);
        QianyiCommandResult result = handler.parseCommandResult(replyMap);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should build barrierKeepOpen command with isKeepOpen field")
    void shouldSendBarrierKeepOpen() throws Exception {
        handler.sendBarrierKeepOpen(TEST_SN, 1, 10);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(anyString(), jsonCaptor.capture());
        Map<String, Object> cmd = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(cmd.get("cmd")).isEqualTo("barrierKeepOpen");
        assertThat(cmd.get("isKeepOpen")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should build syncSysTime command with time_zone=4 and string timestamp")
    void shouldSendSyncSysTime() throws Exception {
        handler.sendSyncSysTime(TEST_SN, 10);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).publishRaw(anyString(), jsonCaptor.capture());
        Map<String, Object> cmd = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(cmd.get("cmd")).isEqualTo("syncSysTime");
        assertThat(cmd.get("time_zone")).isEqualTo(4);
        assertThat(cmd.get("time_stamp")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("Should use registered subtopic for downlink after camera_register")
    void shouldUseRegisteredSubtopic() throws Exception {
        // 先注册（自定义 subtopic）
        Map<String, Object> register = new HashMap<>();
        register.put("cmd", "camera_register");
        register.put("msg_id", "1684361017822x5fvIq0");
        register.put("sn", TEST_SN);
        register.put("subtopic", "/cadevice/" + TEST_SN + "/pub");
        register.put("pubtopic", TEST_PUBTOPIC);
        handler.onRawMessage("/serverAll", register);

        handler.sendCloseGate(TEST_SN, 10);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway, atLeastOnce()).publishRaw(topicCaptor.capture(), anyString());
        assertThat(topicCaptor.getAllValues()).contains("/cadevice/" + TEST_SN + "/pub");
    }

    // ──────────────────── 应答解析测试 ────────────────────

    @Test
    @DisplayName("Should parse status ok as success (case-insensitive)")
    void shouldParseOkStatus() {
        assertThat(handler.parseCommandResult(Map.of("status", "ok")).isSuccess()).isTrue();
        assertThat(handler.parseCommandResult(Map.of("status", "OK")).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should parse non-ok status as failure with status text")
    void shouldParseNonOkStatus() {
        QianyiCommandResult result = handler.parseCommandResult(Map.of("status", "unfound"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("unfound");
    }

    @Test
    @DisplayName("Should parse null reply and missing status as failure")
    void shouldParseInvalidReply() {
        assertThat(handler.parseCommandResult(null).isSuccess()).isFalse();
        assertThat(handler.parseCommandResult(Map.of("cmd", "iooutput_rsp")).isSuccess()).isFalse();
    }
}
