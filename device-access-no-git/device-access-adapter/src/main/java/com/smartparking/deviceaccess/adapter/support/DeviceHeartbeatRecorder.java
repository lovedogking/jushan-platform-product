package com.smartparking.deviceaccess.adapter.support;

import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备心跳记录器（adapter 层内部支持组件）。
 * <p>
 * 负责运行时心跳缓存、写入节流和上下线状态标记。
 * 由 {@link com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler}
 * 和 {@link com.smartparking.deviceaccess.adapter.xinlutong.XinlutongMessageHandler}
 * 共用，避免两个品牌 Handler 直接重复操作数据库。
 * <p>
 * 这是 v0.4 的过渡组件，等 v0.6 引入 DeviceManager 后可被替换。
 *
 * @since v0.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceHeartbeatRecorder {

    private final DeviceMapper deviceMapper;

    /** 设备最后心跳时间缓存，key = deviceSn */
    private final Map<String, Long> lastHeartbeatCache = new ConcurrentHashMap<>();

    /** 设备最后 DB 写入时间缓存（用于写入节流），key = deviceSn */
    private final Map<String, Long> lastDbWriteCache = new ConcurrentHashMap<>();

    /** DB 写入最小间隔：60 秒 */
    private static final long DB_WRITE_THROTTLE_MS = 60_000L;

    /** 离线判定阈值：90 秒 */
    private static final long OFFLINE_THRESHOLD_MS = 90_000L;

    /**
     * 记录一次心跳。
     * <p>
     * 更新内存缓存，并按阈值节流写入数据库（status=ONLINE, last_online_time）。
     *
     * @param deviceSn 设备序列号
     */
    public void recordHeartbeat(String deviceSn) {
        long now = System.currentTimeMillis();
        lastHeartbeatCache.put(deviceSn, now);

        Long lastWrite = lastDbWriteCache.get(deviceSn);
        if (lastWrite == null || (now - lastWrite) > DB_WRITE_THROTTLE_MS) {
            lastDbWriteCache.put(deviceSn, now);
            try {
                int rows = deviceMapper.update(null,
                        new LambdaUpdateWrapper<Device>()
                                .eq(Device::getDeviceId, deviceSn)
                                .set(Device::getLastOnlineTime, LocalDateTime.now())
                                .set(Device::getStatus, "ONLINE"));
                log.debug("Heartbeat persisted to DB: sn={}, rows={}", deviceSn, rows);
            } catch (Exception e) {
                log.error("Failed to persist heartbeat to DB: sn={}", deviceSn, e);
            }
        } else {
            log.debug("Heartbeat DB write throttled: sn={}, lastWrite={}ms ago", deviceSn, now - lastWrite);
        }
    }

    /**
     * 判断设备是否在线。
     *
     * @param deviceSn    设备序列号
     * @param thresholdMs 心跳超时阈值（毫秒）
     * @return true 表示最近 thresholdMs 内收到过心跳
     */
    public boolean isDeviceOnline(String deviceSn, long thresholdMs) {
        Long lastHeartbeat = lastHeartbeatCache.get(deviceSn);
        if (lastHeartbeat == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastHeartbeat) < thresholdMs;
    }

    /**
     * 定时扫描心跳超时设备，标记为 OFFLINE。
     * <p>
     * 每 30 秒执行一次。超过 {@link #OFFLINE_THRESHOLD_MS} 无心跳的设备标记为 OFFLINE。
     */
    @Scheduled(fixedRate = 30_000)
    public void detectOfflineDevices() {
        long now = System.currentTimeMillis();
        lastHeartbeatCache.forEach((sn, lastHb) -> {
            if ((now - lastHb) > OFFLINE_THRESHOLD_MS) {
                log.info("Device offline detected: sn={}, lastHb={}ms ago", sn, now - lastHb);
                try {
                    deviceMapper.update(null,
                            new LambdaUpdateWrapper<Device>()
                                    .eq(Device::getDeviceId, sn)
                                    .eq(Device::getStatus, "ONLINE")
                                    .set(Device::getStatus, "OFFLINE"));
                } catch (Exception e) {
                    log.error("Failed to mark device OFFLINE: sn={}", sn, e);
                }
            }
        });
    }
}
