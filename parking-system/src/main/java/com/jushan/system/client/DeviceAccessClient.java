package com.jushan.system.client;

import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.client.dto.TimeSyncResultDTO;

/**
 * Device Access HTTP 客户端接口（T23）。
 * <p>
 * 封装对 Device Access 的 HTTP 调用，业务层通过此接口调用，
 * 不得自行拼接 Device Access URL。
 * <p>
 * <strong>当前 v0.2 已实现（2 个）</strong>：
 * <ul>
 *   <li>{@link #getStatus(String)} — 设备状态查询</li>
 *   <li>{@link #syncTime(String)} — 设备校时</li>
 * </ul>
 * <p>
 * <strong>v1.0 待实现</strong>：
 * <ul>
 *   <li>{@link #openGate(String)} — 开闸（当前抛出 UnsupportedOperationException，待契约冻结后启用）</li>
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

    /**
     * 开闸（当前不可用）。
     * <p>
     * Device Access v0.2 中开闸为 {@code NOT_IMPLEMENTED_IN_V0.2}，
     * 当前方法预留接口占位，待 v1.0 契约冻结后启用。
     *
     * @param deviceSn 设备厂商序列号
     * @throws UnsupportedOperationException 当前始终抛出
     */
    default void openGate(String deviceSn) {
        throw new UnsupportedOperationException(
                "开闸接口在 Device Access v0.2 中未实现（NOT_IMPLEMENTED_IN_V0.2），待 v1.0 契约冻结后启用");
    }
}
