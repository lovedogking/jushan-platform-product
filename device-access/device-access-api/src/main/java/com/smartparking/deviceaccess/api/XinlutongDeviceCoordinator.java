package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.adapter.xinlutong.XinlutongCommandResult;
import com.smartparking.deviceaccess.adapter.xinlutong.XinlutongMessageHandler;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 淇¤矾閫氳澶囧懡浠ゅ崗璋冨櫒銆? * <p>
 * 灏佽淇¤矾閫氳澶囧懡浠ゆ墽琛岀殑缂栨帓閫昏緫銆? * 鍥哄畾娴佺▼锛氭煡璁惧 鈫?MQTT 鏍￠獙 鈫?璋冪敤 Handler 鈫?绛夊緟鍥炴墽 鈫?杞崲缁撴灉銆? * <p>
 * v0.4 鏂板锛屽弬鑰?{@link ZhenshiDeviceCoordinator} 缁撴瀯銆? * 瀹炵幇 {@link DeviceCoordinator} 鎺ュ彛銆? */
@Slf4j
@Component
@RequiredArgsConstructor
public class XinlutongDeviceCoordinator implements DeviceCoordinator {

    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;
    private final MqttGateway mqttGateway;
    private final XinlutongMessageHandler handler;
    private final DeviceCommandLogService commandLogService;

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private static final int ERR_CODE_UNKNOWN_CMD = 1;
    private static final String ERR_INFO_UNKNOWN_CMD = "Unknow CMD";

    @Override
    public String getBrand() {
        return "XINLUTONG";
    }

    @Override
    public boolean isDeviceOnline(String deviceSn) {
        return handler.isDeviceOnline(deviceSn);
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?    // 鍛戒护鎵ц
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
    /**
     * 鏍℃椂鍛戒护銆?     * <p>
     * 閫氳繃 Rtd 涓嬭鎼哄甫 serverTime 瀹炵幇銆?     */
    public CompletableFuture<CommandResultDTO> syncTime(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "SYNC_TIME",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
        return handler.sendSyncTime(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    XinlutongCommandResult result = handler.parseCommandResult(reply);
                    // 淇¤矾閫?XLT-01 鍥轰欢涓嶆敮鎸佹牎鏃跺懡浠わ紝杩斿洖 errCode=1 "Unknow CMD"
                    if (!result.isSuccess()
                            && result.getErrCode() == ERR_CODE_UNKNOWN_CMD
                            && ERR_INFO_UNKNOWN_CMD.equalsIgnoreCase(result.getErrInfo())) {
                        log.warn("Xinlutong device {} firmware does not support time sync (errCode=1, Unknow CMD)", deviceId);
                        commandLogService.recordResponse(cmdLog.getId(), true, result.getErrCode(),
                                "Time sync not supported by device firmware");
                        return CommandResultDTO.builder()
                                .success(true)
                                .deviceCode(result.getErrCode())
                                .message("Time sync not supported by device firmware")
                                .build();
                    }
                    boolean success = result.isSuccess();
                    String msg = success ? "Time synced" : "Device returned error: " + result.getErrInfo();
                    commandLogService.recordResponse(cmdLog.getId(), success, result.getErrCode(), msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .deviceCode(result.getErrCode())
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to sync time for Xinlutong device: deviceId={}", deviceId, e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 寮€闂稿懡浠ゃ€?     */
    public CompletableFuture<CommandResultDTO> openGate(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "OPEN_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
        return handler.sendOpen(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    XinlutongCommandResult result = handler.parseCommandResult(reply);
                    logGateResult(device, "OPEN_GATE", result.isSuccess(), result.getErrCode());
                    commandLogService.recordResponse(cmdLog.getId(), result.isSuccess(),
                            result.getErrCode(), result.isSuccess() ? "Gate opened" : "Device returned error: " + result.getErrInfo());
                    return CommandResultDTO.builder()
                            .success(result.isSuccess())
                            .deviceCode(result.getErrCode())
                            .message(result.isSuccess() ? "Gate opened" : "Device returned error: " + result.getErrInfo())
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to open gate for Xinlutong device: deviceId={}", deviceId, e);
                    logGateResult(device, "OPEN_GATE", false, null);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 鍏抽椄鍛戒护銆?     */
    public CompletableFuture<CommandResultDTO> closeGate(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "CLOSE_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
        return handler.sendClose(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    XinlutongCommandResult result = handler.parseCommandResult(reply);
                    logGateResult(device, "CLOSE_GATE", result.isSuccess(), result.getErrCode());
                    commandLogService.recordResponse(cmdLog.getId(), result.isSuccess(),
                            result.getErrCode(), result.isSuccess() ? "Gate closed" : "Device returned error: " + result.getErrInfo());
                    return CommandResultDTO.builder()
                            .success(result.isSuccess())
                            .deviceCode(result.getErrCode())
                            .message(result.isSuccess() ? "Gate closed" : "Device returned error: " + result.getErrInfo())
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to close gate for Xinlutong device: deviceId={}", deviceId, e);
                    logGateResult(device, "CLOSE_GATE", false, null);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 锁定道闸（信路通占位实现）。
     * <p>
     * 信路通 XLT-01 当前协议版本不支持锁闸机制，抛出 UnsupportedOperationException。
     */
    public CompletableFuture<CommandResultDTO> lockGate(String deviceId, LockGateRequest req) {
        log.warn("Xinlutong XLT-01 lockGate not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Xinlutong XLT-01 does not support gate lock.");
    }

    /**
     * 解除道闸锁定（信路通占位实现）。
     * <p>
     * 信路通 XLT-01 当前协议版本不支持锁闸机制，抛出 UnsupportedOperationException。
     */
    public CompletableFuture<CommandResultDTO> unlockGate(String deviceId, UnlockGateRequest req) {
        log.warn("Xinlutong XLT-01 unlockGate not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Xinlutong XLT-01 does not support gate unlock.");
    }

    /**
     * 锁定道闸关闭（信路通占位实现）。
     * <p>
     * 信路通 XLT-01 当前协议版本不支持锁闸机制，抛出 UnsupportedOperationException。
     */
    public CompletableFuture<CommandResultDTO> lockCloseGate(String deviceId, LockGateRequest req) {
        log.warn("Xinlutong XLT-01 lockCloseGate not implemented. deviceId={}", deviceId);
        throw new UnsupportedOperationException(
                "Xinlutong XLT-01 does not support lock-close gate.");
    }

    /**
     * 澶栧洿璁惧鎺у埗銆?     */
    public CompletableFuture<PeripheralControlResult> controlPeripheral(String deviceId, PeripheralControlRequest req) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.PERIPHERAL_CONTROL)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support peripheral control");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "PERIPHERAL_CONTROL",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        String action = req.getAction();
        long startMs = System.currentTimeMillis();
        log.info("[Display] Coordinator: controlPeripheral  deviceId={}  action={}  mode={}",
                deviceId, action, req.getMode());

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
        CompletableFuture<Map<String, Object>> future;
        switch (action) {
            case "ENABLE" -> future = handler.setDisplayEnabled(device.getDeviceId(), true);
            case "DISABLE" -> future = handler.setDisplayEnabled(device.getDeviceId(), false);
            case "SET_MODE" -> {
                if (req.getMode() == null) {
                    throw new IllegalArgumentException("mode is required for SET_MODE action");
                }
                var mode = com.smartparking.deviceaccess.common.enums.DisplayMode.valueOf(req.getMode());
                future = handler.setDisplayMode(device.getDeviceId(), mode);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action
                    + ". Supported: ENABLE, DISABLE, SET_MODE");
        }

        return future.thenApply(reply -> {
                    XinlutongCommandResult result = handler.parseCommandResult(reply);
                    boolean success = result.isSuccess();
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: {} done  deviceId={}  errCode={}  elapsedMs={}",
                            action, deviceId, result.getErrCode(), elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), success,
                            result.getErrCode(), success ? "Display " + action.toLowerCase() : "Device error: " + result.getErrInfo());
                    return PeripheralControlResult.builder()
                            .success(success).action(action)
                            .message(success ? "Display " + action.toLowerCase() : "Device error: " + result.getErrInfo()).build();
                })
                .exceptionally(e -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.error("[Display] Coordinator: FAILED  deviceId={}  action={}  elapsedMs={}  errorType={}",
                            deviceId, action, elapsed, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return PeripheralControlResult.builder()
                            .success(false).action(action)
                            .message("Command failed: " + e.getMessage()).build();
                });
    }

    /**
     * 鏄剧ず灞忛厤缃紙闊抽噺銆佷寒搴︺€佹柟鍚戙€佹椂闂村悓姝ワ級銆?     */
    public CompletableFuture<DisplayResult> configDisplay(String deviceId, DisplayConfigRequest req) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.DISPLAY_CONFIG)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support display config");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "DISPLAY_CONFIG",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        String configType = req.getConfigType();
        long startMs = System.currentTimeMillis();
        log.info("[Display] Coordinator: configDisplay  deviceId={}  configType={}  intValue={}",
                deviceId, configType, req.getIntValue());

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenCompose/thenApply 寮傛閾惧紡澶勭悊
        CompletableFuture<Map<String, Object>> future;
        switch (configType) {
            case "VOLUME" -> {
                int volume = req.getIntValue() != null ? req.getIntValue() : 50;
                future = handler.setVolume(device.getDeviceId(), volume);
            }
            case "BRIGHTNESS" -> {
                int brightness = req.getIntValue() != null ? req.getIntValue() : 80;
                future = handler.setDisplayEnabled(device.getDeviceId(), brightness > 10);
            }
            case "DIRECTION" -> {
                int dir = req.getIntValue() != null ? req.getIntValue() : 0;
                future = handler.setDisplayDirection(device.getDeviceId(), dir);
            }
            case "TIME_SYNC" -> future = handler.syncDisplayTime(device.getDeviceId());
            default -> throw new IllegalArgumentException("Unknown configType: " + configType
                    + ". Supported: VOLUME, BRIGHTNESS, DIRECTION, TIME_SYNC");
        }

        return future.thenApply(reply -> {
                    XinlutongCommandResult result = handler.parseCommandResult(reply);
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: configDisplay done  deviceId={}  configType={}  errCode={}  elapsedMs={}",
                            deviceId, configType, result.getErrCode(), elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), result.isSuccess(), result.getErrCode(),
                            result.isSuccess() ? "Display config " + configType + " applied" : "Device error: " + result.getErrInfo());
                    return DisplayResult.builder().success(result.isSuccess()).build();
                })
                .exceptionally(e -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.error("[Display] Coordinator: configDisplay FAILED  deviceId={}  configType={}  elapsedMs={}  errorType={}",
                            deviceId, configType, elapsed, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return DisplayResult.builder()
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    /**
     * 璇煶鎺у埗锛堟挱鏀俱€佸仠姝級銆?     */
    public CompletableFuture<VoiceControlResult> controlVoice(String deviceId, VoiceControlRequest req) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.VOICE_CONTROL)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support voice control");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "VOICE_CONTROL",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        String action = req.getAction();
        long startMs = System.currentTimeMillis();
        log.info("[Display] Coordinator: controlVoice  deviceId={}  action={}  voiceText={}",
                deviceId, action, req.getVoiceText());

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
        CompletableFuture<Map<String, Object>> future;
        switch (action) {
            case "PLAY" -> {
                if (req.getVoiceText() == null || req.getVoiceText().isEmpty()) {
                    throw new IllegalArgumentException("voiceText is required for PLAY action");
                }
                int opt = req.getOpt() != null ? req.getOpt() : 0x01;
                future = handler.playVoice(device.getDeviceId(), opt, req.getVoiceText());
            }
            case "STOP" -> future = handler.stopVoice(device.getDeviceId());
            default -> throw new IllegalArgumentException("Unknown action: " + action
                    + ". Supported: PLAY, STOP");
        }

        return future.thenApply(reply -> {
                    XinlutongCommandResult result = handler.parseCommandResult(reply);
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: controlVoice done  deviceId={}  action={}  errCode={}  elapsedMs={}",
                            deviceId, action, result.getErrCode(), elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), result.isSuccess(), result.getErrCode(),
                            result.isSuccess() ? "Voice " + action + " executed" : "Device error: " + result.getErrInfo());
                    return VoiceControlResult.builder()
                            .success(result.isSuccess())
                            .action(action)
                            .voiceText(req.getVoiceText())
                            .message(result.isSuccess() ? "Voice " + action + " executed" : "Device error: " + result.getErrInfo())
                            .build();
                })
                .exceptionally(e -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.error("[Display] Coordinator: controlVoice FAILED  deviceId={}  action={}  elapsedMs={}  errorType={}",
                            deviceId, action, elapsed, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return VoiceControlResult.builder()
                            .success(false)
                            .action(action)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    /**
     * 澧炲己鐗堝疄鏃舵樉绀烘枃瀛椼€?     */
    public CompletableFuture<DisplayResult> displayTextEnhanced(String deviceId, String content, DisplayDirection direction,
                                                OlmM1dProtocol.FontType font, List<int[]> color,
                                                Integer voiceId, String voiceVariable) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.DISPLAY_ENHANCED)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support enhanced display");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "DISPLAY_TEXT_ENHANCED",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        long startMs = System.currentTimeMillis();
        log.info("[Display] Coordinator: displayTextEnhanced  deviceId={}  direction={}  font={}  voiceId={}  content=\"{}\"",
                deviceId, direction, font != null ? font.name() : "DEFAULT",
                voiceId != null ? voiceId : "NONE", content.replace("\n", "\\n"));

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
        return handler.displayTextEnhanced(device.getDeviceId(), content, direction,
                        font, color, voiceId, voiceVariable)
                .thenApply(ok -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: displayTextEnhanced done  deviceId={}  handlerResult={}  elapsedMs={}",
                            deviceId, ok, elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), ok != null && ok, null,
                            ok != null && ok ? "Enhanced display text sent" : "Enhanced display text failed");
                    return DisplayResult.builder().success(ok != null && ok).build();
                })
                .exceptionally(e -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.error("[Display] Coordinator: displayTextEnhanced FAILED  deviceId={}  elapsedMs={}  errorType={}",
                            deviceId, elapsed, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return DisplayResult.builder()
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    /**
     * 瀹炴椂鏄剧ず鏂囧瓧銆?     */
    public CompletableFuture<DisplayResult> displayText(String deviceId, String content, DisplayDirection direction, String colorName) {
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

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
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
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.error("[Display] Coordinator: displayText FAILED  deviceId={}  elapsedMs={}  errorType={}",
                            deviceId, elapsed, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return DisplayResult.builder()
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    /**
     * 淇濆瓨鏄剧ず鍐呭銆?     */
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

        // 淇锛氬垹闄?future.get() 闃诲璋冪敤锛屾敼涓?thenApply/exceptionally 寮傛閾惧紡澶勭悊
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
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.error("[Display] Coordinator: saveDisplay FAILED  deviceId={}  elapsedMs={}  errorType={}",
                            deviceId, elapsed, e.getClass().getName(), e);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return DisplayResult.builder()
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                });
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?    // 鍐呴儴鏂规硶
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
    /**
     * 主动抓拍（v0.7 暂不支持）。
     */
    @Override
    public CompletableFuture<CaptureResultDTO> capture(String deviceId) {
        log.warn("{} capture not implemented. deviceId={}", getBrand(), deviceId);
        throw new UnsupportedOperationException(
                getBrand() + " camera does not support capture yet.");
    }

    private void ensureMqttConnected() {
        if (!mqttGateway.isConnected()) {
            throw new MqttConnectionException("MQTT is not connected, cannot send command");
        }
    }

    /**
     * 璁板綍缁熶竴鏍煎紡鐨勯亾闂稿懡浠ゆ墽琛屾棩蹇椼€?     */
    private void logGateResult(Device device, String command, boolean success, Integer deviceCode) {
        String plateNo = handler.getLastPlate(device.getDeviceId());
        log.info("Gate command result: deviceSn={}, brand={}, command={}, plateNo={}, platformDeviceId={}, tenantId={}, parkingLotId={}, laneId={}, success={}, deviceCode={}, message={}",
                device.getDeviceId(),
                "XINLUTONG",
                command,
                plateNo,
                device.getPlatformDeviceId(),
                device.getTenantId(),
                device.getParkingLotId(),
                device.getLaneId(),
                success,
                deviceCode,
                success ? ("OPEN_GATE".equals(command) ? "Gate opened" : "Gate closed") : "Command failed");
    }
}
