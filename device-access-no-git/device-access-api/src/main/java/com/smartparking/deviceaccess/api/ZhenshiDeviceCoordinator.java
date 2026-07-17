package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler;
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
     * 解除道闸锁定（解锁 IO0 + 重置状态机 + 关闸）。
     * <p>
     * 三步流程：
     * <ol>
     *   <li>发送 set_io_lock_status ioout=0 status=0 解除 IO0 锁定</li>
     *   <li>发送 gate_direct_open 重置摄像头道闸状态机</li>
     *   <li>发送 gpio_out IO1 关闸脉冲</li>
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

        int io = 0; // IO0 = 开闸继电器
        log.info("[Gate] Coordinator: unlockGate  deviceId={}  io={}", deviceId, io);

        // 1. 先解除 IO 锁定
        return handler.sendUnlockGate(device.getDeviceId(), io, DEFAULT_TIMEOUT_SECONDS)
                .thenCompose(reply -> {
                    boolean unlockSuccess = reply.getCode() != null && reply.getCode() == 200;
                    if (!unlockSuccess) {
                        String msg = "Device returned error code: " + reply.getCode();
                        logGateResult(device, "UNLOCK_GATE", false, reply.getCode());
                        commandLogService.recordResponse(cmdLog.getId(), false, reply.getCode(), msg);
                        return CompletableFuture.completedFuture(CommandResultDTO.builder()
                                .success(false)
                                .deviceCode(reply.getCode())
                                .message(msg)
                                .build());
                    }

                    // 2. 发送 gate_direct_open 重置摄像头道闸状态机
                    log.info("[Gate] Coordinator: unlockGate  deviceId={}  resetting state machine via gate_direct_open",
                            deviceId);

                    return handler.sendGateDirectOpen(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                            .handle((resetReply, resetError) -> {
                                if (resetError != null || resetReply == null || resetReply.getCode() == null || resetReply.getCode() != 200) {
                                    String reason = resetError != null ? resetError.getMessage()
                                            : (resetReply != null ? "code=" + resetReply.getCode() : "null reply");
                                    log.warn("[Gate] Coordinator: unlockGate  deviceId={}  gate_direct_open failed ({}), still trying close gate",
                                            deviceId, reason);
                                } else {
                                    log.info("[Gate] Coordinator: unlockGate  deviceId={}  state machine reset ok",
                                            deviceId);
                                }
                                return null;
                            })
                            .thenCompose(ignored -> {
                                // 3. 发送关闸命令
                                log.info("[Gate] Coordinator: unlockGate  deviceId={}  sending close gate", deviceId);

                                return handler.sendCloseGate(device.getDeviceId(), DEFAULT_TIMEOUT_SECONDS)
                                        .thenApply(pulseReply -> {
                                            boolean pulseSuccess = pulseReply.getCode() != null && pulseReply.getCode() == 200;
                                            String msg = pulseSuccess
                                                    ? "Gate unlocked and closed"
                                                    : "Gate unlocked but failed to close: " + pulseReply.getCode();
                                            logGateResult(device, "UNLOCK_GATE", pulseSuccess, pulseReply.getCode());
                                            commandLogService.recordResponse(cmdLog.getId(), pulseSuccess, pulseReply.getCode(), msg);
                                            return CommandResultDTO.builder()
                                                    .success(pulseSuccess)
                                                    .deviceCode(pulseReply.getCode())
                                                    .message(msg)
                                                    .build();
                                        });
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
