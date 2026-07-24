package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.api.dto.*;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 品牌命令分派器。
 * <p>
 * 负责在 DeviceService 校验能力之后，按品牌将命令路由到具体的 DeviceCoordinator。
 * 通过 {@link DeviceCoordinator} 接口统一调用，不再直接依赖 adapter 层具体类。
 * <p>
 * 品牌映射：product.getBrand() 返回中文（"臻识"、"信路通"）或英文（"ZHENSHI"），
 * 通过 BRAND_ALIAS_MAP 映射到 DeviceCoordinator.getBrand() 的标准值（"ZHENSHI"、"XINLUTONG"）。
 *
 * @since v0.4
 */
@Slf4j
@Component
public class BrandCommandDispatcher {

    private final Map<String, DeviceCoordinator> coordinators;

    /**
     * 品牌别名映射：product.getBrand() 的各种写法 → DeviceCoordinator.getBrand() 的标准值。
     */
    private static final Map<String, String> BRAND_ALIAS_MAP = Map.of(
            "臻识", "ZHENSHI",
            "ZHENSHI", "ZHENSHI",
            "信路通", "XINLUTONG",
            "芊熠", "QIANYI",
            "QIANYI", "QIANYI"
    );

    public BrandCommandDispatcher(List<DeviceCoordinator> coordinatorList) {
        this.coordinators = coordinatorList.stream()
                .collect(Collectors.toMap(DeviceCoordinator::getBrand, Function.identity()));
        log.info("Registered device coordinators: {}", coordinators.keySet());
    }

    /**
     * 解析品牌标识到标准值。
     */
    private String resolveBrand(String brand) {
        String resolved = BRAND_ALIAS_MAP.get(brand);
        if (resolved == null) {
            throw new UnsupportedOperationException("Unknown brand: " + brand);
        }
        return resolved;
    }

    private DeviceCoordinator getCoordinator(DeviceProduct product) {
        return coordinators.get(resolveBrand(product.getBrand()));
    }

    // ═══════════════════════════════════════════
    // 命令分派
    // ═══════════════════════════════════════════

    /**
     * 校时命令分派。
     */
    public CompletableFuture<CommandResultDTO> syncTime(String deviceId, DeviceProduct product) {
        return getCoordinator(product).syncTime(deviceId);
    }

    /**
     * 开闸命令分派。
     */
    public CompletableFuture<CommandResultDTO> openGate(String deviceId, DeviceProduct product) {
        return getCoordinator(product).openGate(deviceId);
    }

    /**
     * 关闸命令分派。
     */
    public CompletableFuture<CommandResultDTO> closeGate(String deviceId, DeviceProduct product) {
        return getCoordinator(product).closeGate(deviceId);
    }

    /**
     * 锁定道闸命令分派。
     */
    public CompletableFuture<CommandResultDTO> lockGate(String deviceId, DeviceProduct product, LockGateRequest req) {
        return getCoordinator(product).lockGate(deviceId, req);
    }

    /**
     * 解除道闸锁定命令分派。
     */
    public CompletableFuture<CommandResultDTO> unlockGate(String deviceId, DeviceProduct product, UnlockGateRequest req) {
        return getCoordinator(product).unlockGate(deviceId, req);
    }

    /**
     * 锁定道闸关闭命令分派。
     */
    public CompletableFuture<CommandResultDTO> lockCloseGate(String deviceId, DeviceProduct product, LockGateRequest req) {
        return getCoordinator(product).lockCloseGate(deviceId, req);
    }

    /**
     * 在线状态查询分派。
     */
    public boolean isDeviceOnline(String deviceSn, DeviceProduct product) {
        return getCoordinator(product).isDeviceOnline(deviceSn);
    }

    /**
     * 外围设备控制分派。
     */
    public CompletableFuture<PeripheralControlResult> controlPeripheral(String deviceId, DeviceProduct product,
                                                                          PeripheralControlRequest req) {
        return getCoordinator(product).controlPeripheral(deviceId, req);
    }

    /**
     * 实时显示文字分派。
     */
    public CompletableFuture<DisplayResult> displayText(String deviceId, DeviceProduct product,
                                                          String content, DisplayDirection direction) {
        return getCoordinator(product).displayText(deviceId, content, direction);
    }

    /**
     * 保存显示内容分派。
     */
    public CompletableFuture<DisplayResult> saveDisplay(String deviceId, DeviceProduct product,
                                                          String content, DisplayDirection direction) {
        return getCoordinator(product).saveDisplay(deviceId, content, direction);
    }

    /**
     * 显示屏配置分派（音量、亮度、方向、时间同步）。
     */
    public CompletableFuture<DisplayResult> configDisplay(String deviceId, DeviceProduct product, DisplayConfigRequest req) {
        return getCoordinator(product).configDisplay(deviceId, req);
    }

    /**
     * 语音控制分派（播放、停止）。
     */
    public CompletableFuture<VoiceControlResult> controlVoice(String deviceId, DeviceProduct product, VoiceControlRequest req) {
        return getCoordinator(product).controlVoice(deviceId, req);
    }

    /**
     * 增强版实时显示文字分派。
     */
    public CompletableFuture<DisplayResult> displayTextEnhanced(String deviceId, DeviceProduct product,
                                                                    String content, DisplayDirection direction,
                                                                    com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol.FontType font,
                                                                    List<int[]> color, Integer voiceId, String voiceVariable) {
        return getCoordinator(product).displayTextEnhanced(
                deviceId, content, direction, font, color, voiceId, voiceVariable);
    }

    /**
     * 主动抓拍分派。
     *
     * @since v0.7
     */
    public CompletableFuture<CaptureResultDTO> capture(String deviceId, DeviceProduct product) {
        return getCoordinator(product).capture(deviceId);
    }
}
