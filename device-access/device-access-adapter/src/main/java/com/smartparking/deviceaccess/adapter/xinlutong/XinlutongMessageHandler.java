package com.smartparking.deviceaccess.adapter.xinlutong;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.adapter.support.DeviceHeartbeatRecorder;
import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import com.smartparking.deviceaccess.common.enums.DisplayMode;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.common.event.PlateRecognizedListener;
import com.smartparking.deviceaccess.mqtt.MqttGateway;
import com.smartparking.deviceaccess.mqtt.MqttRawMessageListener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 信路通（XLT-01）MQTT 消息处理器。
 * <p>
 * 真机联调验证的协议格式（与协议文档 V10 的差异）：
 * <ul>
 *   <li>命令字段名为 {@code command}（非 {@code cmd}）</li>
 *   <li>消息 ID 字段名为 {@code requestId}（非 {@code msgId}）</li>
 *   <li>设备序列号在消息体 {@code sn} 字段中（非仅从 Topic 提取）</li>
 * </ul>
 * <p>
 * v0.4 新增。参考 {@link com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler} 结构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XinlutongMessageHandler implements MqttRawMessageListener {

    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final DeviceHeartbeatRecorder heartbeatRecorder;

    /** 车牌识别事件回调（可选注入，由 api 模块 PlateRecognizedEventDispatcher 实现） */
    @Autowired(required = false)
    private PlateRecognizedListener plateListener;

    /** 已发送 Config(enableReply) 的设备集合（幂等控制） */
    private final Set<String> replyEnabledDevices = ConcurrentHashMap.newKeySet();

    /** 等待设备回执的 Future，key = requestId */
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingFutures = new ConcurrentHashMap<>();

    /** 最近识别的车牌号，key = sn */
    private final Map<String, String> lastPlateMap = new ConcurrentHashMap<>();

    // ──────────────────── 消息名常量 ────────────────────

    private static final String CMD_CONN = "Conn";
    private static final String CMD_RTD = "Rtd";
    private static final String CMD_IMAGE = "Image";

    // ──────────────────── Topic 模板 ────────────────────

    private static final String TOPIC_DOWNLINK = "download/%s";

    // ──────────────────── 时间格式 ────────────────────

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ──────────────────── 生命周期 ────────────────────

    @PostConstruct
    public void init() {
        mqttGateway.addRawListener(this);
        log.info("XinlutongMessageHandler registered as MQTT raw listener");
    }

    // ──────────────────── 消息入口 ────────────────────

    @Override
    public void onRawMessage(String topic, Map<String, Object> rawJson) {
        // 仅处理信路通 Topic
        if (!topic.startsWith("upload/") && !topic.startsWith("download/")) {
            return;
        }

        String command = (String) rawJson.get("command");
        String sn = (String) rawJson.get("sn");
        if (sn == null) {
            sn = extractSnFromTopic(topic);
        }

        if (command == null) {
            log.warn("Xinlutong message without 'command' field. Topic: {}, keys: {}", topic, rawJson.keySet());
            return;
        }
        if (sn == null) {
            log.warn("Xinlutong message without 'sn'. Topic: {}, command: {}", topic, command);
            return;
        }

        try {
            // 先检查是否为命令回执（设备回显原 command 名 + 原 requestId）
            // 必须在业务 switch 之前检查，否则 Rtd/Config 回执会被业务 handler 消费掉
            String requestId = (String) rawJson.get("requestId");
            if (requestId != null) {
                CompletableFuture<Map<String, Object>> future = pendingFutures.remove(requestId);
                if (future != null) {
                    log.info("Xinlutong reply matched: command={}, sn={}, requestId={}", command, sn, requestId);
                    future.complete(rawJson);
                    return;  // 已作为回执处理，不再走业务逻辑
                }
            }

            // 自发消息（无 pending future）→ 按 command 执行业务处理
            switch (command) {
                case CMD_CONN -> handleConn(sn, rawJson);
                case CMD_RTD -> handleRtd(sn, rawJson);
                case CMD_IMAGE -> handleImage(sn, rawJson);
                default -> log.debug("Unhandled Xinlutong command: {} sn={}", command, sn);
            }
        } catch (Exception e) {
            log.error("Error handling Xinlutong message. command={}, sn={}", command, sn, e);
        }
    }

    // ──────────────────── 上行消息处理 ────────────────────

    /**
     * 处理设备连接消息（设备上电/重连后发送）。
     * <p>
     * 首次收到 Conn 时自动下发 Config(enableReply=1)。
     * 重连不重复发送（幂等控制）。
     */
    private void handleConn(String sn, Map<String, Object> rawJson) {
        String time = (String) rawJson.get("time");
        String data = (String) rawJson.get("data");

        // Conn 表示设备在线，更新心跳缓存
        heartbeatRecorder.recordHeartbeat(sn);

        String devInfo = null;
        if (data != null && !data.isEmpty()) {
            try {
                Map<String, Object> dataMap = objectMapper.readValue(data,
                        new TypeReference<Map<String, Object>>() {});
                devInfo = (String) dataMap.get("devInfo");
            } catch (Exception e) {
                log.warn("Failed to parse Conn data: sn={}, data={}", sn, data, e);
            }
        }

        log.info("Xinlutong device connected: sn={}, time={}, devInfo={}", sn, time, devInfo);

        if (replyEnabledDevices.add(sn)) {
            sendConfigEnableReply(sn);
        }
    }

    /**
     * 处理心跳（Rtd）—— 更新缓存 + DB 持久化。
     * <p>
     * 真机 Rtd 格式：{@code {"command":"Rtd","data":"{\"hasCar\":2}",...}}。
     */
    private void handleRtd(String sn, Map<String, Object> rawJson) {
        String time = (String) rawJson.get("time");
        String data = (String) rawJson.get("data");

        heartbeatRecorder.recordHeartbeat(sn);

        // 首次见到设备时发送 Config(enableReply=1)（Conn 可能已被错过）
        if (replyEnabledDevices.add(sn)) {
            sendConfigEnableReply(sn);
        }

        // 解析 Rtd data（如 {"hasCar":2}）
        if (data != null && !data.isEmpty()) {
            try {
                Map<String, Object> dataMap = objectMapper.readValue(data,
                        new TypeReference<Map<String, Object>>() {});
                log.debug("Xinlutong Rtd: sn={}, time={}, data={}", sn, time, dataMap);
            } catch (Exception e) {
                log.debug("Xinlutong Rtd: sn={}, time={}, data(raw)={}", sn, time, data);
            }
        } else {
            log.debug("Xinlutong Rtd: sn={}, time={}", sn, time);
        }
    }

    /**
     * 处理车牌识别事件（Image 消息）。
     * <p>
     * v0.4 变更：移除自动开闸逻辑。设备识别到车牌后上报 Image 消息，
     * Device Access 解析并输出统一 PlateRecognizedEvent，由 EventPublisher
     * 异步推送给业务侧，业务侧决策后调用 API 下发开闸命令。
     */
    private void handleImage(String sn, Map<String, Object> rawJson) {
        String requestId = (String) rawJson.get("requestId");
        String time = (String) rawJson.get("time");

        log.info("Xinlutong plate detected: sn={}, requestId={}, time={}", sn, requestId, time);

        // 解析车牌号（Image 消息 data 字段中可能包含 plateNo）
        String plateNo = extractPlateNo(rawJson);
        if (plateNo != null && !plateNo.isBlank()) {
            lastPlateMap.put(sn, plateNo);
        }

        // v0.4: 通过回调接口推送车牌识别事件到业务侧
        if (plateListener != null && plateNo != null && !plateNo.isBlank()) {
            try {
                Long occurredAtMillis = parseTimeToMillis(time);
                PlateRecognizedData data = new PlateRecognizedData(
                        sn,
                        plateNo,
                        null,
                        null,
                        null,
                        null,
                        null,
                        occurredAtMillis
                );
                plateListener.onPlateRecognized(data);
            } catch (Exception e) {
                log.error("Failed to notify plate listener: sn={}, plateNo={}", sn, plateNo, e);
            }
        }
    }

    /**
     * 将信路通时间字符串（yyyy-MM-dd HH:mm:ss）转为毫秒时间戳。
     */
    private Long parseTimeToMillis(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time, TIME_FMT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Exception e) {
            log.debug("Failed to parse time: {}", time);
            return null;
        }
    }

    /**
     * 从 Image 消息中提取车牌号。
     */
    private String extractPlateNo(Map<String, Object> rawJson) {
        String data = (String) rawJson.get("data");
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> dataMap = objectMapper.readValue(data,
                    new TypeReference<Map<String, Object>>() {});
            Object plateNo = dataMap.get("plateNo");
            return plateNo != null ? plateNo.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to extract plateNo from Image data: {}", data);
            return null;
        }
    }

    /**
     * 获取最近识别的车牌号（用于日志上下文）。
     *
     * @param deviceSn 设备序列号
     * @return 最近车牌号，无记录返回空字符串
     */
    public String getLastPlate(String deviceSn) {
        return lastPlateMap.getOrDefault(deviceSn, "");
    }

    // ──────────────────── 下行命令 ────────────────────

    /**
     * 下发 Config 命令启用回执模式。
     * <p>
     * 真机联调：下行命令格式为 {@code {"command":"Config","data":"{\"enableReply\":1}","requestId":"uuid","time":"..."}}。
     * <p>
     * TODO: 加入重试和超时降级。当前 MVP 为 fire-and-forget。
     */
    private void sendConfigEnableReply(String sn) {
        String requestId = UUID.randomUUID().toString();
        String time = LocalDateTime.now().format(TIME_FMT);

        String json = "{\"command\":\"Config\",\"data\":\"{\\\"enableReply\\\":1}\",\"requestId\":\""
                + requestId + "\",\"sn\":\"" + sn + "\",\"time\":\"" + time + "\",\"version\":\"1.0.1\"}";

        log.info("Xinlutong sending Config(enableReply=1): sn={}, requestId={}", sn, requestId);

        String topic = String.format(TOPIC_DOWNLINK, sn);
        mqttGateway.publishRaw(topic, json);
    }

    /**
     * 下发开门命令。
     */
    public CompletableFuture<Map<String, Object>> sendOpen(String deviceSn, long timeoutSeconds) {
        return sendCommand(deviceSn, "Open", "", timeoutSeconds);
    }

    /**
     * 下发关门命令。
     */
    public CompletableFuture<Map<String, Object>> sendClose(String deviceSn, long timeoutSeconds) {
        return sendCommand(deviceSn, "Close", "", timeoutSeconds);
    }

    /**
     * 下发校时命令。
     * <p>
     * 联调结论：设备不支持任何校时命令（set_time/SetTime/TimeSync 等均返回 errCode=1 "Unknow CMD"）。
     * 使用 {@code set_time} 而非 {@code Rtd}，因为 Rtd 被设备视为心跳不下发回执，会导致调用方超时。
     * set_time 至少会返回 errCode 让调用方立即获知结果。
     */
    public CompletableFuture<Map<String, Object>> sendSyncTime(String deviceSn, long timeoutSeconds) {
        String serverTime = LocalDateTime.now().format(TIME_FMT);
        String data = "{\"serverTime\":\"" + serverTime + "\"}";
        return sendCommand(deviceSn, "set_time", data, timeoutSeconds);
    }

    /**
     * 通用命令下发。
     */
    private CompletableFuture<Map<String, Object>> sendCommand(String deviceSn, String command,
                                                               String data, long timeoutSeconds) {
        String requestId = UUID.randomUUID().toString();
        String time = LocalDateTime.now().format(TIME_FMT);

        // 构造下行 JSON（字段与设备上行格式对齐：command/data/requestId/sn/time/version）
        StringBuilder json = new StringBuilder();
        json.append("{\"command\":\"").append(command).append("\",\"data\":");
        if (data == null || data.isEmpty()) {
            json.append("\"\"");
        } else {
            json.append("\"").append(escapeJson(data)).append("\"");
        }
        json.append(",\"requestId\":\"").append(requestId).append("\"");
        json.append(",\"sn\":\"").append(deviceSn).append("\"");
        json.append(",\"time\":\"").append(time).append("\"");
        json.append(",\"version\":\"1.0.1\"");
        json.append("}");

        String jsonStr = json.toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingFutures.put(requestId, future);

        future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((r, e) -> pendingFutures.remove(requestId));

        String topic = String.format(TOPIC_DOWNLINK, deviceSn);
        log.info("Xinlutong sending command: sn={}, command={}, requestId={}, topic={}",
                deviceSn, command, requestId, topic);

        try {
            mqttGateway.publishRaw(topic, jsonStr);
        } catch (Exception e) {
            pendingFutures.remove(requestId);
            future.completeExceptionally(e);
            log.error("Failed to send Xinlutong command: sn={}, command={}", deviceSn, command, e);
        }

        return future;
    }

    // ──────────────────── 状态查询 ────────────────────

    /**
     * 查询设备在线状态。
     * <p>
     * 通过心跳缓存判断：最近 90 秒内有心跳视为在线。
     */
    public boolean isDeviceOnline(String deviceSn) {
        return heartbeatRecorder.isDeviceOnline(deviceSn, 90_000L);
    }

    // ──────────────────── 命令结果解析 ────────────────────

    /**
     * 从命令回执中解析执行结果。
     * <p>
     * 信路通回执格式：{@code {"errCode":0,"errInfo":""}}。
     *
     * @param reply 设备回执消息体
     * @return 结构化命令结果
     */
    public XinlutongCommandResult parseCommandResult(Map<String, Object> reply) {
        if (reply == null) {
            log.warn("Xinlutong command result is null");
            return XinlutongCommandResult.parseFailure("null reply");
        }

        String data = (String) reply.get("data");
        if (data == null || data.isEmpty()) {
            log.warn("Xinlutong command result has no data field: {}", reply);
            return XinlutongCommandResult.parseFailure(data);
        }

        try {
            Map<String, Object> resultMap = objectMapper.readValue(data,
                    new TypeReference<Map<String, Object>>() {});
            Object errCodeObj = resultMap.get("errCode");
            int errCode = errCodeObj == null ? -1 : ((Number) errCodeObj).intValue();
            String errInfo = (String) resultMap.get("errInfo");
            if (errInfo == null) {
                errInfo = "";
            }

            if (errCode == 0) {
                return XinlutongCommandResult.success();
            }
            return XinlutongCommandResult.failure(errCode, errInfo);
        } catch (Exception e) {
            log.warn("Failed to parse Xinlutong command result data: {}", data, e);
            return XinlutongCommandResult.parseFailure(data);
        }
    }

    // ──────────────────── 工具方法 ────────────────────

    /**
     * 从 Topic 中提取设备序列号（fallback，sn 通常从消息体获取）。
     */
    private String extractSnFromTopic(String topic) {
        if (topic == null || topic.isEmpty()) {
            return null;
        }
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    /**
     * 转义 JSON 字符串中的特殊字符（用于嵌入外层 JSON 的 data 字段）。
     */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ═══════════════════════════════════════════
    // 显示屏控制（v0.4 新增，通过 SerialData 透传 OLM-M1D 协议）
    // ═══════════════════════════════════════════

    /**
     * 通过 SerialData 命令下发 OLM-M1D 协议帧。
     * <p>
     * 信路通格式：{@code {"command":"SerialData","data":"{\"serialData\":[{\"channel\":0,\"data\":\"Base64\",\"len\":44}]}",...}}
     *
     * @param deviceSn      设备序列号
     * @param serialChannel 串口通道号（默认 0）
     * @param rawData       OLM-M1D 协议帧字节
     * @param interval      帧间隔毫秒
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> sendSerialData(String deviceSn, int serialChannel,
                                                                  byte[] rawData, int interval) {
        String requestId = UUID.randomUUID().toString();
        String time = LocalDateTime.now().format(TIME_FMT);

        String base64Data = java.util.Base64.getEncoder().encodeToString(rawData);

        // 构造 data 字段内的 serialData 对象
        String serialDataJson = String.format(
                "{\"serialData\":[{\"channel\":%d,\"data\":\"%s\",\"len\":%d}]}",
                serialChannel, base64Data, rawData.length);

        // 构造外层 JSON（data 字段需要转义为字符串）
        StringBuilder json = new StringBuilder();
        json.append("{\"command\":\"SerialData\",\"data\":\"");
        json.append(escapeJson(serialDataJson));
        json.append("\",\"requestId\":\"").append(requestId).append("\"");
        json.append(",\"sn\":\"").append(deviceSn).append("\"");
        json.append(",\"time\":\"").append(time).append("\"");
        json.append(",\"version\":\"1.0.1\"");
        json.append("}");

        String jsonStr = json.toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingFutures.put(requestId, future);

        future.orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((r, e) -> pendingFutures.remove(requestId));

        String topic = String.format(TOPIC_DOWNLINK, deviceSn);
        log.info("[Display] Xinlutong SEND SerialData  deviceSn={}  requestId={}  channel={}  len={}  topic={}",
                deviceSn, requestId, serialChannel, rawData.length, topic);

        try {
            mqttGateway.publishRaw(topic, jsonStr);
        } catch (Exception e) {
            pendingFutures.remove(requestId);
            future.completeExceptionally(e);
            log.error("[Display] Failed to send SerialData: sn={}, error={}", deviceSn, e.getMessage());
        }

        return future;
    }

    /**
     * 设置音量。
     * <p>
     * 对应 OLM-M1D 0x0D 命令。
     *
     * @param deviceSn 设备序列号
     * @param percent  音量百分比（0~100）
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> setVolume(String deviceSn, int percent) {
        byte[] frame = OlmM1dProtocol.buildVolumeFrame(OlmM1dProtocol.DA_DEFAULT, percent);
        log.info("[Display] ACTION: SET_VOLUME  deviceSn={}  volume={}%  cmd=0x0D", deviceSn, percent);
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 同步屏卡时间。
     * <p>
     * 对应 OLM-M1D 0x05 命令，使用服务器当前时间。
     *
     * @param deviceSn 设备序列号
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> syncDisplayTime(String deviceSn) {
        LocalDateTime now = LocalDateTime.now();
        int week = now.getDayOfWeek().getValue() % 7;
        byte[] frame = OlmM1dProtocol.buildSyncTimeFrame(
                OlmM1dProtocol.DA_DEFAULT,
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond(), week);
        log.info("[Display] ACTION: SYNC_TIME  deviceSn={}  time={}  cmd=0x05",
                deviceSn, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 设置显示方向。
     * <p>
     * 对应 OLM-M1D 0x19 命令。
     *
     * @param deviceSn  设备序列号
     * @param direction 方向：0=正常，1=旋转180度
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> setDisplayDirection(String deviceSn, int direction) {
        byte[] frame = OlmM1dProtocol.buildDirectionFrame(OlmM1dProtocol.DA_DEFAULT, direction);
        log.info("[Display] ACTION: SET_DIRECTION  deviceSn={}  direction={}  cmd=0x19",
                deviceSn, direction == 1 ? "ROTATE_180" : "NORMAL");
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 播放语音。
     * <p>
     * 对应 OLM-M1D 0x30 命令。
     *
     * @param deviceSn 设备序列号
     * @param voiceId  语音ID（0~341）
     * @param variable 变量替换文本（可选）
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> playVoice(String deviceSn, int voiceId, String variable) {
        byte[] frame = OlmM1dProtocol.buildPlayVoiceFrame(OlmM1dProtocol.DA_DEFAULT, variable, voiceId);
        log.info("[Display] ACTION: PLAY_VOICE  deviceSn={}  voiceId={}  variable=\"{}\"  cmd=0x30",
                deviceSn, voiceId, variable != null ? variable : "");
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 停止语音。
     * <p>
     * 对应 OLM-M1D 0x31 命令。
     *
     * @param deviceSn 设备序列号
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> stopVoice(String deviceSn) {
        byte[] frame = OlmM1dProtocol.buildStopVoiceFrame(OlmM1dProtocol.DA_DEFAULT);
        log.info("[Display] ACTION: STOP_VOICE  deviceSn={}  cmd=0x31", deviceSn);
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 增强版实时显示文字。
     * <p>
     * 支持字体、颜色、语音同步。
     * 对应 OLM-M1D 0x6F 命令（SF=0）。
     *
     * @param deviceSn       设备序列号
     * @param content        文本内容
     * @param direction      文本布局方向
     * @param font           字体类型
     * @param color          文字颜色 RGBA
     * @param voiceId        同步语音ID（可选）
     * @param voiceVariable  语音变量替换文本（可选）
     * @return Future，true 表示设备回执 errCode=0
     */
    public CompletableFuture<Boolean> displayTextEnhanced(String deviceSn, String content,
                                                           DisplayDirection direction,
                                                           OlmM1dProtocol.FontType font,
                                                           java.util.List<int[]> color,
                                                           Integer voiceId,
                                                           String voiceVariable) {
        List<String> lines = toLines(content, direction);

        // 构造语音参数
        byte[] voiceParams = null;
        if (voiceId != null) {
            java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
            byte[] varBytes = (voiceVariable != null && !voiceVariable.isEmpty())
                    ? voiceVariable.getBytes(gbk) : new byte[0];
            // VF(1) + VTL(1) + voiceId(1) + varBytes
            voiceParams = new byte[3 + varBytes.length];
            voiceParams[0] = 0x0A; // VF
            voiceParams[1] = (byte) (varBytes.length + 1); // VTL
            voiceParams[2] = (byte) (int) voiceId;
            if (varBytes.length > 0) {
                System.arraycopy(varBytes, 0, voiceParams, 3, varBytes.length);
            }
        }

        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(
                OlmM1dProtocol.DA_DEFAULT,
                lines.size(),
                lines,
                font,
                color,
                voiceParams);
        log.info("[Display] ACTION: DISPLAY_TEXT_ENHANCED  deviceSn={}  direction={}  rows={}  font={}  voiceId={}  cmd=0x6F" +
                        "\n  content=\"{}\"",
                deviceSn, direction, lines.size(),
                font != null ? font.name() : "DEFAULT",
                voiceId != null ? voiceId : "NONE",
                content.replace("\n", "\\n"));
        return sendSerialData(deviceSn, 0, frame, 50)
                .thenApply(reply -> {
                    XinlutongCommandResult result = parseCommandResult(reply);
                    boolean ok = result.isSuccess();
                    log.info("[Display] DISPLAY_TEXT_ENHANCED result: deviceSn={}  success={}  errCode={}",
                            deviceSn, ok, result.getErrCode());
                    return ok;
                });
    }

    /**
     * 启用/关闭显示屏。
     * <p>
     * 通过亮度控制实现。enabled=true 时设置亮度为 80%，false 时为最低有效亮度。
     *
     * @param deviceSn 设备序列号
     * @param enabled  true=启用，false=关闭
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> setDisplayEnabled(String deviceSn, boolean enabled) {
        int brightness = enabled ? 80 : OlmM1dProtocol.BRIGHTNESS_OFF;
        byte[] frame = OlmM1dProtocol.buildBrightnessFrame(OlmM1dProtocol.DA_DEFAULT, brightness);
        log.info("[Display] ACTION: {}  deviceSn={}  brightness={}%  cmd=0x0C",
                enabled ? "ENABLE" : "DISABLE", deviceSn, brightness);
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 设置显示屏模式（2行/4行）。
     * <p>
     * 通过 0x6F 单包多行显示命令配置显示卡的 zone 布局。
     * 帧中文本为空 —— Device Access 只配置布局，不管理显示内容。
     *
     * @param deviceSn 设备序列号
     * @param mode     显示模式（TWO_LINE / FOUR_LINE）
     * @return 设备回执 Future
     */
    public CompletableFuture<Map<String, Object>> setDisplayMode(String deviceSn, DisplayMode mode) {
        int rows = (mode == DisplayMode.TWO_LINE) ? 2 : 4;
        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(OlmM1dProtocol.DA_DEFAULT, rows);
        log.info("[Display] ACTION: SET_MODE  deviceSn={}  mode={}  rows={}  cmd=0x6F",
                deviceSn, mode, rows);
        return sendSerialData(deviceSn, 0, frame, 50);
    }

    /**
     * 实时显示文字。
     * <p>
     * 向控制卡临时区（RAM）下发文字内容，立即覆盖当前显示。
     * 内容掉电丢失，不影响控制卡存储区的内容。
     * <p>
     * 对应 OLM-M1D 0x6F 命令（SF=0）。
     *
     * @param deviceSn  设备序列号
     * @param content   文本内容，HORIZONTAL 时用 \n 分隔多行
     * @param direction 文本布局方向（当前仅支持 HORIZONTAL）
     * @return Future，true 表示设备回执 errCode=0
     */
    public CompletableFuture<Boolean> displayText(String deviceSn, String content,
                                                   DisplayDirection direction) {
        List<String> lines = toLines(content, direction);
        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(OlmM1dProtocol.DA_DEFAULT, lines.size(), lines);
        log.info("[Display] ACTION: DISPLAY_TEXT  deviceSn={}  direction={}  rows={}  cmd=0x6F" +
                        "\n  content=\"{}\"",
                deviceSn, direction, lines.size(), content.replace("\n", "\\n"));
        return sendSerialData(deviceSn, 0, frame, 50)
                .thenApply(reply -> {
                    XinlutongCommandResult result = parseCommandResult(reply);
                    boolean ok = result.isSuccess();
                    log.info("[Display] DISPLAY_TEXT result: deviceSn={}  success={}  errCode={}",
                            deviceSn, ok, result.getErrCode());
                    return ok;
                });
    }

    /**
     * 保存显示内容到控制卡。
     * <p>
     * 将文字逐行写入控制卡外部存储器（Flash），掉电不丢失。
     * 控制卡空闲时会自动循环显示已存储的持久化内容。
     * <p>
     * <b>低频操作。</b>每次调用会擦写控制卡 Flash，频繁调用会降低存储器寿命。
     * <p>
     * 对应 OLM-M1D 0x67 命令。
     *
     * @param deviceSn  设备序列号
     * @param content   文本内容，HORIZONTAL 时用 \n 分隔多行
     * @param direction 文本布局方向（当前仅支持 HORIZONTAL）
     * @return Future，true 表示所有行均发送成功且设备回执 errCode=0
     */
    public CompletableFuture<Boolean> saveDisplay(String deviceSn, String content,
                                                   DisplayDirection direction) {
        List<String> lines = toLines(content, direction);
        log.info("[Display] ACTION: SAVE_DISPLAY  deviceSn={}  direction={}  rows={}  cmd=0x67" +
                        "\n  content=\"{}\"",
                deviceSn, direction, lines.size(), content.replace("\n", "\\n"));

        return CompletableFuture.supplyAsync(() -> {
            boolean allOk = true;
            for (int i = 0; i < lines.size(); i++) {
                byte[] frame = OlmM1dProtocol.build0x67Frame(OlmM1dProtocol.DA_DEFAULT, i, lines.get(i));
                log.info("[Display] SAVE row {}/{}: deviceSn={}  twid={}  text=\"{}\"",
                        i + 1, lines.size(), deviceSn, i, lines.get(i));
                try {
                    var reply = sendSerialData(deviceSn, 0, frame, 50)
                            .get(10, TimeUnit.SECONDS);
                    XinlutongCommandResult result = parseCommandResult(reply);
                    if (!result.isSuccess()) {
                        log.warn("[Display] SAVE row {}/{} FAILED: deviceSn={}  errCode={}  errInfo={}",
                                i + 1, lines.size(), deviceSn, result.getErrCode(), result.getErrInfo());
                        allOk = false;
                    } else {
                        log.info("[Display] SAVE row {}/{} OK: deviceSn={}",
                                i + 1, lines.size(), deviceSn);
                    }
                    // 行间短暂间隔，避免控制卡串口缓冲区溢出
                    if (i < lines.size() - 1) {
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    log.error("[Display] SAVE row {}/{} ERROR: deviceSn={}  errorType={}  message={}",
                            i + 1, lines.size(), deviceSn,
                            e.getClass().getName(), e.getMessage(), e);
                    allOk = false;
                }
            }
            log.info("[Display] SAVE_DISPLAY complete: deviceSn={}  allOk={}  rows={}",
                    deviceSn, allOk, lines.size());
            return allOk;
        });
    }

    // ──────────────────── 文本布局转换 ────────────────────

    /**
     * 将平台显示请求转换为行文本列表。
     * <p>
     * 当前仅支持 HORIZONTAL 模式（按 \n 拆分）。
     * VERTICAL 模式依赖真实 LED 点阵参数，暂不实现。
     *
     * @param content   文本内容
     * @param direction 布局方向
     * @return 行文本列表（每行一个字符串）
     * @throws UnsupportedOperationException 当 direction=VERTICAL 时
     */
    private List<String> toLines(String content, DisplayDirection direction) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be null or blank");
        }
        switch (direction) {
            case HORIZONTAL:
                return Arrays.asList(content.split("\n"));
            case VERTICAL:
                throw new UnsupportedOperationException(
                        "VERTICAL display layout is not implemented yet");
            default:
                throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }
}
