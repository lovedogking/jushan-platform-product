package com.jushan.platform.modules.device.client;

import com.jushan.platform.modules.device.client.dto.CaptureResultDTO;
import com.jushan.platform.modules.device.client.dto.CommandResultDTO;
import com.jushan.platform.modules.device.client.dto.DeviceAccessResponse;
import com.jushan.platform.modules.device.client.dto.DeviceStatusDTO;
import com.jushan.platform.modules.device.client.dto.DisplayConfigRequest;
import com.jushan.platform.modules.device.client.dto.DisplayResultDTO;
import com.jushan.platform.modules.device.client.dto.DisplaySaveRequest;
import com.jushan.platform.modules.device.client.dto.DisplayTextRequest;
import com.jushan.platform.modules.device.client.dto.TimeSyncResultDTO;
import com.jushan.platform.modules.device.client.dto.VoiceControlRequest;
import com.jushan.platform.modules.device.client.dto.VoiceResultDTO;

/**
 * Device Access HTTP 客户端接口（T23）。
 * <p>
 * 封装对 Device Access 的 HTTP 调用，业务层通过此接口调用，
 * 不得自行拼接 Device Access URL。
 * <p>
 * <strong>v0.2 已实现</strong>：
 * <ul>
 *   <li>{@link #getStatus(String)} — 设备状态查询</li>
 *   <li>{@link #syncTime(String)} — 设备校时</li>
 * </ul>
 * <p>
 * <strong>v0.4 新增</strong>：
 * <ul>
 *   <li>{@link #openGate(String)} — 开闸</li>
 *   <li>{@link #closeGate(String)} — 关闸</li>
 *   <li>{@link #displayText(String, DisplayTextRequest)} — 显示屏实时文字</li>
 *   <li>{@link #saveDisplay(String, DisplaySaveRequest)} — 保存显示内容</li>
 *   <li>{@link #displayConfig(String, DisplayConfigRequest)} — 显示屏配置</li>
 *   <li>{@link #voiceControl(String, VoiceControlRequest)} — 语音播报</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface DeviceAccessClient {

    /**
     * 查询设备状态。
     * <p>
     * 对应 {@code GET /api/v1/devices/{deviceSn}/status}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 设备状态
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    DeviceStatusDTO getStatus(String deviceSn);

    /**
     * 同步设备时间。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/time/sync}。
     * 当前 DA v0.2 无 commandId 幂等，每次调用都会向设备发送校时命令。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 校时结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    TimeSyncResultDTO syncTime(String deviceSn);

    // ==================== v0.4 设备控制 ====================

    /**
     * 开闸。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/gate/open}。
     * 写操作，<strong>不携带 commandId</strong>。如需幂等重试请使用 {@link #openGate(String, String)}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    CommandResultDTO openGate(String deviceSn);

    /**
     * 关闸。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/gate/close}。
     * 写操作，<strong>不携带 commandId</strong>。如需幂等重试请使用 {@link #closeGate(String, String)}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    CommandResultDTO closeGate(String deviceSn);

    /**
     * 开闸（携带 commandId 幂等标记，任务包 7-1）。
     * <p>
     * 与 {@link #openGate(String)} 的区别在于携带应用层幂等键，
     * 写入 {@code X-Command-Id} 请求头，支持网络瞬断场景的自动重试。
     *
     * @param deviceSn  设备厂商序列号
     * @param commandId 幂等命令 ID（UUID），跨重试保持一致
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或重试耗尽
     */
    CommandResultDTO openGate(String deviceSn, String commandId);

    /**
     * 关闸（携带 commandId 幂等标记，任务包 7-1）。
     *
     * @param deviceSn  设备厂商序列号
     * @param commandId 幂等命令 ID（UUID），跨重试保持一致
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或重试耗尽
     */
    CommandResultDTO closeGate(String deviceSn, String commandId);

    /**
     * 锁定道闸（常开：继电器强制吸合保持开启）。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/gate/lock}。
     * 写操作，<strong>不携带 commandId</strong>。如需幂等重试请使用 {@link #lockGate(String, String)}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    CommandResultDTO lockGate(String deviceSn);

    /**
     * 解除道闸锁定（取消常开：解锁并关闸，恢复常规模式）。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/gate/unlock}。
     * 写操作，<strong>不携带 commandId</strong>。如需幂等重试请使用 {@link #unlockGate(String, String)}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    CommandResultDTO unlockGate(String deviceSn);

    /**
     * 锁定道闸（常开，携带 commandId 幂等标记）。
     *
     * @param deviceSn  设备厂商序列号
     * @param commandId 幂等命令 ID（UUID），跨重试保持一致
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或重试耗尽
     */
    CommandResultDTO lockGate(String deviceSn, String commandId);

    /**
     * 解除道闸锁定（取消常开，携带 commandId 幂等标记）。
     *
     * @param deviceSn  设备厂商序列号
     * @param commandId 幂等命令 ID（UUID），跨重试保持一致
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或重试耗尽
     */
    CommandResultDTO unlockGate(String deviceSn, String commandId);

    /**
     * 锁定道闸常关（继电器强制保持关闭）。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/gate/lock-close}。
     * 写操作，<strong>不携带 commandId</strong>。如需幂等重试请使用 {@link #lockCloseGate(String, String)}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     * @since v1.5
     */
    CommandResultDTO lockCloseGate(String deviceSn);

    /**
     * 锁定道闸常关（携带 commandId 幂等标记）。
     *
     * @param deviceSn  设备厂商序列号
     * @param commandId 幂等命令 ID（UUID），跨重试保持一致
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或重试耗尽
     * @since v1.5
     */
    CommandResultDTO lockCloseGate(String deviceSn, String commandId);

    /**
     * 显示屏实时文字。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/display/text}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @param request  显示内容请求
     * @return 显示结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    DisplayResultDTO displayText(String deviceSn, DisplayTextRequest request);

    /**
     * 保存显示内容。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/display/save}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @param request  保存内容请求
     * @return 显示结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    DisplayResultDTO saveDisplay(String deviceSn, DisplaySaveRequest request);

    /**
     * 显示屏配置（音量/亮度/方向）。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/display/config}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @param request  配置请求
     * @return 显示结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    DisplayResultDTO displayConfig(String deviceSn, DisplayConfigRequest request);

    /**
     * 语音播报。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/voice/control}。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @param request  语音控制请求
     * @return 播报结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    VoiceResultDTO voiceControl(String deviceSn, VoiceControlRequest request);

    /**
     * 主动抓拍。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/capture}。
     * 触发相机立即抓拍一张图片，保存到存储后返回可访问 URL。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 抓拍结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     * @since v0.7
     */
    CaptureResultDTO captureImage(String deviceSn);

    /**
     * 同步设备白名单（单条操作）。
     * <p>
     * 对应 {@code POST /api/v1/devices/{deviceSn}/whitelist/sync}。
     *
     * @param deviceSn 设备序列号
     * @param action   操作类型：ADD / DELETE / CLEAR
     * @param plate    车牌号（DELETE/CLEAR 可为 null）
     * @return 命令执行结果
     */
    CommandResultDTO syncWhitelist(String deviceSn, String action, String plate);

    /**
     * 重启设备。
     */
    CommandResultDTO reboot(String deviceSn);

    /**
     * 手动触发识别（抓拍+识别）。
     */
    CommandResultDTO triggerRecognition(String deviceSn);
}
