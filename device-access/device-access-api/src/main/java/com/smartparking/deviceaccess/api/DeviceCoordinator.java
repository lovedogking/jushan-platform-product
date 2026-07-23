package com.smartparking.deviceaccess.api;

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
import com.smartparking.deviceaccess.common.enums.DisplayDirection;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 设备命令协调器接口。
 * <p>
 * 定义所有品牌设备命令协调器的统一契约。
 * 这是 api 层内部抽象，不涉及跨品牌适配（跨品牌适配在 adapter 层）。
 * <p>
 * 提取原因：ZhenshiDeviceCoordinator 和 XinlutongDeviceCoordinator 已验证大量共同能力
 *（开闸/关闸/校时/显示屏控制/语音控制），出现重复模式，按演进式架构原则提取接口。
 *
 * @since v0.4
 */
public interface DeviceCoordinator {

    /**
     * 返回品牌标识，用于 BrandCommandDispatcher 路由。
     *
     * @return "ZHENSHI" 或 "XINLUTONG"
     */
    String getBrand();

    // ═══════════════════════════════════════════
    // 异步命令
    // ═══════════════════════════════════════════

    /**
     * 校时命令。
     */
    CompletableFuture<CommandResultDTO> syncTime(String deviceId);

    /**
     * 开闸命令。
     */
    CompletableFuture<CommandResultDTO> openGate(String deviceId);

    /**
     * 关闸命令。
     */
    CompletableFuture<CommandResultDTO> closeGate(String deviceId);

    /**
     * 锁定道闸。
     */
    CompletableFuture<CommandResultDTO> lockGate(String deviceId, LockGateRequest req);

    /**
     * 解除道闸锁定。
     */
    CompletableFuture<CommandResultDTO> unlockGate(String deviceId, UnlockGateRequest req);

    /**
     * 外围设备控制。
     */
    CompletableFuture<PeripheralControlResult> controlPeripheral(String deviceId, PeripheralControlRequest req);

    /**
     * 实时显示文字。
     */
    CompletableFuture<DisplayResult> displayText(String deviceId, String content, DisplayDirection direction);

    /**
     * 保存显示内容。
     */
    CompletableFuture<DisplayResult> saveDisplay(String deviceId, String content, DisplayDirection direction);

    /**
     * 显示屏配置（音量、亮度、方向、时间同步）。
     */
    CompletableFuture<DisplayResult> configDisplay(String deviceId, DisplayConfigRequest req);

    /**
     * 语音控制（播放、停止）。
     */
    CompletableFuture<VoiceControlResult> controlVoice(String deviceId, VoiceControlRequest req);

    /**
     * 增强版实时显示文字。
     */
    CompletableFuture<DisplayResult> displayTextEnhanced(String deviceId, String content, DisplayDirection direction,
                                                           OlmM1dProtocol.FontType font, List<int[]> color,
                                                           Integer voiceId, String voiceVariable);

    /**
     * 主动抓拍。
     * <p>
     * 触发相机立即抓拍一张图片，上传至存储并返回可访问 URL。
     *
     * @param deviceId 设备 ID（注册时的 deviceId）
     * @return 抓拍结果（含图片 URL）
     * @since v0.7
     */
    CompletableFuture<CaptureResultDTO> capture(String deviceId);

    // ═══════════════════════════════════════════
    // 同步查询
    // ═══════════════════════════════════════════

    /**
     * 查询设备是否在线。
     * <p>
     * 同步方法，不异步化（查询类操作保持简单）。
     *
     * @param deviceSn 设备序列号
     * @return true 如果设备在线
     */
    boolean isDeviceOnline(String deviceSn);
}
