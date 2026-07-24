package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler;
import com.smartparking.deviceaccess.api.image.ImageStorageService;
import com.smartparking.deviceaccess.api.dto.*;
import com.smartparking.deviceaccess.common.dto.mqtt.MqttMessage;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 臻识设备命令协调器。
 * <p>
 * 封装设备命令执行的编排逻辑，降低 api 层对具体厂商 Handler 的直接依赖。
 * <p>
 * 固定流程：查设备 → 能力校验 → MQTT 校验 → 调用 Handler → 等待回复 → 转换结果。
 * <p>
 * v0.4: 实现 {@link DeviceCoordinator} 接口，所有命令方法改为异步返回 CompletableFuture，
 * 删除 future.get() 阻塞调用，改为 thenApply/exceptionally 链式处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhenshiDeviceCoordinator implements DeviceCoordinator {

    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;
    private final MqttGateway mqttGateway;
    private final ZhenshiMessageHandler handler;
    private final DeviceCommandLogService commandLogService;
    private final ImageStorageService imageStorageService;

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    @Override
    public String getBrand() {
        return "ZHENSHI";
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        return handler.sendSyncTime(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    boolean success = reply.getCode() != null && reply.getCode() == 200;
                    String msg = success ? "Time synced" : "Device returned error code: " + reply.getCode();
                    commandLogService.recordResponse(cmdLog.getId(), success, reply.getCode(), msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .deviceCode(reply.getCode())
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to sync time: deviceId={}", deviceId, e);
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
     * gpio_out: IO0, value=2（先通后断脉冲）, delay=1500ms。
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
                    boolean success = reply.getCode() != null && reply.getCode() == 200;
                    String msg = success ? "Gate opened" : "Device returned error code: " + reply.getCode();
                    logGateResult(device, "OPEN_GATE", success, reply.getCode());
                    commandLogService.recordResponse(cmdLog.getId(), success, reply.getCode(), msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .deviceCode(reply.getCode())
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to open gate: deviceId={}", deviceId, e);
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
     * 关闸命令。
     * <p>
     * gpio_out: IO1, value=2（先通后断脉冲）, delay=3000ms。
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
                    boolean success = reply.getCode() != null && reply.getCode() == 200;
                    String msg = success ? "Gate closed" : "Device returned error code: " + reply.getCode();
                    logGateResult(device, "CLOSE_GATE", success, reply.getCode());
                    commandLogService.recordResponse(cmdLog.getId(), success, reply.getCode(), msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .deviceCode(reply.getCode())
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to close gate: deviceId={}", deviceId, e);
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
     * 锁定道闸（继电器强制吸合保持开启）。
     * <p>
     * 使用 set_io_lock_status: ioout=0 (IO0), status=1 (高电平锁定)。
     * IO0 继电器持续吸合，道闸保持开启。硬件仅支持锁定开闸方向。
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

        int io = 0; // IO0 = 开闸继电器
        log.info("[Gate] Coordinator: lockGate  deviceId={}  io={}", deviceId, io);

        return handler.sendLockGate(device.getDeviceId(), io, DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    boolean success = reply.getCode() != null && reply.getCode() == 200;
                    String msg = success ? "Gate locked open" : "Device returned error code: " + reply.getCode();
                    logGateResult(device, "LOCK_GATE", success, reply.getCode());
                    commandLogService.recordResponse(cmdLog.getId(), success, reply.getCode(), msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .deviceCode(reply.getCode())
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to lock gate: deviceId={}", deviceId, e);
                    logGateResult(device, "LOCK_GATE", false, null);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 解除道闸锁定（解锁 IO0 + gpio_out 开闸脉冲复位状态机）。
     * <p>
     * 两步流程：
     * <ol>
     *   <li>发送 set_io_lock_status io=0 status=0 解除 IO0 锁定</li>
     *   <li>发送 gpio_out IO0 开闸脉冲，重置设备内部道闸状态机，恢复 gpio_out 命令的正常响应</li>
     * </ol>
     * <p>
     * 注意：本设备固件 bv=16771 不支持 gate_direct_open，使用 gpio_out 开闸脉冲替代。
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

        int io = 0; // IO0 = 开闸继电器
        log.info("[Gate] Coordinator: unlockGate  deviceId={}  io={}", deviceId, io);

        // 两步流程：
        // 1. 解除 IO 锁定（set_io_lock_status status=0）
        // 2. 重置设备内部道闸状态机（gate_direct_open），恢复 gpio_out 命令的正常响应
        //    步骤 2 为 best-effort，失败不影响解锁结果
        return handler.sendUnlockGate(device.getDeviceId(), io, DEFAULT_TIMEOUT_SECONDS)
                .thenCompose(reply -> {
                    boolean unlockSuccess = reply.getCode() != null && reply.getCode() == 200;

                    if (!unlockSuccess) {
                        // IO 解锁失败 → 直接返回失败
                        String msg = "Device returned error code: " + reply.getCode();
                        logGateResult(device, "UNLOCK_GATE", false, reply.getCode());
                        commandLogService.recordResponse(cmdLog.getId(), false, reply.getCode(), msg);
                        return CompletableFuture.completedFuture(
                                CommandResultDTO.builder()
                                        .success(false)
                                        .deviceCode(reply.getCode())
                                        .message(msg)
                                        .build());
                    }

                    // IO 解锁成功 → best-effort 重置状态机
                    // gate_direct_open 不被本设备固件(bv=16771)支持，改用 gpio_out 开闸脉冲复位
                    log.info("[Gate] Coordinator: unlockGate IO unlocked, resetting state machine via gpio_out(openGate)  deviceId={}", deviceId);
                    return handler.sendOpenGate(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                            .handle((resetReply, resetError) -> {
                                String msg = "Gate unlocked";
                                if (resetError != null) {
                                    log.warn("[Gate] Coordinator: unlockGate state reset(gpio_out) failed, close gate may not respond  deviceId={}  error={}",
                                            deviceId, resetError.getMessage());
                                    msg += " (state reset warning: " + resetError.getMessage() + ")";
                                } else if (resetReply.getCode() == null || resetReply.getCode() != 200) {
                                    log.warn("[Gate] Coordinator: unlockGate state reset(gpio_out) returned non-200  deviceId={}  code={}",
                                            deviceId, resetReply.getCode());
                                    msg += " (state reset warning: code=" + resetReply.getCode() + ")";
                                } else {
                                    log.info("[Gate] Coordinator: unlockGate state machine reset via gpio_out successful  deviceId={}", deviceId);
                                }
                                logGateResult(device, "UNLOCK_GATE", true, reply.getCode());
                                commandLogService.recordResponse(cmdLog.getId(), true, reply.getCode(), msg);
                                return CommandResultDTO.builder()
                                        .success(true)
                                        .deviceCode(reply.getCode())
                                        .message(msg)
                                        .build();
                            });
                })
                .exceptionally(e -> {
                    log.error("Failed to unlock gate: deviceId={}", deviceId, e);
                    logGateResult(device, "UNLOCK_GATE", false, null);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 锁定道闸关闭（继电器强制低电平保持关闭）。
     * <p>
     * 使用 set_io_lock_status: ioout=0 (IO0), status=2 (低电平锁定)。
     * 与 lockGate（status=1 高电平锁定开闸）对称。
     */
    public CompletableFuture<CommandResultDTO> lockCloseGate(String deviceId, LockGateRequest req) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        if (!productRegistry.hasCapability(device.getProductId(), DeviceCapability.LOCK_CLOSE_GATE)) {
            throw new UnsupportedOperationException(
                    "Device " + deviceId + " does not support LOCK_CLOSE_GATE");
        }
        DeviceProduct product = productRegistry.getById(device.getProductId());
        DeviceCommandLog cmdLog = commandLogService.recordRequest(
                deviceId, product.getBrand(), "LOCK_CLOSE_GATE",
                handler.getLastPlate(device.getDeviceId()),
                device.getPlatformDeviceId(), device.getTenantId(),
                device.getParkingLotId(), device.getLaneId());

        ensureMqttConnected();

        int io = 0; // IO0 = 开闸继电器（低电平锁定=关闸方向）
        log.info("[Gate] Coordinator: lockCloseGate  deviceId={}  io={}", deviceId, io);

        return handler.sendLockCloseGate(device.getDeviceId(), io, DEFAULT_TIMEOUT_SECONDS)
                .thenApply(reply -> {
                    boolean success = reply.getCode() != null && reply.getCode() == 200;
                    String msg = success ? "Gate locked close" : "Device returned error code: " + reply.getCode();
                    logGateResult(device, "LOCK_CLOSE_GATE", success, reply.getCode());
                    commandLogService.recordResponse(cmdLog.getId(), success, reply.getCode(), msg);
                    return CommandResultDTO.builder()
                            .success(success)
                            .deviceCode(reply.getCode())
                            .message(msg)
                            .build();
                })
                .exceptionally(e -> {
                    log.error("Failed to lock close gate: deviceId={}", deviceId, e);
                    logGateResult(device, "LOCK_CLOSE_GATE", false, null);
                    commandLogService.recordResponse(cmdLog.getId(), false, null,
                            "Command failed: " + e.getMessage());
                    return CommandResultDTO.builder()
                            .success(false)
                            .message("Command failed: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 外围设备控制。
     */
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        CompletableFuture<MqttMessage> future;
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
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: {} done  deviceId={}  mqttCode={}  elapsedMs={}",
                            action, deviceId, reply.getCode(), elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), true, reply.getCode(),
                            "Display " + action.toLowerCase());
                    return PeripheralControlResult.builder()
                            .success(true).action(action)
                            .message("Display " + action.toLowerCase()).build();
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
     * 显示屏配置（音量、亮度、方向、时间同步）。
     */
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        CompletableFuture<MqttMessage> future;
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
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: configDisplay done  deviceId={}  configType={}  mqttCode={}  elapsedMs={}",
                            deviceId, configType, reply.getCode(), elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), true, reply.getCode(),
                            "Display config " + configType + " applied");
                    return DisplayResult.builder().success(true).build();
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
     * 语音控制（播放、停止）。
     */
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        CompletableFuture<MqttMessage> future;
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
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: controlVoice done  deviceId={}  action={}  mqttCode={}  elapsedMs={}",
                            deviceId, action, reply.getCode(), elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), true, reply.getCode(),
                            "Voice " + action + " executed");
                    return VoiceControlResult.builder()
                            .success(true)
                            .action(action)
                            .voiceText(req.getVoiceText())
                            .message("Voice " + action + " executed")
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
     * 增强版实时显示文字。
     */
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        return handler.displayTextEnhanced(device.getDeviceId(), content, direction,
                        font, color, voiceId, voiceVariable)
                .thenApply(ok -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: displayTextEnhanced done  deviceId={}  handlerResult={}  elapsedMs={}",
                            deviceId, ok, elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), true, null, "Enhanced display text sent");
                    return DisplayResult.builder().success(true).build();
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        return handler.displayText(device.getDeviceId(), content, direction)
                .thenApply(ok -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: displayText done  deviceId={}  handlerResult={}  elapsedMs={}",
                            deviceId, ok, elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), true, null, "Display text sent");
                    return DisplayResult.builder().success(true).build();
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

        // 修复：删除 future.get() 阻塞调用，改为 thenApply/exceptionally 异步链式处理
        return handler.saveDisplay(device.getDeviceId(), content, direction)
                .thenApply(ok -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[Display] Coordinator: saveDisplay done  deviceId={}  handlerResult={}  elapsedMs={}",
                            deviceId, ok, elapsed);
                    commandLogService.recordResponse(cmdLog.getId(), true, null, "Display saved");
                    return DisplayResult.builder().success(true).build();
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

    /**
     * 主动抓拍。
     * <p>
     * 臻识抓拍为两段式（协议 7.6/6.5）：下行 snapshot 命令仅回执确认，
     * 抓图结果由设备通过 up/snapshot 异步推送；取 payload.image_content（Base64 背景图）落盘。
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

        return handler.sendSnapshot(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                .thenApply(message -> {
                    CaptureResultDTO result = parseSnapshotResult(device.getDeviceId(), message);
                    long elapsed = System.currentTimeMillis() - startMs;
                    if (result.isSuccess()) {
                        log.info("[Capture] Coordinator: snapshot ok  deviceId={}  imageUrl={}  elapsedMs={}",
                                deviceId, result.getImageUrl(), elapsed);
                        commandLogService.recordResponse(cmdLog.getId(), true, null, "snapshot ok");
                    } else {
                        log.warn("[Capture] Coordinator: snapshot failed  deviceId={}  reason={}  elapsedMs={}",
                                deviceId, result.getMessage(), elapsed);
                        commandLogService.recordResponse(cmdLog.getId(), false, null, result.getMessage());
                    }
                    return result;
                })
                .exceptionally(e -> {
                    log.warn("[Capture] Coordinator: snapshot error  deviceId={}  error={}", deviceId, e.getMessage());
                    commandLogService.recordResponse(cmdLog.getId(), false, null, "snapshot error: " + e.getMessage());
                    return CaptureResultDTO.builder()
                            .success(false)
                            .message("snapshot error: " + e.getMessage())
                            .build();
                });
    }

    /**
     * 解析 up/snapshot 抓图结果：state_code 非 200 视为失败；image_content 非空则落盘返回 URL。
     */
    private CaptureResultDTO parseSnapshotResult(String deviceSn, MqttMessage message) {
        Object payload = message.getPayload();
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return CaptureResultDTO.builder()
                    .success(false)
                    .message("snapshot result payload empty")
                    .build();
        }
        Object stateCode = payloadMap.get("state_code");
        if (stateCode instanceof Number num && num.intValue() != 200) {
            return CaptureResultDTO.builder()
                    .success(false)
                    .message("device snapshot failed, state_code=" + num.intValue())
                    .build();
        }
        Object imageContent = payloadMap.get("image_content");
        if (imageContent instanceof String base64 && !base64.isBlank()) {
            return saveBase64Image(deviceSn, base64, "snapshot");
        }
        // 传图方式非 MQTT 直传时，设备回云存路径（Base64 编码的签名 URL）
        String ossUrl = decodeOssUrl(payloadMap);
        if (ossUrl != null) {
            return downloadAndSave(deviceSn, ossUrl);
        }
        return CaptureResultDTO.builder()
                .success(false)
                .message("snapshot result no image_content")
                .build();
    }

    /**
     * 解析云存路径字段（imgAbsolutePath → imgPath），Base64 解码为签名 URL。
     *
     * @return 解码后的 URL；无可用路径返回 null
     */
    private String decodeOssUrl(Map<?, ?> payloadMap) {
        for (String field : new String[]{"imgAbsolutePath", "imgPath"}) {
            Object value = payloadMap.get(field);
            if (value instanceof String encoded && !encoded.isBlank()) {
                try {
                    String url = new String(java.util.Base64.getDecoder().decode(encoded),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (url.startsWith("http")) {
                        return url;
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("[Capture] decode {} failed (not base64?)  deviceSn ignored, value={}", field, encoded);
                }
            }
        }
        return null;
    }

    /**
     * 从云存签名 URL 下载图片并本地落盘（保证链接长期有效）；
     * 下载失败时退化返回原始签名 URL（约 1 小时有效期，可即时查看）。
     */
    private CaptureResultDTO downloadAndSave(String deviceSn, String ossUrl) {
        long epochSeconds = java.time.Instant.now().getEpochSecond();
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) java.net.URI.create(ossUrl).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (java.io.InputStream in = conn.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0) {
                    throw new java.io.IOException("empty image");
                }
                imageStorageService.saveBytes(deviceSn, epochSeconds, bytes, null);
            }
            String imageUrl = imageStorageService.buildFullImageUrl(deviceSn, epochSeconds);
            return CaptureResultDTO.builder()
                    .success(true)
                    .imageUrl(imageUrl)
                    .message("snapshot ok (oss downloaded)")
                    .build();
        } catch (Exception e) {
            log.warn("[Capture] download oss image failed, fallback to signed url  deviceSn={}  error={}",
                    deviceSn, e.getMessage());
            return CaptureResultDTO.builder()
                    .success(true)
                    .imageUrl(ossUrl)
                    .message("snapshot ok (oss signed url, expires soon)")
                    .build();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Base64 图片解码落盘，返回可访问 URL。与芊熠实现保持一致。
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
     */
    private void logGateResult(Device device, String command, boolean success, Integer deviceCode) {
        String plateNo = handler.getLastPlate(device.getDeviceId());
        log.info("Gate command result: deviceSn={}, brand={}, command={}, plateNo={}, platformDeviceId={}, tenantId={}, parkingLotId={}, laneId={}, success={}, deviceCode={}, message={}",
                device.getDeviceId(),
                "ZHENSHI",
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
