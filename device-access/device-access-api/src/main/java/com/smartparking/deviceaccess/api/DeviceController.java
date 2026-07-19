package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.api.dto.*;
import com.smartparking.deviceaccess.common.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 设备命令 API。
 * <p>
 * 提供给停车业务平台调用的 REST 接口。
 * 所有接口统一返回 {@link Result} 格式。
 * <p>
 * v0.3: 筛选参数 brand→deviceType；注册使用 productId；新增设备关系管理端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceMonitorService deviceMonitorService;

    // ═══════════════════════════════════════════
    // v0.1 遗留接口（不变）
    // ═══════════════════════════════════════════

    /**
     * 校时。
     */
    @PostMapping("/{deviceId}/time/sync")
    public CompletableFuture<Result<CommandResultDTO>> syncTime(@PathVariable String deviceId) {
        log.info("API: syncTime deviceId={}", deviceId);
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.syncTime(deviceId)
                .thenApply(result -> result.getSuccess() ? Result.ok(result) : Result.fail(500, result.getMessage()));
    }

    /**
     * 开闸。
     */
    @PostMapping("/{deviceId}/gate/open")
    public CompletableFuture<Result<CommandResultDTO>> openGate(@PathVariable String deviceId) {
        log.info("API: openGate deviceId={}", deviceId);
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.openGate(deviceId)
                .thenApply(result -> result.getSuccess() ? Result.ok(result) : Result.fail(500, result.getMessage()));
    }

    /**
     * 关闸。
     */
    @PostMapping("/{deviceId}/gate/close")
    public CompletableFuture<Result<CommandResultDTO>> closeGate(@PathVariable String deviceId) {
        log.info("API: closeGate deviceId={}", deviceId);
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.closeGate(deviceId)
                .thenApply(result -> result.getSuccess() ? Result.ok(result) : Result.fail(500, result.getMessage()));
    }

    /**
     * 锁定道闸（继电器强制吸合保持开启）。
     */
    @PostMapping("/{deviceId}/gate/lock")
    public CompletableFuture<Result<CommandResultDTO>> lockGate(
            @PathVariable String deviceId,
            @RequestBody(required = false) LockGateRequest req) {
        log.info("API: lockGate deviceId={}", deviceId);
        LockGateRequest body = req != null ? req : new LockGateRequest();
        return deviceService.lockGate(deviceId, body)
                .thenApply(result -> result.getSuccess() ? Result.ok(result) : Result.fail(500, result.getMessage()));
    }

    /**
     * 解除道闸锁定（解锁并关闸）。
     */
    @PostMapping("/{deviceId}/gate/unlock")
    public CompletableFuture<Result<CommandResultDTO>> unlockGate(
            @PathVariable String deviceId,
            @RequestBody(required = false) UnlockGateRequest req) {
        log.info("API: unlockGate deviceId={}", deviceId);
        UnlockGateRequest body = req != null ? req : new UnlockGateRequest(); return deviceService.unlockGate(deviceId, body)
                .thenApply(result -> result.getSuccess() ? Result.ok(result) : Result.fail(500, result.getMessage()));
    }

    /**
     * 查询设备状态。
     */
    @GetMapping("/{deviceId}/status")
    public Result<DeviceStatusDTO> getStatus(@PathVariable String deviceId) {
        log.info("API: getStatus deviceId={}", deviceId);
        DeviceStatusDTO status = deviceService.getStatus(deviceId);
        return Result.ok(status);
    }

    /**
     * 查询设备健康状态。
     */
    @GetMapping("/{deviceId}/health")
    public Result<DeviceHealthDTO> getHealth(@PathVariable String deviceId) {
        log.info("API: getHealth deviceId={}", deviceId);
        DeviceHealthDTO health = deviceMonitorService.getHealth(deviceId);
        return Result.ok(health);
    }

    // ═══════════════════════════════════════════
    // v0.3: 设备 CRUD
    // ═══════════════════════════════════════════

    /**
     * 注册设备。
     */
    @PostMapping
    public Result<DeviceDTO> register(@Valid @RequestBody DeviceRegisterRequest req) {
        log.info("API: register deviceId={}, name={}, productId={}",
                req.getDeviceId(), req.getDeviceName(), req.getProductId());
        DeviceDTO device = deviceService.register(req);
        return Result.ok(device);
    }

    /**
     * 设备列表（不含 relations）。
     * <p>
     * v0.4: 新增 tenantId / parkingLotId / laneId 业务字段筛选，支持云平台多租户查询。
     */
    @GetMapping
    public Result<List<DeviceDTO>> listDevices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String parkingLotId,
            @RequestParam(required = false) String laneId) {
        log.info("API: listDevices keyword={}, deviceType={}, direction={}, status={}, tenantId={}, parkingLotId={}, laneId={}",
                keyword, deviceType, direction, status, tenantId, parkingLotId, laneId);
        List<DeviceDTO> devices = deviceService.listDevices(keyword, deviceType, direction, status,
                tenantId, parkingLotId, laneId);
        return Result.ok(devices);
    }

    /**
     * 设备详情（含 relations）。
     */
    @GetMapping("/{deviceId}")
    public Result<DeviceDetailDTO> getDevice(@PathVariable String deviceId) {
        log.info("API: getDevice deviceId={}", deviceId);
        DeviceDetailDTO device = deviceService.getDeviceDetail(deviceId);
        return Result.ok(device);
    }

    /**
     * 更新设备（部分更新）。
     */
    @PutMapping("/{deviceId}")
    public Result<DeviceDTO> updateDevice(@PathVariable String deviceId,
                                          @Valid @RequestBody DeviceUpdateRequest req) {
        log.info("API: updateDevice deviceId={}", deviceId);
        DeviceDTO device = deviceService.updateDevice(deviceId, req);
        return Result.ok(device);
    }

    /**
     * 注销设备（软删除）。
     */
    @DeleteMapping("/{deviceId}")
    public Result<Void> deleteDevice(@PathVariable String deviceId) {
        log.info("API: deleteDevice deviceId={}", deviceId);
        deviceService.deleteDevice(deviceId);
        return Result.ok();
    }

    // ═══════════════════════════════════════════
    // v0.3: 设备关系管理
    // ═══════════════════════════════════════════

    /**
     * 查询设备的所有关系。
     */
    @GetMapping("/{deviceId}/relations")
    public Result<List<DeviceRelationDTO>> getRelations(@PathVariable String deviceId) {
        log.info("API: getRelations deviceId={}", deviceId);
        List<DeviceRelationDTO> relations = deviceService.getRelations(deviceId);
        return Result.ok(relations);
    }

    /**
     * 创建设备关系。
     */
    @PostMapping("/{deviceId}/relations")
    public Result<DeviceRelationDTO> createRelation(@PathVariable String deviceId,
                                                     @Valid @RequestBody DeviceRelationRequest req) {
        log.info("API: createRelation source={}, type={}, target={}",
                deviceId, req.getRelationType(), req.getTargetDeviceId());
        DeviceRelationDTO relation = deviceService.createRelation(deviceId, req);
        return Result.ok(relation);
    }

    /**
     * 删除设备关系。
     */
    @DeleteMapping("/{deviceId}/relations/{relationId}")
    public Result<Void> deleteRelation(@PathVariable String deviceId,
                                       @PathVariable Long relationId) {
        log.info("API: deleteRelation deviceId={}, relationId={}", deviceId, relationId);
        deviceService.deleteRelation(deviceId, relationId);
        return Result.ok();
    }

    /**
     * 启用设备关系。
     */
    @PutMapping("/{deviceId}/relations/{relationId}/enable")
    public Result<Void> enableRelation(@PathVariable String deviceId,
                                       @PathVariable Long relationId) {
        log.info("API: enableRelation deviceId={}, relationId={}", deviceId, relationId);
        deviceService.enableRelation(deviceId, relationId);
        return Result.ok();
    }

    /**
     * 停用设备关系。
     */
    @PutMapping("/{deviceId}/relations/{relationId}/disable")
    public Result<Void> disableRelation(@PathVariable String deviceId,
                                        @PathVariable Long relationId) {
        log.info("API: disableRelation deviceId={}, relationId={}", deviceId, relationId);
        deviceService.disableRelation(deviceId, relationId);
        return Result.ok();
    }

    // ═══════════════════════════════════════════
    // 显示内容控制（Display Content）
    // ═══════════════════════════════════════════

    /**
     * 实时显示文字。
     * <p>
     * 向设备下挂 LED 控制卡临时区发送文本，立即覆盖当前显示。
     * 内容掉电丢失，不持久化。
     */
    @PostMapping("/{deviceId}/display/text")
    public CompletableFuture<Result<DisplayResult>> displayText(
            @PathVariable String deviceId,
            @Valid @RequestBody DisplayTextRequest req) {
        log.info("API: displayText deviceId={}, direction={}", deviceId, req.getDirection());
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.displayText(deviceId, req)
                .thenApply(result -> result.isSuccess()
                        ? Result.ok(result)
                        : Result.fail(500, result.getErrorMessage()));
    }

    /**
     * 保存显示内容到设备下挂控制卡。
     * <p>
     * 写入控制卡外部存储器，掉电不丢失。
     * 控制卡空闲时自动循环显示已存储内容。低频操作。
     */
    @PostMapping("/{deviceId}/display/save")
    public CompletableFuture<Result<DisplayResult>> saveDisplay(
            @PathVariable String deviceId,
            @Valid @RequestBody DisplaySaveRequest req) {
        log.info("API: saveDisplay deviceId={}, direction={}", deviceId, req.getDirection());
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.saveDisplay(deviceId, req)
                .thenApply(result -> result.isSuccess()
                        ? Result.ok(result)
                        : Result.fail(500, result.getErrorMessage()));
    }

    // ═══════════════════════════════════════════
    // 外围设备控制（Peripheral Control）
    // ═══════════════════════════════════════════

    /**
     * 控制 Camera 的外围设备（当前仅支持 DISPLAY）。
     * <p>
     * 支持动作：ENABLE（启用）、DISABLE（关闭）、SET_MODE（设置模式）。
     * Device Access 只负责设备能力控制，不管理显示内容。
     */
    @PostMapping("/{deviceId}/peripheral/display")
    public CompletableFuture<Result<PeripheralControlResult>> controlDisplay(
            @PathVariable String deviceId,
            @Valid @RequestBody PeripheralControlRequest request) {
        log.info("API: controlDisplay deviceId={}, action={}, mode={}",
                deviceId, request.getAction(), request.getMode());
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.controlPeripheral(deviceId, request)
                .thenApply(Result::ok);
    }

    // ═══════════════════════════════════════════
    // v0.5: 显示屏高级控制（Config / Voice / Enhanced Display）
    // ═══════════════════════════════════════════

    /**
     * 显示屏配置（音量、亮度、方向、时间同步）。
     * <p>
     * 配置与显示内容分离，各自独立生命周期。
     */
    @PostMapping("/{deviceId}/display/config")
    public CompletableFuture<Result<DisplayResult>> configDisplay(
            @PathVariable String deviceId,
            @Valid @RequestBody DisplayConfigRequest req) {
        log.info("API: configDisplay deviceId={}, configType={}", deviceId, req.getConfigType());
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.configDisplay(deviceId, req)
                .thenApply(result -> result.isSuccess()
                        ? Result.ok(result)
                        : Result.fail(500, result.getErrorMessage()));
    }

    /**
     * 语音控制（播放、停止）。
     * <p>
     * 语音与显示内容分离，各自独立生命周期。
     */
    @PostMapping("/{deviceId}/voice/control")
    public CompletableFuture<Result<VoiceControlResult>> controlVoice(
            @PathVariable String deviceId,
            @Valid @RequestBody VoiceControlRequest req) {
        log.info("API: controlVoice deviceId={}, action={}, voiceText={}",
                deviceId, req.getAction(), req.getVoiceText());
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.controlVoice(deviceId, req)
                .thenApply(result -> result.isSuccess()
                        ? Result.ok(result)
                        : Result.fail(500, result.getErrorMessage()));
    }

    /**
     * 增强版实时显示文字。
     * <p>
     * 支持字体、颜色、语音同步。
     * 向后兼容 /display/text，新增字段均为可选。
     */
    @PostMapping("/{deviceId}/display/text/enhanced")
    public CompletableFuture<Result<DisplayResult>> displayTextEnhanced(
            @PathVariable String deviceId,
            @Valid @RequestBody DisplayTextRequest req) {
        log.info("API: displayTextEnhanced deviceId={}, direction={}, font={}, voiceId={}",
                deviceId, req.getDirection(), req.getFont(), req.getVoiceId());
        // 修复：改为异步返回，Spring MVC 自动处理 CompletableFuture
        return deviceService.displayTextEnhanced(deviceId, req)
                .thenApply(result -> result.isSuccess()
                        ? Result.ok(result)
                        : Result.fail(500, result.getErrorMessage()));
    }

}
