package com.jushan.system.client;

import com.jushan.system.client.dto.*;

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
     * 写操作，<strong>禁止自动重试</strong>。网络超时标记为 UNCERTAIN。
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
     * 写操作，<strong>禁止自动重试</strong>。网络超时标记为 UNCERTAIN。
     *
     * @param deviceSn 设备厂商序列号（来自平台可信设备记录，非前端传入）
     * @return 命令执行结果
     * @throws com.jushan.common.BusinessException DA 返回错误或网络异常
     */
    CommandResultDTO closeGate(String deviceSn);

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
}
