package com.jushan.platform.modules.booth.service;
import com.jushan.platform.modules.device.service.MonitorAlertService;import com.jushan.platform.modules.device.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.platform.modules.device.entity.Device;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.device.mapper.DeviceMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主备相机故障切换服务（ADMIN-005）。
 * <p>
 * 监控主相机在线状态，主相机离线时自动记录备相机为当前活跃设备，
 * 主相机恢复后切回。切换冷却期 60 秒防抖。
 * <p>
 * <strong>注意</strong>：本服务仅负责状态追踪、日志告警；
 * 开闸执行设备（executorDeviceId）逻辑不受影响。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Service
public class CameraFailoverService {

    private static final Logger log = LoggerFactory.getLogger(CameraFailoverService.class);

    /** 切换冷却期（秒） */
    private static final long FAILOVER_COOLDOWN_SECONDS = 60;

    /** 活跃相机标识 */
    public static final String CAMERA_SOURCE_PRIMARY = "PRIMARY";
    public static final String CAMERA_SOURCE_BACKUP = "BACKUP";

    /**
     * 故障切换状态记录。
     * key: laneId:recognitionDirection, value: 最近一次切换时间
     */
    private final Map<String, FailoverState> failoverStates = new ConcurrentHashMap<>();

    private final DeviceMapper deviceMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final MonitorAlertService alertService;

    public CameraFailoverService(DeviceMapper deviceMapper,
                                  ParkingLotMapper parkingLotMapper,
                                  MonitorAlertService alertService) {
        this.deviceMapper = deviceMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.alertService = alertService;
    }

    /**
     * 故障切换状态。
     */
    static class FailoverState {
        /** 当前活跃的相机来源：PRIMARY / BACKUP */
        String activeSource;
        /** 主相机 ID */
        Long primaryDeviceId;
        /** 备相机 ID */
        Long backupDeviceId;
        /** 最近一次切换时间 */
        LocalDateTime lastSwitchTime;
        /** 主相机是否在线 */
        Boolean primaryOnline;

        FailoverState(String activeSource, Long primary, Long backup) {
            this.activeSource = activeSource;
            this.primaryDeviceId = primary;
            this.backupDeviceId = backup;
            this.lastSwitchTime = LocalDateTime.now();
            this.primaryOnline = true;
        }
    }

    /**
     * 处理设备离线事件（由状态查询或告警驱动）。
     * <p>
     * 调用方在检测到设备离线时调用此方法，
     * 本方法检查该设备是否为某车道的主相机，若为主相机则触发切换。
     *
     * @param deviceId 离线设备 ID
     * @param deviceSn 离线设备 SN
     */
    public void onDeviceOffline(Long deviceId, String deviceSn) {
        List<Device> cameras = findRelatedCameras(deviceId);
        if (cameras == null || cameras.size() < 2) {
            return; // 不是主备配置场景
        }

        Device self = cameras.stream().filter(c -> c.getId().equals(deviceId)).findFirst().orElse(null);
        if (self == null || self.getRecognitionDirection() == null) {
            return;
        }

        Integer direction = self.getRecognitionDirection();
        Long laneId = self.getLaneId();

        // 识别当前设备在主备中的角色
        Device primary = cameras.stream()
                .filter(c -> c.getCameraRole() != null && c.getCameraRole() == DeviceService.CAMERA_ROLE_PRIMARY)
                .findFirst().orElse(null);
        Device backup = cameras.stream()
                .filter(c -> c.getCameraRole() != null && c.getCameraRole() == DeviceService.CAMERA_ROLE_BACKUP)
                .findFirst().orElse(null);

        if (primary == null || backup == null) {
            return; // 不是完整的主备配置
        }

        String stateKey = stateKey(laneId, direction);

        // 冷却期检查
        FailoverState state = failoverStates.computeIfAbsent(stateKey,
                k -> new FailoverState(CAMERA_SOURCE_PRIMARY, primary.getId(), backup.getId()));

        if (isInCooldown(state)) {
            log.info("主备切换冷却期内，跳过: laneId={}, direction={}, deviceId={}",
                    laneId, direction, deviceId);
            return;
        }

        boolean isPrimary = deviceId.equals(primary.getId());
        boolean isOffline = true; // called from onDeviceOffline

        if (isPrimary && isOffline) {
            // 主相机离线 → 切到备相机
            state.activeSource = CAMERA_SOURCE_BACKUP;
            state.lastSwitchTime = LocalDateTime.now();
            state.primaryOnline = false;
            failoverStates.put(stateKey, state);

            log.warn("主相机离线，自动切换至备相机: laneId={}, direction={}, primaryDeviceId={}, backupDeviceId={}",
                    laneId, direction, primary.getId(), backup.getId());
            createFailoverAlert(laneId, self.getParkingLotId(),
                    "主相机 '" + primary.getName() + "' 离线，已自动切换至备相机 '" + backup.getName() + "'");

        } else if (!isPrimary && isOffline) {
            // 备相机离线：仅记录日志
            log.warn("备相机离线: laneId={}, direction={}, backupDeviceId={}", laneId, direction, backup.getId());
        }
    }

    /**
     * 处理设备上线事件（主相机恢复）。
     *
     * @param deviceId 上线设备 ID
     */
    public void onDeviceOnline(Long deviceId) {
        List<Device> cameras = findRelatedCameras(deviceId);
        if (cameras == null || cameras.size() < 2) {
            return;
        }

        Device self = cameras.stream().filter(c -> c.getId().equals(deviceId)).findFirst().orElse(null);
        if (self == null || self.getRecognitionDirection() == null) {
            return;
        }

        Device primary = cameras.stream()
                .filter(c -> c.getCameraRole() != null && c.getCameraRole() == DeviceService.CAMERA_ROLE_PRIMARY)
                .findFirst().orElse(null);

        if (primary == null || !deviceId.equals(primary.getId())) {
            return; // 不是主相机恢复
        }

        String stateKey = stateKey(self.getLaneId(), self.getRecognitionDirection());
        FailoverState state = failoverStates.get(stateKey);
        if (state == null) {
            return;
        }

        if (isInCooldown(state)) {
            log.info("主备切换冷却期内，跳过恢复: laneId={}, direction={}, deviceId={}",
                    self.getLaneId(), self.getRecognitionDirection(), deviceId);
            return;
        }

        // 主相机恢复 → 切回主相机
        state.activeSource = CAMERA_SOURCE_PRIMARY;
        state.lastSwitchTime = LocalDateTime.now();
        state.primaryOnline = true;
        failoverStates.put(stateKey, state);

        log.info("主相机恢复在线，切回主相机: laneId={}, direction={}, deviceId={}",
                self.getLaneId(), self.getRecognitionDirection(), primary.getId());
        createRecoveryAlert(self.getLaneId(), self.getParkingLotId(),
                "主相机 '" + primary.getName() + "' 已恢复在线，切回主相机");
    }

    /**
     * 获取指定车道和方向的当前活跃相机来源。
     *
     * @return PRIMARY / BACKUP，默认 PRIMARY
     */
    public String getActiveSource(Long laneId, Integer direction) {
        FailoverState state = failoverStates.get(stateKey(laneId, direction));
        return state != null ? state.activeSource : CAMERA_SOURCE_PRIMARY;
    }

    /**
     * 检查是否在冷却期内。
     */
    private boolean isInCooldown(FailoverState state) {
        if (state == null || state.lastSwitchTime == null) {
            return false;
        }
        long elapsed = java.time.Duration.between(state.lastSwitchTime, LocalDateTime.now()).getSeconds();
        return elapsed < FAILOVER_COOLDOWN_SECONDS;
    }

    /**
     * 查找与指定相机同车道的所有相机（用于主备配对）。
     */
    private List<Device> findRelatedCameras(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || device.getLaneId() == null || !"CAMERA".equals(device.getDeviceType())) {
            return null;
        }
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getLaneId, device.getLaneId())
                        .eq(Device::getDeviceType, "CAMERA")
                        .eq(Device::getStatus, DeviceService.STATUS_ENABLED));
    }

    private String stateKey(Long laneId, Integer direction) {
        return laneId + ":" + direction;
    }

    private void createFailoverAlert(Long laneId, Long parkingLotId, String message) {
        try {
            ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
            if (lot != null) {
                alertService.createGateAlert(lot.getTenantId(), parkingLotId, laneId, null,
                        "CAMERA_FAILOVER", message);
            }
        } catch (Exception e) {
            log.warn("创建故障切换告警失败: {}", e.getMessage());
        }
    }

    private void createRecoveryAlert(Long laneId, Long parkingLotId, String message) {
        try {
            ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
            if (lot != null) {
                alertService.createGateAlert(lot.getTenantId(), parkingLotId, laneId, null,
                        "CAMERA_RECOVERY", message);
            }
        } catch (Exception e) {
            log.warn("创建相机恢复告警失败: {}", e.getMessage());
        }
    }
}
