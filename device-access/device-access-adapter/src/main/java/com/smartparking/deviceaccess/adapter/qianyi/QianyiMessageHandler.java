package com.smartparking.deviceaccess.adapter.qianyi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.adapter.support.DeviceHeartbeatRecorder;
import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.common.event.PlateRecognizedListener;
import com.smartparking.deviceaccess.mqtt.MqttGateway;
import com.smartparking.deviceaccess.mqtt.MqttRawMessageListener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 芊熠车牌相机 MQTT 消息处理器。
 * <p>
 * 协议依据：《芊熠智能：MQTT通信协议-基线260401》（V4.23）。要点：
 * <ul>
 *   <li>消息信封扁平 JSON：{@code {cmd, msg_id, ...}}，无版本字段、无嵌套字符串 JSON</li>
 *   <li>主题不固定：设备开机向固定主题 {@code /serverAll} 发 {@code camera_register}，
 *       上报自己的 {@code subtopic}（平台下行用）和 {@code pubtopic}（设备上行用）；
 *       车牌相机默认上行 {@code aiot/plate/{sn}}，默认下行 {@code device/plate/{sn}}</li>
 *   <li>应答 cmd = 原 cmd + {@code _rsp}，唯一关联字段 {@code msg_id} 原样回传；
 *       成功标识 {@code status == "ok"}（字符串小写，其它值为错误描述）</li>
 *   <li>识别上行 {@code result} 必须回 {@code result_rsp}；心跳 {@code mqtt_herat}
 *       （原文拼写错误，协议保留）默认 30 秒间隔，无需应答</li>
 *   <li>msg_id 规则：前 13 位毫秒时间 + 后 7 位字母数字随机</li>
 * </ul>
 * <p>
 * v0.5 新增。结构参照 {@link com.smartparking.deviceaccess.adapter.xinlutong.XinlutongMessageHandler}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QianyiMessageHandler implements MqttRawMessageListener {

    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final DeviceHeartbeatRecorder heartbeatRecorder;

    /** 车牌识别事件回调（可选注入，由 api 模块 PlateRecognizedEventDispatcher 实现） */
    @Autowired(required = false)
    private PlateRecognizedListener plateListener;

    /** 设备注册上报的下行主题，key = sn */
    private final Map<String, String> subtopicMap = new ConcurrentHashMap<>();

    /** 已注册设备的上行主题集合（pubtopic），用于 Topic 守卫 */
    private final Set<String> uplinkTopics = ConcurrentHashMap.newKeySet();

    /** 设备最近一条 qymqtt 自定义上行主题，key = sn（小写），用于未重新注册时推导下行主题 */
    private final Map<String, String> uplinkTopicBySn = new ConcurrentHashMap<>();

    /** 等待设备应答的 Future，key = msg_id */
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingFutures = new ConcurrentHashMap<>();

    /** 最近识别的车牌号，key = sn */
    private final Map<String, String> lastPlateMap = new ConcurrentHashMap<>();

    /** result 消息去重缓存（msg_id → 接收时间毫秒），防重叠订阅重复投递 */
    private final Map<String, Long> recentResultMsgIds = new ConcurrentHashMap<>();

    /** result 去重窗口（毫秒） */
    private static final long RESULT_DUP_WINDOW_MS = 10_000;

    /**
     * 判断 result 消息是否为短时间内的重复投递。
     */
    private boolean isDuplicateDelivery(String msgId) {
        if (msgId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long seen = recentResultMsgIds.putIfAbsent(msgId, now);
        if (seen != null && now - seen < RESULT_DUP_WINDOW_MS) {
            return true;
        }
        // 顺带清理过期条目，避免无限增长
        if (recentResultMsgIds.size() > 512) {
            recentResultMsgIds.entrySet().removeIf(e -> now - e.getValue() > RESULT_DUP_WINDOW_MS);
        }
        return false;
    }

    // ──────────────────── 协议常量 ────────────────────

    /** 设备注册固定主题（平台订阅） */
    static final String TOPIC_REGISTER = "/serverAll";

    /** 车牌相机默认上行主题前缀（aiot/plate/{sn}） */
    static final String DEFAULT_UPLINK_PREFIX = "aiot/plate/";

    /** 车牌相机默认下行主题模板（device/plate/{sn}），注册信息缺失时的兜底 */
    private static final String DEFAULT_SUBTOPIC_TEMPLATE = "device/plate/%s";

    /** qymqtt 协议自定义上行主题前后缀（/{产品标识}/{sn}/qymqttpost） */
    private static final String QYMQTT_UPLINK_PREFIX = "/qymqtt/";
    private static final String QYMQTT_UPLINK_SUFFIX = "/qymqttpost";

    /** qymqtt 协议自定义下行主题后缀（与上行同前缀，post→down） */
    private static final String QYMQTT_DOWNLINK_SUFFIX = "/qymqttdown";

    // cmd 名称（注意：mqtt_herat 为原文拼写错误，协议保留不修正）
    private static final String CMD_CAMERA_REGISTER = "camera_register";
    private static final String CMD_HEARTBEAT = "mqtt_herat";
    private static final String CMD_OFFLINE = "offline";
    private static final String CMD_RESULT = "result";
    private static final String CMD_TARKPHOTO = "tarkphoto";
    private static final String CMD_SNAPSHOT = "snapshot";

    private static final String STATUS_OK = "ok";

    /** 无牌车 plate_num 取值（协议规定为字符串 "null"，非 JSON null） */
    private static final String NO_PLATE = "null";

    /** 开闸继电器编号（ionum=0，实测有效） */
    private static final int GATE_OPEN_IONUM = 0;
    /** 关闸继电器编号（ionum=2，继电器响但需确认接线） */
    private static final int GATE_CLOSE_IONUM = 2;

    /** syncSysTime 时区枚举：0=GMT+12 … 24=GMT-12，GMT+8 = 4（文档示例写 20 疑似笔误，实测验证） */
    private static final int TIME_ZONE_GMT8 = 4;

    private static final String MSG_ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ──────────────────── 生命周期 ────────────────────

    @PostConstruct
    public void init() {
        mqttGateway.addRawListener(this);
        log.info("QianyiMessageHandler registered as MQTT raw listener");
    }

    // ──────────────────── 消息入口 ────────────────────

    @Override
    public void onRawMessage(String topic, Map<String, Object> rawJson) {
        if (rawJson == null) {
            return;
        }
        // 仅处理芊熠 Topic：注册主题 / 默认上行前缀 / 已注册设备的 pubtopic
        if (!isQianyiTopic(topic)) {
            return;
        }

        String cmd = (String) rawJson.get("cmd");
        String sn = (String) rawJson.get("sn");
        if (sn == null) {
            sn = extractSnFromTopic(topic);
        }
        if (sn != null) {
            // SN 统一小写规范化：相机按自身大小写上报（如 15ZK...），而注册表/REST 路径
            // 均规范化为小写，统一后 subtopicMap / 心跳缓存 / pendingFutures 键才一致
            sn = sn.toLowerCase();
            // 记录 qymqtt 自定义上行主题：DA 重启后设备未重新注册时可按同前缀推导下行主题
            if (isQymqttUplink(topic)) {
                uplinkTopicBySn.put(sn, topic);
            }
        }

        if (cmd == null) {
            log.warn("Qianyi message without 'cmd' field. Topic: {}, keys: {}", topic, rawJson.keySet());
            return;
        }
        if (sn == null) {
            log.warn("Qianyi message without 'sn'. Topic: {}, cmd: {}", topic, cmd);
            return;
        }

        try {
            // 先检查是否为命令应答（*_rsp 原样回传 msg_id）
            // 必须在业务 switch 之前检查，否则应答会被业务 handler 消费掉
            String msgId = (String) rawJson.get("msg_id");
            if (msgId != null) {
                CompletableFuture<Map<String, Object>> future = pendingFutures.remove(msgId);
                if (future != null) {
                    log.info("Qianyi reply matched: cmd={}, sn={}, msg_id={}", cmd, sn, msgId);
                    future.complete(rawJson);
                    return;  // 已作为应答处理，不再走业务逻辑
                }
            }

            // 自发消息（无 pending future）→ 按 cmd 执行业务处理
            switch (cmd) {
                case CMD_CAMERA_REGISTER -> handleRegister(sn, rawJson);
                case CMD_HEARTBEAT -> handleHeartbeat(sn, rawJson);
                case CMD_OFFLINE -> handleOffline(sn, rawJson);
                case CMD_RESULT -> handleResult(sn, rawJson);
                default -> log.debug("Unhandled Qianyi cmd: {} sn={}", cmd, sn);
            }
        } catch (Exception e) {
            log.error("Error handling Qianyi message. cmd={}, sn={}", cmd, sn, e);
        }
    }

    /**
     * Topic 守卫：注册主题、默认上行前缀、已注册设备的 pubtopic、
     * qymqtt 协议自定义上行（/{产品标识}/{sn}/qymqttpost，静态订阅，注册前即可收）。
     */
    private boolean isQianyiTopic(String topic) {
        return TOPIC_REGISTER.equals(topic)
                || topic.startsWith(DEFAULT_UPLINK_PREFIX)
                || uplinkTopics.contains(topic)
                || isQymqttUplink(topic);
    }

    /** qymqtt 协议自定义上行主题：/qymqtt/{sn}/qymqttpost */
    private static boolean isQymqttUplink(String topic) {
        return topic != null && topic.startsWith(QYMQTT_UPLINK_PREFIX) && topic.endsWith(QYMQTT_UPLINK_SUFFIX);
    }

    // ──────────────────── 上行消息处理 ────────────────────

    /**
     * 处理设备注册（开机/重连后发往 /serverAll）。
     * <p>
     * 缓存 sn → subtopic/pubtopic 映射；pubtopic 未被默认通配覆盖时动态订阅；
     * 必须向 subtopic 回 camera_register_rsp（非 ok 设备不会设置相应帐号）。
     */
    private void handleRegister(String sn, Map<String, Object> rawJson) {
        String subtopic = (String) rawJson.get("subtopic");
        String pubtopic = (String) rawJson.get("pubtopic");
        String model = (String) rawJson.get("model");
        String softVersion = (String) rawJson.get("softVersion");

        if (subtopic != null && !subtopic.isBlank()) {
            subtopicMap.put(sn, subtopic);
        }
        if (pubtopic != null && !pubtopic.isBlank()) {
            uplinkTopics.add(pubtopic);
            if (!pubtopic.startsWith(DEFAULT_UPLINK_PREFIX)) {
                // 非默认通配覆盖的上行主题 → 动态订阅
                mqttGateway.subscribe(pubtopic);
                log.info("Qianyi dynamic subscribed pubtopic: sn={}, pubtopic={}", sn, pubtopic);
            }
        }

        heartbeatRecorder.recordHeartbeat(sn);
        log.info("Qianyi device registered: sn={}, subtopic={}, pubtopic={}, model={}, softVersion={}",
                sn, subtopic, pubtopic, model, softVersion);

        // 应答注册（协议要求，发往设备订阅主题）
        String msgId = (String) rawJson.get("msg_id");
        sendResponse(sn, CMD_CAMERA_REGISTER + "_rsp", msgId);
    }

    /**
     * 处理心跳（mqtt_herat，默认 30 秒间隔，消息内无时间戳，无需应答）。
     */
    private void handleHeartbeat(String sn, Map<String, Object> rawJson) {
        heartbeatRecorder.recordHeartbeat(sn);
        log.debug("Qianyi heartbeat: sn={}", sn);
    }

    /**
     * 处理离线遗嘱消息（broker 代发，无需应答）。
     * <p>
     * 离线状态判定仍走 DeviceHeartbeatRecorder 的 90 秒超时扫描，此处仅记日志。
     */
    private void handleOffline(String sn, Map<String, Object> rawJson) {
        log.info("Qianyi device offline (last-will): sn={}", sn);
    }

    /**
     * 处理车牌识别结果上报（result）。
     * <p>
     * 协议要求平台回 result_rsp（仅 cmd/status/msg_id，不含开闸决策；
     * 是否开闸由业务侧决策后另行下发 iooutput）。
     * type=offline 为断网续传结果，与在线结果同样处理（业务侧负责去重）。
     */
    private void handleResult(String sn, Map<String, Object> rawJson) {
        String msgId = (String) rawJson.get("msg_id");
        String type = (String) rawJson.get("type");

        // 先回应答，再解析上报
        sendResponse(sn, CMD_RESULT + "_rsp", msgId);

        // 重复投递去重：静态通配订阅与设备动态 pubtopic 订阅重叠时，
        // broker 会将同一消息投递两次，按 msg_id 在短窗口内幂等
        if (isDuplicateDelivery(msgId)) {
            log.debug("重复投递忽略: sn={}, msg_id={}", sn, msgId);
            return;
        }

        String plateNum = (String) rawJson.get("plate_num");
        if (NO_PLATE.equals(plateNum)) {
            plateNum = null;  // 无牌车
        }

        log.info("Qianyi plate detected: sn={}, plate={}, type={}, inout={}, msg_id={}, keys={}",
                sn, plateNum, type, rawJson.get("inout"), msgId, rawJson.keySet());

        if (plateNum != null && !plateNum.isBlank()) {
            lastPlateMap.put(sn, plateNum);
        }

        if (plateListener != null && plateNum != null && !plateNum.isBlank()) {
            try {
                // 图片来源优先级：base64（MQTT 直传） > 本地路径（待 HTTP 上传）
                String fullPic = (String) rawJson.get("full_pic");
                String platePic = (String) rawJson.get("plate_pic");
                Long utcTs = toEpochMillis(rawJson.get("utc_ts"));

                // 若 MQTT 消息中含 base64 图片，标记 data.type 前缀以便 Dispatcher 识别
                String imagePath = (String) rawJson.get("full_pic_path");
                String plateImagePath = (String) rawJson.get("plate_pic_path");
                if (fullPic != null && !fullPic.isBlank()) {
                    imagePath = "base64:" + fullPic;
                    log.info("Qianyi result carries base64 full_pic: sn={}, len={}", sn, fullPic.length());
                }
                if (platePic != null && !platePic.isBlank()) {
                    plateImagePath = "base64:" + platePic;
                }

                PlateRecognizedData data = new PlateRecognizedData(
                        sn,
                        plateNum,
                        toInteger(rawJson.get("confidence")),
                        null,   // direction: 芊熠为 in/out 语义，PlateRecognizedData 为编号，不传
                        null,   // plateColor: 芊熠为中文字符串枚举，PlateRecognizedData 为编号，不传
                        imagePath,
                        plateImagePath,
                        utcTs
                );
                plateListener.onPlateRecognized(data);
            } catch (Exception e) {
                log.error("Failed to notify plate listener: sn={}, plate={}", sn, plateNum, e);
            }
        }
    }

    /**
     * 获取最近识别的车牌号（用于日志上下文）。
     *
     * @param deviceSn 设备序列号
     * @return 最近车牌号，无记录返回空字符串
     */
    public String getLastPlate(String deviceSn) {
        // 统一小写匹配：lastPlateMap 的 key 来自 handleResult 中已 lowerCase 的 sn
        return lastPlateMap.getOrDefault(deviceSn.toLowerCase(), "");
    }

    // ──────────────────── 下行命令 ────────────────────

    /**
     * 下发开闸命令（脉冲触发）。
     * <p>
     * {@code {"cmd":"iooutput","msg_id":"...","ionum":0,"action":"on","utc_ts":秒}}
     * 参照臻识 IO0 开闸。
     */
    public CompletableFuture<Map<String, Object>> sendOpenGate(String deviceSn, long timeoutSeconds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ionum", GATE_OPEN_IONUM);
        fields.put("action", "on");
        fields.put("utc_ts", Instant.now().getEpochSecond());
        return sendCommand(deviceSn, "iooutput", fields, timeoutSeconds);
    }

    /**
     * 下发关闸命令（脉冲触发）。
     * <p>
     * {@code {"cmd":"iooutput","msg_id":"...","ionum":2,"action":"on","utc_ts":秒}}
     */
    public CompletableFuture<Map<String, Object>> sendCloseGate(String deviceSn, long timeoutSeconds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ionum", GATE_CLOSE_IONUM);
        fields.put("action", "on");
        fields.put("utc_ts", Instant.now().getEpochSecond());
        return sendCommand(deviceSn, "iooutput", fields, timeoutSeconds);
    }

    /**
     * 常开控制。
     * <p>
     * {@code {"cmd":"barrierKeepOpen","msg_id":"...","isKeepOpen":1|0}}
     * 1=保持常开，0=取消常开（文档表格与示例矛盾，以示例及 7.2.15 节为准，实测验证）。
     * 取消常开后如需落闸，需另行下发 sendCloseGate。
     */
    public CompletableFuture<Map<String, Object>> sendBarrierKeepOpen(String deviceSn, int isKeepOpen,
                                                                       long timeoutSeconds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("isKeepOpen", isKeepOpen);
        return sendCommand(deviceSn, "barrierKeepOpen", fields, timeoutSeconds);
    }

    /**
     * 校时命令。
     * <p>
     * {@code {"cmd":"syncSysTime","msg_id":"...","time_zone":4,"time_stamp":"UTC秒（字符串）"}}
     */
    public CompletableFuture<Map<String, Object>> sendSyncSysTime(String deviceSn, long timeoutSeconds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("time_zone", TIME_ZONE_GMT8);
        fields.put("time_stamp", String.valueOf(Instant.now().getEpochSecond()));
        return sendCommand(deviceSn, "syncSysTime", fields, timeoutSeconds);
    }

    /**
     * RS485 串口透传（LED 屏显等）。
     * <p>
     * {@code {"cmd":"rs485","msg_id":"...","encode_type":"base64","rs485ch1_data":[{"data":"base64"}]}}
     *
     * @param frames OLM-M1D 协议帧列表，每帧 Base64 编码后放入
     */
    public CompletableFuture<Map<String, Object>> sendRs485(String deviceSn, List<byte[]> frames,
                                                             long timeoutSeconds) {
        List<Map<String, String>> ch1Data = new ArrayList<>(frames.size());
        for (byte[] frame : frames) {
            ch1Data.add(Map.of("data", java.util.Base64.getEncoder().encodeToString(frame)));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("encode_type", "base64");
        fields.put("rs485ch1_data", ch1Data);
        return sendCommand(deviceSn, "rs485", fields, timeoutSeconds);
    }

    /**
     * 下发抓拍图片命令（车牌相机专用）。
     * <p>
     * 协议 §7：{@code {"cmd":"tarkphoto","msg_id":"...","utc_ts":秒级时间戳}}
     * 应答 {@code tarkphoto_rsp} 含 {@code plate_pic}（BASE64 编码车牌图）。
     *
     * @param deviceSn     设备序列号
     * @param timeoutSeconds 超时秒数
     * @return 设备应答 Future
     */
    public CompletableFuture<Map<String, Object>> sendTarkphoto(String deviceSn, long timeoutSeconds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("utc_ts", Instant.now().getEpochSecond());
        return sendCommand(deviceSn, CMD_TARKPHOTO, fields, timeoutSeconds);
    }

    /**
     * 下发通用抓拍图片命令。
     * <p>
     * 协议 §5.2.6：{@code {"cmd":"snapshot","msg_id":"..."}}
     * 应答 {@code snapshot_rsp} 含 {@code picture}（BASE64 编码 JPG）。
     *
     * @param deviceSn     设备序列号
     * @param timeoutSeconds 超时秒数
     * @return 设备应答 Future
     */
    public CompletableFuture<Map<String, Object>> sendSnapshot(String deviceSn, long timeoutSeconds) {
        return sendCommand(deviceSn, CMD_SNAPSHOT, null, timeoutSeconds);
    }

    /**
     * 通用命令下发。
     * <p>
     * 生成 msg_id（13 位毫秒 + 7 位字母数字随机）→ 组 JSON → 注册 pendingFutures
     * → publishRaw 到设备 subtopic → orTimeout 超时自动清理。
     */
    private CompletableFuture<Map<String, Object>> sendCommand(String deviceSn, String cmd,
                                                               Map<String, Object> fields, long timeoutSeconds) {
        String msgId = generateMsgId();

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("cmd", cmd);
        json.put("msg_id", msgId);
        if (fields != null) {
            json.putAll(fields);
        }

        String jsonStr;
        try {
            jsonStr = objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingFutures.put(msgId, future);

        future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((r, e) -> pendingFutures.remove(msgId));

        String topic = subtopicOf(deviceSn);
        log.info("Qianyi sending command: sn={}, cmd={}, msg_id={}, topic={}", deviceSn, cmd, msgId, topic);

        try {
            mqttGateway.publishRaw(topic, jsonStr);
        } catch (Exception e) {
            pendingFutures.remove(msgId);
            future.completeExceptionally(e);
            log.error("Failed to send Qianyi command: sn={}, cmd={}", deviceSn, cmd, e);
        }

        return future;
    }

    /**
     * 平台 → 设备应答（camera_register_rsp / result_rsp 等）。
     * <p>
     * 协议格式：{@code {"cmd":"<原cmd>_rsp","status":"ok","msg_id":"<原样回传>"}}，fire-and-forget。
     */
    private void sendResponse(String deviceSn, String rspCmd, String msgId) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("cmd", rspCmd);
        json.put("status", STATUS_OK);
        if (msgId != null) {
            json.put("msg_id", msgId);
        }
        try {
            mqttGateway.publishRaw(subtopicOf(deviceSn), objectMapper.writeValueAsString(json));
        } catch (Exception e) {
            log.warn("Failed to send Qianyi response: sn={}, cmd={}", deviceSn, rspCmd, e);
        }
    }

    /**
     * 解析设备下行主题：优先注册上报的 subtopic；未注册时若已知其 qymqtt 上行主题，
     * 按同前缀约定推导下行（post→down）；最后回退默认模板 device/plate/{sn}。
     */
    private String subtopicOf(String deviceSn) {
        // 统一小写：onRawMessage 存入时已 lowerCase，但外部调用（如 REST API）传入的
        // deviceId 可能保留原始大小写（如相机上报 "15ZK231028466482"），导致查不到 key。
        String normalizedSn = deviceSn.toLowerCase();
        String subtopic = subtopicMap.get(normalizedSn);
        if (subtopic != null) {
            return subtopic;
        }
        String uplink = uplinkTopicBySn.get(normalizedSn);
        if (uplink != null && uplink.endsWith(QYMQTT_UPLINK_SUFFIX)) {
            String derived = uplink.substring(0, uplink.length() - QYMQTT_UPLINK_SUFFIX.length())
                    + QYMQTT_DOWNLINK_SUFFIX;
            log.info("Qianyi subtopic derived from uplink (not re-registered since adapter start): sn={}, subtopic={}",
                    deviceSn, derived);
            return derived;
        }
        log.warn("Qianyi subtopic unknown (device not registered since adapter start), "
                + "falling back to default template: sn={}", deviceSn);
        return String.format(DEFAULT_SUBTOPIC_TEMPLATE, deviceSn);
    }

    // ──────────────────── 状态查询 ────────────────────

    /**
     * 查询设备在线状态。
     * <p>
     * 通过心跳缓存判断：最近 90 秒内有心跳视为在线（芊熠心跳默认 30 秒间隔）。
     */
    public boolean isDeviceOnline(String deviceSn) {
        // 统一小写匹配：heartbeatRecorder 的 key 来自 onRawMessage 已 lowerCase 的 sn
        return heartbeatRecorder.isDeviceOnline(deviceSn.toLowerCase(), 90_000L);
    }

    // ──────────────────── 命令结果解析 ────────────────────

    /**
     * 从命令应答中解析执行结果。
     * <p>
     * 芊熠应答格式：{@code {"cmd":"<原cmd>_rsp","status":"ok","msg_id":"...","sn":"..."}}。
     * status 为字符串，"ok"（忽略大小写）成功，其它值为错误描述文本。
     *
     * @param reply 设备应答消息体
     * @return 结构化命令结果
     */
    public QianyiCommandResult parseCommandResult(Map<String, Object> reply) {
        if (reply == null) {
            log.warn("Qianyi command result is null");
            return QianyiCommandResult.parseFailure("null reply");
        }
        Object status = reply.get("status");
        if (status == null) {
            log.warn("Qianyi command result has no status field: {}", reply);
            return QianyiCommandResult.parseFailure(String.valueOf(reply.get("cmd")));
        }
        String statusStr = status.toString();
        if (STATUS_OK.equalsIgnoreCase(statusStr)) {
            return QianyiCommandResult.success();
        }
        return QianyiCommandResult.failure(statusStr);
    }

    // ═══════════════════════════════════════════
    // 显示屏控制（通过 rs485 透传 OLM-M1D 协议帧）
    // ═══════════════════════════════════════════

    /**
     * 实时显示文字。
     * <p>
     * 对应 OLM-M1D 0x6F 命令（SF=0），经 rs485 base64 透传到屏卡。
     *
     * @return Future，true 表示设备应答 status=ok
     */
    public CompletableFuture<Boolean> displayText(String deviceSn, String content,
                                                   DisplayDirection direction) {
        List<String> lines = toLines(content, direction);
        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(OlmM1dProtocol.DA_DEFAULT, lines.size(), lines);
        log.info("[Display] ACTION: DISPLAY_TEXT  deviceSn={}  direction={}  rows={}  cmd=rs485" +
                        "\n  content=\"{}\"",
                deviceSn, direction, lines.size(), content.replace("\n", "\\n"));
        return sendRs485(deviceSn, List.of(frame), 10)
                .thenApply(reply -> {
                    QianyiCommandResult result = parseCommandResult(reply);
                    log.info("[Display] DISPLAY_TEXT result: deviceSn={}  success={}  status={}",
                            deviceSn, result.isSuccess(), result.getStatus());
                    return result.isSuccess();
                });
    }

    /**
     * 保存显示内容到控制卡。
     * <p>
     * 对应 OLM-M1D 0x67 命令，逐行写入控制卡 Flash（低频操作）。
     *
     * @return Future，true 表示所有行均发送成功且设备应答 status=ok
     */
    public CompletableFuture<Boolean> saveDisplay(String deviceSn, String content,
                                                   DisplayDirection direction) {
        List<String> lines = toLines(content, direction);
        log.info("[Display] ACTION: SAVE_DISPLAY  deviceSn={}  direction={}  rows={}  cmd=rs485(0x67)" +
                        "\n  content=\"{}\"",
                deviceSn, direction, lines.size(), content.replace("\n", "\\n"));

        return CompletableFuture.supplyAsync(() -> {
            boolean allOk = true;
            for (int i = 0; i < lines.size(); i++) {
                byte[] frame = OlmM1dProtocol.build0x67Frame(OlmM1dProtocol.DA_DEFAULT, i, lines.get(i));
                try {
                    var reply = sendRs485(deviceSn, List.of(frame), 10).get(12, TimeUnit.SECONDS);
                    QianyiCommandResult result = parseCommandResult(reply);
                    if (!result.isSuccess()) {
                        log.warn("[Display] SAVE row {}/{} FAILED: deviceSn={}  status={}",
                                i + 1, lines.size(), deviceSn, result.getStatus());
                        allOk = false;
                    }
                    // 行间短暂间隔，避免控制卡串口缓冲区溢出
                    if (i < lines.size() - 1) {
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    log.error("[Display] SAVE row {}/{} ERROR: deviceSn={}  message={}",
                            i + 1, lines.size(), deviceSn, e.getMessage(), e);
                    allOk = false;
                }
            }
            log.info("[Display] SAVE_DISPLAY complete: deviceSn={}  allOk={}  rows={}",
                    deviceSn, allOk, lines.size());
            return allOk;
        });
    }

    // ──────────────────── 工具方法 ────────────────────

    /**
     * 生成协议 msg_id：前 13 位毫秒时间 + 后 7 位字母数字随机。
     */
    private static String generateMsgId() {
        StringBuilder sb = new StringBuilder(20);
        sb.append(System.currentTimeMillis());
        for (int i = 0; i < 7; i++) {
            sb.append(MSG_ID_CHARS.charAt(RANDOM.nextInt(MSG_ID_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 从 Topic 中提取设备序列号（fallback，sn 通常从消息体获取）。
     * <p>支持 aiot/plate/{sn} 与 /qymqtt/{sn}/qymqttpost 两种形态。</p>
     */
    private String extractSnFromTopic(String topic) {
        if (topic == null || topic.isEmpty()) {
            return null;
        }
        // /qymqtt/{sn}/qymqttpost → 去前导斜杠后 split：[, qymqtt, sn, qymqttpost]
        if (isQymqttUplink(topic)) {
            String stripped = topic.substring(QYMQTT_UPLINK_PREFIX.length());
            int slash = stripped.indexOf('/');
            return slash > 0 ? stripped.substring(0, slash) : null;
        }
        String[] parts = topic.split("/");
        // aiot/plate/{sn} → parts = [aiot, plate, sn]；取最后一段
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * utc_ts（UTC 秒级时间戳）→ 毫秒。
     */
    private static Long toEpochMillis(Object utcTs) {
        if (utcTs instanceof Number number) {
            return number.longValue() * 1000L;
        }
        return null;
    }

    /**
     * 将平台显示请求转换为行文本列表（按 \n 拆分，仅支持 HORIZONTAL）。
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
