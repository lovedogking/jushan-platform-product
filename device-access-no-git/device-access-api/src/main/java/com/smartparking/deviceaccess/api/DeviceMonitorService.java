package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.api.dto.DeviceHealthDTO;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备监控服务。
 * <p>
 * 定时扫描 ONLINE 设备，检查心跳是否超时，超时则标记为 OFFLINE。
 * <p>
 * 心跳阈值：
 * <ul>
 *   <li>臻识 C5H：90 秒（3 次心跳间隔，每次 30 秒）</li>
 *   <li>信路通 XLT-01：270 秒（3 次心跳间隔，每次 90 秒）</li>
 * </ul>
 * <p>
 * 避免误标记：
 * <ul>
 *   <li>只检查 status=ONLINE 的设备</li>
 *   <li>刚注册的设备（create_time < 5 分钟）不检查</li>
 * </ul>
 * <p>
 * 定时频率：每 30 秒执行一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMonitorService {

    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;

    /** 臻识 C5H 心跳超时阈值：90 秒 */
    private static final long ZHENSHI_TIMEOUT_SECONDS = 90;

    /** 信路通 XLT-01 心跳超时阈值：270 秒 */
    private static final long XINLUTONG_TIMEOUT_SECONDS = 270;

    /** 新设备保护期：5 分钟 */
    private static final long NEW_DEVICE_GRACE_MINUTES = 5;

    // ═══════════════════════════════════════════
    // 定时离线检测
    // ═══════════════════════════════════════════

    /**
     * 定时扫描 ONLINE 设备，检查心跳是否超时并标记 OFFLINE。
     * <p>
     * 每 30 秒执行一次。使用 fixedRate 确保即使上次执行延迟，
     * 下次仍按固定间隔触发。
     */
    @Scheduled(fixedRate = 30_000)
    public void checkOfflineDevices() {
        LocalDateTime now = LocalDateTime.now();
        List<Device> onlineDevices = deviceRegistry.list(null, null, null, "ONLINE", null, null, null);

        int checked = 0;
        int markedOffline = 0;

        for (Device device : onlineDevices) {
            // 跳过新设备保护期
            if (isNewDevice(device, now)) {
                continue;
            }

            checked++;

            // 获取品牌对应的超时阈值
            long timeoutSeconds = getTimeoutSeconds(device.getProductId());
            if (isHeartbeatTimeout(device, timeoutSeconds, now)) {
                markOffline(device);
                markedOffline++;
            }
        }

        if (markedOffline > 0) {
            log.info("DeviceMonitor: checked {} ONLINE devices, marked {} OFFLINE",
                    checked, markedOffline);
        } else if (checked > 0) {
            log.debug("DeviceMonitor: checked {} ONLINE devices, all alive", checked);
        }
    }

    // ═══════════════════════════════════════════
    // 设备健康状态查询
    // ═══════════════════════════════════════════

    /**
     * 查询设备健康状态。
     *
     * @param deviceId 设备标识
     * @return 设备健康状态 DTO
     */
    public DeviceHealthDTO getHealth(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());

        long timeoutSeconds = getTimeoutSeconds(device.getProductId());
        boolean heartbeatAlive = !isHeartbeatTimeout(device, timeoutSeconds, LocalDateTime.now());

        return DeviceHealthDTO.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .brand(product.getBrand())
                .model(product.getModel())
                .status(device.getStatus())
                .healthy(heartbeatAlive)
                .lastOnlineTime(device.getLastOnlineTime() != null
                        ? device.getLastOnlineTime().toString()
                        : null)
                .heartbeatTimeoutSeconds(timeoutSeconds)
                .build();
    }

    // ═══════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════

    /**
     * 判断设备是否为新设备（保护期内）。
     */
    private boolean isNewDevice(Device device, LocalDateTime now) {
        if (device.getCreateTime() == null) {
            return false;
        }
        return Duration.between(device.getCreateTime(), now).toMinutes() < NEW_DEVICE_GRACE_MINUTES;
    }

    /**
     * 判断心跳是否超时。
     */
    private boolean isHeartbeatTimeout(Device device, long timeoutSeconds, LocalDateTime now) {
        LocalDateTime lastOnline = device.getLastOnlineTime();
        if (lastOnline == null) {
            return true;
        }
        return Duration.between(lastOnline, now).getSeconds() > timeoutSeconds;
    }

    /**
     * 获取品牌对应的心跳超时阈值。
     * <p>
     * 品牌阈值直接硬编码在 api 层，不提取通用接口。
     * 当接入第三品牌时，在此方法内新增 case 分支。
     */
    private long getTimeoutSeconds(Long productId) {
        DeviceProduct product = productRegistry.getById(productId);
        String brand = product.getBrand();
        return switch (brand) {
            case "ZHENSHI", "臻识" -> ZHENSHI_TIMEOUT_SECONDS;
            case "信路通" -> XINLUTONG_TIMEOUT_SECONDS;
            default -> ZHENSHI_TIMEOUT_SECONDS;
        };
    }

    /**
     * 标记设备为 OFFLINE。
     * <p>
     * 使用 DeviceRegistry.update 进行部分更新，确保线程安全。
     */
    private void markOffline(Device device) {
        Device updateFields = new Device();
        updateFields.setStatus("OFFLINE");

        deviceRegistry.update(device.getDeviceId(), updateFields);
        log.warn("DeviceMonitor: device marked OFFLINE  deviceId={}  brand={}  lastOnlineTime={}",
                device.getDeviceId(),
                getBrandName(device.getProductId()),
                device.getLastOnlineTime());
    }

    private String getBrandName(Long productId) {
        try {
            return productRegistry.getById(productId).getBrand();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
