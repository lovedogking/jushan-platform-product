package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.adapter.qianyi.QianyiCommandResult;
import com.smartparking.deviceaccess.adapter.qianyi.QianyiMessageHandler;
import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.api.dto.CaptureResultDTO;
import com.smartparking.deviceaccess.api.dto.CommandResultDTO;
import com.smartparking.deviceaccess.api.dto.DisplayConfigRequest;
import com.smartparking.deviceaccess.api.dto.DisplayResult;
import com.smartparking.deviceaccess.api.dto.LockGateRequest;
import com.smartparking.deviceaccess.api.dto.PeripheralControlRequest;
import com.smartparking.deviceaccess.api.dto.PeripheralControlResult;
import com.smartparking.deviceaccess.api.dto.UnlockGateRequest;
import com.smartparking.deviceaccess.api.dto.VoiceControlRequest;
import com.smartparking.deviceaccess.api.dto.VoiceControlResult;
import com.smartparking.deviceaccess.api.image.ImageStorageService;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceCommandLog;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.enums.DeviceCapability;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import com.smartparking.deviceaccess.common.exception.MqttConnectionException;
import com.smartparking.deviceaccess.mqtt.MqttGateway;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 芊熠设备命令协调器。
 * <p>
 * 封装芊熠设备命令执行的编排逻辑。
 * 固定流程：查设备 → 能力校验 → MQTT 校验 → 调用 Handler → 等待应答 → 转换结果。
 * <p>
 * 协议命令映射（《芊熠智能：MQTT通信协议-基线260401》）：
 * <ul>
 *   <li>开闸 → iooutput ionum=0 action=on；关闸 → iooutput ionum=1 action=on（脉冲触发）</li>
 *   <li>常开 → barrierKeepOpen isKeepOpen=1；取消常开 → isKeepOpen=0 + 补发 iooutput off 落闸</li>
 *   <li>校时 → syncSysTime</li>
 *   <li>屏显 → rs485 透传 OLM-M1D 帧</li>
 * </ul>
 * <p>
 * v0.5 新增，实现 {@link DeviceCoordinator} 接口，结构参照 {@link ZhenshiDeviceCoordinator}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QianyiDeviceCoordinator implements DeviceCoordinator {

    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;
    private final MqttGateway mqttGateway;
    private final QianyiMessageHandler handler;
    private final DeviceCommandLogService commandLogService;
    private final ImageStorageService imageStorageService;

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    @Override
    public String getBrand() {
        return "QIANYI";
    }

    @Override
    public boolean isDeviceOnline(String deviceSn) {
        return handler.isDeviceOnline(deviceSn);
    }

    // ═══════════════════════════════════════════
    // 命令执行
    // ═══════════════════════════════════════════

    /**
     * 校时命令。
     * <p>
     * syncSysTime: time_zone=4 (GMT+8)，time_stamp 为 UTC 秒字符串。
     */
    public CompletableFuture<CommandResultDTO> syncTime(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "SYNC_TIME",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        return handler.sendSyncSysTime(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    QianyiCommandResult result = handler.parseCommandResult(reply);
                    boolean success = result.isSuccess();
                    String msg = success ? "Time synced" : "Device returned error: " + result.getStatus();
                    commandLogService.recordResponse(cmdLog.getId(), success, null, msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to sync time for Qianyi device: deviceId={}", deviceId, e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 开闸命令。
     * <p>
     * iooutput: ionum=0, action=on（脉冲触发，参照臻识 IO0 开闸）。
     */
    public CompletableFuture<CommandResultDTO> openGate(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "OPEN_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        return handler.sendOpenGate(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    QianyiCommandResult result = handler.parseCommandResult(reply);
                    boolean success = result.isSuccess();
                    String msg = success ? "Gate opened" : "Device returned error: " + result.getStatus();
                    logGateResult(device, "OPEN_GATE", success);
                    commandLogService.recordResponse(cmdLog.getId(), success, null, msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to open gate for Qianyi device: deviceId={}", deviceId, e);
                    logGateResult(device, "OPEN_GATE", false);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 关闸命令。
     * <p>
     * iooutput: ionum=2, action=on（脉冲触发，关闸继电器）。
     */
    public CompletableFuture<CommandResultDTO> closeGate(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "CLOSE_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        return handler.sendCloseGate(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    QianyiCommandResult result = handler.parseCommandResult(reply);
                    boolean success = result.isSuccess();
                    String msg = success ? "Gate closed" : "Device returned error: " + result.getStatus();
                    logGateResult(device, "CLOSE_GATE", success);
                    commandLogService.recordResponse(cmdLog.getId(), success, null, msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to close gate for Qianyi device: deviceId={}", deviceId, e);
                    logGateResult(device, "CLOSE_GATE", false);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 锁定道闸（常开）。
     * <p>
     * barrierKeepOpen: isKeepOpen=1 保持常开。
     */
    public CompletableFuture<CommandResultDTO> lockGate(String deviceId, LockGateRequest req) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.LOCK_OPEN_GATE)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support LOCK_OPEN_GATE");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "LOCK_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        log.info("[Gate] Coordinator: lockGate(barrierKeepOpen=1)  deviceId={}", deviceId);

        return handler.sendBarrierKeepOpen(device.getDeviceId(), 1, DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    QianyiCommandResult result = handler.parseCommandResult(reply);
                    boolean success = result.isSuccess();
                    String msg = success ? "Gate locked open" : "Device returned error: " + result.getStatus();
                    logGateResult(device, "LOCK_GATE", success);
                    commandLogService.recordResponse(cmdLog.getId(), success, null, msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to lock gate for Qianyi device: deviceId={}", deviceId, e);
                    logGateResult(device, "LOCK_GATE", false);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 解除道闸锁定（取消常开 + 补发落闸）。
     * <p>
     * 两步流程（协议注明：从常开切回后如需落闸，要另外发落闸指令）：
     * <ol>
     *   <li>barrierKeepOpen isKeepOpen=0 取消常开</li>
     *   <li>iooutput action=off 落闸（best-effort，失败不影响取消常开结果）</li>
     * </ol>
     */
    public CompletableFuture<CommandResultDTO> unlockGate(String deviceId, UnlockGateRequest req) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.LOCK_OPEN_GATE)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support LOCK_OPEN_GATE");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "UNLOCK_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        log.info("[Gate] Coordinator: unlockGate(barrierKeepOpen=0)  deviceId={}", deviceId);

        return handler.sendBarrierKeepOpen(device.getDeviceId(), 0, DEFAULT_TIMEOUT_SECONDS)
                .thenCompose(reply -> {
                    QianyiCommandResult result = handler.parseCommandResult(reply);

                    if (!result.isSuccess()) {
                        String msg = "Device returned error: " + result.getStatus();
                        logGateResult(device, "UNLOCK_GATE", false);
                        commandLogService.recordResponse(cmdLog.getId(), false, null, msg);
                        return CompletableFuture.completedFuture(
                                CommandResultDTO.builder()
                                        .success(false)
                                        .message(msg)
                                        .build());
                    }

                    // 取消常开成功 → best-effort 落闸
                    log.info("[Gate] Coordinator: unlockGate keep-open cancelled, closing barrier via sendCloseGate  deviceId={}", deviceId);
                    return handler.sendCloseGate(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                            .handle((closeReply, closeError) -> {
                                String msg = "Gate unlocked";
                                if (closeError != null) {
                                    log.warn("[Gate] Coordinator: unlockGate close barrier failed  deviceId={}  error={}",
                                            deviceId, closeError.getMessage());
                                    msg += " (close barrier warning: " + closeError.getMessage() + ")";
                                } else {
                                    QianyiCommandResult closeResult = handler.parseCommandResult(closeReply);
                                    if (!closeResult.isSuccess()) {
                                        log.warn("[Gate] Coordinator: unlockGate close barrier returned non-ok  deviceId={}  status={}",
                                                deviceId, closeResult.getStatus());
                                        msg += " (close barrier warning: " + closeResult.getStatus() + ")";
                                    } else {
                                        log.info("[Gate] Coordinator: unlockGate barrier closed  deviceId={}", deviceId);
                                    }
                                }
                                logGateResult(device, "UNLOCK_GATE", true);
                                commandLogService.recordResponse(cmdLog.getId(), true, null, msg);
                                return CommandResultDTO.builder()
                                        .success(true)
                                        .message(msg)
                                        .build();
                            });
                })
                .exceptionally(e -> {
                    log.error("Failed to unlock gate for Qianyi device: deviceId={}", deviceId, e);
                    logGateResult(device, "UNLOCK_GATE", false);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 实时显示文字。
     */
    public CompletableFuture<DisplayResult> displayText(String deviceId, String content, DisplayDirection direction) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.DISPLAY_TEXT)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support display text");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "DISPLAY_TEXT",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        long startMs = System.currentTimeMillis();
        log.info("[Display] Coordinator: displayText  deviceId={}  direction={}  content=\"{}\"",
                deviceId, direction, content.replace("\n", "\\n"));

        return handler.displayText(device.getDeviceId(), content, direction)
                .thenApply(ok -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: displayText done  deviceId={}  handlerResult={}  elapsedMs={}",
                            deviceId, ok, elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), ok != null && ok, null,
                            ok != null && ok ? "Display text sent" : "Display text failed");
                    return DisplayResult.builder().success(ok != null && ok).build();
                })
                .exceptionally(e -> {
                    log.error("[Display] Coordinator: displayText FAILED  deviceId={}  errorType={}",
                            deviceId, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return DisplayResult.builder()
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    /**
     * 保存显示内容。
     */
    public CompletableFuture<DisplayResult> saveDisplay(String deviceId, String content, DisplayDirection direction) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.DISPLAY_SAVE)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support display save");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "DISPLAY_SAVE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        long startMs = System.currentTimeMillis();
        log.info("[Display] Coordinator: saveDisplay  deviceId={}  direction={}  content=\"{}\"",
                deviceId, direction, content.replace("\n", "\\n"));

        return handler.saveDisplay(device.getDeviceId(), content, direction)
                .thenApply(ok -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: saveDisplay done  deviceId={}  handlerResult={}  elapsedMs={}",
                            deviceId, ok, elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), ok != null && ok, null,
                            ok != null && ok ? "Display saved" : "Display save failed");
                    return DisplayResult.builder().success(ok != null && ok).build();
                })
                .exceptionally(e -> {
                    log.error("[Display] Coordinator: saveDisplay FAILED  deviceId={}  errorType={}",
                            deviceId, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return DisplayResult.builder()
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    // ═══════════════════════════════════════════
    // 一期不支持的能力（占位，照信路通 lockGate 模式）
    // ═══════════════════════════════════════════

    /**
     * 外围设备控制（一期不支持）。
     */
    public CompletableFuture<PeripheralControlResult> controlPeripheral(String deviceId, PeripheralControlRequest req) {
        log.warn("Qianyi controlPeripheral not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Qianyi camera does not support peripheral control yet.");
    }

    /**
     * 显示屏配置（一期不支持）。
     */
    public CompletableFuture<DisplayResult> configDisplay(String deviceId, DisplayConfigRequest req) {
        log.warn("Qianyi configDisplay not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Qianyi camera does not support display config yet.");
    }

    /**
     * 语音控制（一期不支持）。
     */
    public CompletableFuture<VoiceControlResult> controlVoice(String deviceId, VoiceControlRequest req) {
        log.warn("Qianyi controlVoice not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Qianyi camera does not support voice control yet.");
    }

    /**
     * 增强版实时显示文字（一期不支持）。
     */
    public CompletableFuture<DisplayResult> displayTextEnhanced(String deviceId, String content, DisplayDirection direction,
                                                                 OlmM1dProtocol.FontType font, List<int[]> color,
                                                                 Integer voiceId, String voiceVariable) {
        log.warn("Qianyi displayTextEnhanced not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Qianyi camera does not support enhanced display yet.");
    }

    /**
     * 主动抓拍。
     * <p>
     * 优先使用通用 {@code snapshot} 命令获取全景图；失败或无图时回退到
     * 车牌相机专用 {@code tarkphoto} 命令获取车牌特写图。
     * 图片保存到本地存储后返回可访问 URL。
     */
    @Override
    public CompletableFuture<CaptureResultDTO> capture(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "CAPTURE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        long startMs = System.currentTimeMillis();
        log.info("[Capture] Coordinator: capture  deviceId={}", deviceId);

        return trySnapshot(device)
                .thenCompose(snapshotResult -> {
                    if (snapshotResult != null && snapshotResult.isSuccess()) {
                        long elapsed = System.currentTimeMillis() - startMs;
                        log.info("[Capture] Coordinator: snapshot ok  deviceId={}  imageUrl={}  elapsedMs={}",
                                deviceId, snapshotResult.getImageUrl(), elapsed);
                        commandLogService.recordResponse(cmdLog.getId(), true, null, "snapshot ok");
                        return CompletableFuture.completedFuture(snapshotResult);
                    }
                    String reason = snapshotResult != null ? snapshotResult.getMessage() : "null";
                    log.info("[Capture] Coordinator: snapshot failed/empty, trying tarkphoto  deviceId={}  reason={}",
                            deviceId, reason);
                    return tryTarkphoto(device);
                })
                .thenCompose(tarkphotoResult -> {
                    if (tarkphotoResult != null && tarkphotoResult.isSuccess()) {
                        long elapsed = System.currentTimeMillis() - startMs;
                        log.info("[Capture] Coordinator: tarkphoto ok  deviceId={}  imageUrl={}  elapsedMs={}",
                                deviceId, tarkphotoResult.getImageUrl(), elapsed);
                        commandLogService.recordResponse(cmdLog.getId(), true, null, "tarkphoto ok");
                        return CompletableFuture.completedFuture(tarkphotoResult);
                    }
                    String reason = tarkphotoResult != null ? tarkphotoResult.getMessage() : "null";
                    String msg = "snapshot failed, tarkphoto also failed: " + reason;
                    log.warn("[Capture] Coordinator: capture failed  deviceId={}  reason={}", deviceId, msg);
                    commandLogService.recordResponse(cmdLog.getId(), false, null, msg);
                    return CompletableFuture.completedFuture(
                            CaptureResultDTO.builder().success(false).message(msg).build());
                })
                .exceptionally(e -> {
                    log.error("[Capture] Coordinator: capture FAILED  deviceId={}", deviceId, e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CaptureResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    // ═══════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════

    private void ensureMqttConnected() {
        if (!mqttGateway.isConnected()) {
            throw new MqttConnectionException("MQTT is not connected, cannot send command");
        }
    }

    /**
     * 记录统一格式的道闸命令执行日志。
     * <p>
     * 芊熠应答无数字错误码，deviceCode 恒为 null。
     */
    private void logGateResult(Device device, String command, boolean success) {
        String plateNo = handler.getLastPlate(device.getDeviceId());
        log.info("Gate command result: deviceSn={}, brand={}, command={}, plateNo={}, platformDeviceId={}, tenantId={}, parkingLotId={}, laneId={}, success={}, message={}",
                device.getDeviceId(),
                "QIANYI",
                command,
                plateNo,
                device.getPlatformDeviceId(),
                device.getTenantId(),
                device.getParkingLotId(),
                device.getLaneId(),
                success,
                success ? ("OPEN_GATE".equals(command) ? "Gate opened" : "Gate closed") : "Command failed");
    }

    /**
     * 尝试 {@code snapshot} 通用抓拍命令。
     *
     * @param device 设备
     * @return 抓拍结果；失败时 success=false
     */
    private CompletableFuture<CaptureResultDTO> trySnapshot(Device device) {
        String deviceSn = device.getDeviceId();
        return handler.sendSnapshot(deviceSn, DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    String picture = (String) reply.get("picture");
                    if (picture != null && !picture.isBlank()) {
                        return saveBase64Image(deviceSn, picture, "snapshot");
                    }
                    return CaptureResultDTO.builder()
                            .success(false)
                            .message("snapshot response no picture")
                            .build();
                })
                .exceptionally(e -> {
                    log.warn("[Capture] Coordinator: snapshot error  deviceId={}  error={}",
                            deviceSn, e.getMessage());
                    return CaptureResultDTO.builder()
                            .success(false)
                            .message("snapshot error: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 尝试 {@code tarkphoto} 车牌相机抓拍命令。
     *
     * @param device 设备
     * @return 抓拍结果；失败时 success=false
     */
    private CompletableFuture<CaptureResultDTO> tryTarkphoto(Device device) {
        String deviceSn = device.getDeviceId();
        return handler.sendTarkphoto(deviceSn, DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    String platePic = (String) reply.get("plate_pic");
                    if (platePic != null && !platePic.isBlank()) {
                        return saveBase64Image(deviceSn, platePic, "tarkphoto");
                    }
                    return CaptureResultDTO.builder()
                            .success(false)
                            .message("tarkphoto response no plate_pic")
                            .build();
                })
                .exceptionally(e -> {
                    log.warn("[Capture] Coordinator: tarkphoto error  deviceId={}  error={}",
                            deviceSn, e.getMessage());
                    return CaptureResultDTO.builder()
                            .success(false)
                            .message("tarkphoto error: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 将 Base64 图片数据保存到本地存储并构造对外 URL。
     *
     * @param deviceSn  设备序列号
     * @param base64    Base64 编码图片
     * @param sourceCmd 来源命令（snapshot / tarkphoto）
     * @return 抓拍结果
     */
    private CaptureResultDTO saveBase64Image(String deviceSn, String base64, String sourceCmd) {
        try {
            long epochSeconds = java.time.Instant.now().getEpochSecond();
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            imageStorageService.saveBytes(deviceSn, epochSeconds, bytes, null);
            String imageUrl = imageStorageService.buildFullImageUrl(deviceSn, epochSeconds);
            return CaptureResultDTO.builder()
                    .success(true)
                    .imageUrl(imageUrl)
                    .message(sourceCmd + " ok")
                    .build();
        } catch (Exception e) {
            log.error("[Capture] Coordinator: save image failed  deviceSn={}  sourceCmd={}", deviceSn, sourceCmd, e);
            return CaptureResultDTO.builder()
                    .success(false)
                    .message("save image failed: " + e.getMessage())
                    .build();
        }
    }
}
