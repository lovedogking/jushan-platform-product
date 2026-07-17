package com.smartparking.deviceaccess.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.deviceaccess.common.entity.DeviceCommandLog;
import com.smartparking.deviceaccess.common.entity.DeviceCommandLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备命令日志服务。
 * <p>
 * 提供命令执行日志的持久化能力。
 * 记录请求和响应分两步：先 recordRequest 获取 logId，再 recordResponse 更新结果。
 * <p>
 * 所有写操作使用 {@link Async} 异步执行，避免阻塞命令下发主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandLogService {

    private final DeviceCommandLogMapper commandLogMapper;

    /**
     * 记录命令请求（同步，需要返回 logId 供后续更新）。
     *
     * @param deviceId         设备ID
     * @param brand            品牌
     * @param commandType      命令类型
     * @param plateNo          车牌号
     * @param platformDeviceId 平台设备ID
     * @param tenantId         租户ID
     * @param parkingLotId     停车场ID
     * @param laneId           车道ID
     * @return 已持久化的日志记录（含 id）
     */
    public DeviceCommandLog recordRequest(String deviceId, String brand, String commandType,
                                          String plateNo, String platformDeviceId,
                                          String tenantId, String parkingLotId, String laneId) {
        DeviceCommandLog log = new DeviceCommandLog();
        log.setDeviceId(deviceId);
        log.setBrand(brand);
        log.setCommandType(commandType);
        log.setPlateNo(plateNo);
        log.setSuccess(false); // 初始为 false，响应后更新
        log.setPlatformDeviceId(platformDeviceId);
        log.setTenantId(tenantId);
        log.setParkingLotId(parkingLotId);
        log.setLaneId(laneId);
        log.setRequestTime(LocalDateTime.now());

        commandLogMapper.insert(log);
        return log;
    }

    /**
     * 更新命令响应结果（异步，不阻塞主流程）。
     *
     * @param logId      日志ID（recordRequest 返回值）
     * @param success    是否成功
     * @param deviceCode 设备返回码
     * @param message    结果描述
     */
    @Async
    public void recordResponse(Long logId, boolean success, Integer deviceCode, String message) {
        try {
            DeviceCommandLog update = new DeviceCommandLog();
            update.setId(logId);
            update.setSuccess(success);
            update.setDeviceCode(deviceCode);
            update.setMessage(message);
            update.setResponseTime(LocalDateTime.now());

            commandLogMapper.updateById(update);
        } catch (Exception e) {
            log.error("Failed to record command response: logId={}", logId, e);
        }
    }

    /**
     * 查询设备的命令历史。
     *
     * @param deviceId 设备ID
     * @param limit    最大返回条数
     * @return 命令日志列表（按请求时间倒序）
     */
    public List<DeviceCommandLog> queryByDeviceId(String deviceId, int limit) {
        LambdaQueryWrapper<DeviceCommandLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceCommandLog::getDeviceId, deviceId)
                .orderByDesc(DeviceCommandLog::getRequestTime)
                .last("LIMIT " + limit);
        return commandLogMapper.selectList(wrapper);
    }
}
