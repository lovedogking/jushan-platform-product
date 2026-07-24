package com.jushan.platform.modules.booth.service;
import com.jushan.platform.modules.device.service.MonitorAlertService;import com.jushan.platform.modules.device.service.DeviceService;import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;import com.jushan.platform.modules.parking.service.ParkingLaneService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.device.entity.Device;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.booth.entity.RecognitionEventLog;
import com.jushan.platform.modules.device.mapper.DeviceMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.booth.mapper.RecognitionEventLogMapper;
import com.jushan.platform.modules.booth.vo.BoothLaneVO;
import com.jushan.platform.modules.booth.vo.BoothLaneCameraVO;
import com.jushan.platform.modules.booth.vo.BoothMonitorSnapshotVO;
import com.jushan.platform.modules.booth.vo.BoothRecognitionEventVO;
import com.jushan.platform.modules.device.vo.DeviceStatusVO;
import com.jushan.platform.modules.device.vo.MonitorAlertVO;
import com.jushan.platform.modules.parking.vo.ParkingLotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 岗亭监控服务（P005）。
 * <p>
 * 提供岗亭监控页面初始化快照、设备状态刷新等能力。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class BoothMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BoothMonitorService.class);

    /** 最近识别事件数量。 */
    private static final int RECENT_EVENT_LIMIT = 20;

    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final DeviceMapper deviceMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final DeviceService deviceService;
    private final MonitorAlertService alertService;
    private final ParkingLotScopeResolver scopeResolver;
    private final CameraFailoverService cameraFailoverService;
    private final com.jushan.platform.modules.parking.service.ParkingSessionService parkingSessionService;

    public BoothMonitorService(ParkingLotMapper parkingLotMapper,
                                ParkingLaneMapper laneMapper,
                                DeviceMapper deviceMapper,
                                RecognitionEventLogMapper eventLogMapper,
                                DeviceService deviceService,
                                MonitorAlertService alertService,
                                ParkingLotScopeResolver scopeResolver,
                                CameraFailoverService cameraFailoverService,
                                com.jushan.platform.modules.parking.service.ParkingSessionService parkingSessionService) {
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.deviceMapper = deviceMapper;
        this.eventLogMapper = eventLogMapper;
        this.deviceService = deviceService;
        this.alertService = alertService;
        this.scopeResolver = scopeResolver;
        this.cameraFailoverService = cameraFailoverService;
        this.parkingSessionService = parkingSessionService;
    }

    /**
     * 获取岗亭监控初始化快照。
     * <p>
     * 包含停车场摘要、最近识别事件、设备状态列表、未确认异常提醒。
     *
     * @param parkingLotId 停车场 ID
     * @return 监控快照
     */
    public BoothMonitorSnapshotVO getSnapshot(Long parkingLotId) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }

        ParkingLot lot = getParkingLotWithAuth(parkingLotId);

        BoothMonitorSnapshotVO snapshot = new BoothMonitorSnapshotVO();
        snapshot.setParkingLot(toParkingLotVO(lot));
        snapshot.setLanes(loadLanes(parkingLotId));
        snapshot.setRecentEvents(loadRecentEvents(parkingLotId));
        snapshot.setDeviceStatuses(loadDeviceStatuses(parkingLotId));

        // P005：生成停车场级异常提醒（lot full / lot disabled）
        generateLotAlerts(lot);

        snapshot.setAlerts(loadAlerts(parkingLotId));

        log.info("岗亭监控快照已生成: parkingLotId={}, operator={}",
                parkingLotId, TenantContext.get() != null ? TenantContext.get().userId() : "unknown");
        return snapshot;
    }

    /**
     * 刷新指定停车场的设备状态并返回最新快照。
     * <p>
     * 注意：本方法会触发 Device Access 调用，有一定开销。
     *
     * @param parkingLotId 停车场 ID
     * @return 刷新后的设备状态列表
     */
    public List<DeviceStatusVO> refreshDeviceStatuses(Long parkingLotId) {
        ParkingLot lot = getParkingLotWithAuth(parkingLotId);

        List<Device> devices = listEnabledDevices(parkingLotId);
        List<Long> deviceIds = devices.stream()
                .map(Device::getId)
                .collect(Collectors.toList());

        List<DeviceStatusVO> statuses;
        if (deviceIds.isEmpty()) {
            statuses = Collections.emptyList();
        } else {
            statuses = deviceService.queryStatusBatch(deviceIds);
        }

        // 检查离线设备并生成告警
        for (DeviceStatusVO status : statuses) {
            if (status.getDeviceId() != null) {
                alertService.checkDeviceOffline(lot, status.getDeviceId(), status);
            }
        }

        return statuses;
    }

    // ==================== 私有方法 ====================

    private ParkingLot getParkingLotWithAuth(Long parkingLotId) {
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        DataScope.validateTenantMatch(lot.getTenantId(), "停车场");
        scopeResolver.validateAccess(parkingLotId);
        return lot;
    }

    private List<BoothRecognitionEventVO> loadRecentEvents(Long parkingLotId) {
        List<RecognitionEventLog> events = eventLogMapper.selectList(
                new LambdaQueryWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getParkingLotId, parkingLotId)
                        .orderByDesc(RecognitionEventLog::getEventTime)
                        .last("LIMIT " + RECENT_EVENT_LIMIT));

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ParkingLane> laneMap = laneMapper.selectByLotIdIgnoreTenant(parkingLotId)
                .stream()
                .collect(Collectors.toMap(ParkingLane::getId, l -> l));

        Map<Long, Device> deviceMap = deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                                .eq(Device::getParkingLotId, parkingLotId))
                .stream()
                .collect(Collectors.toMap(Device::getId, d -> d));

        return events.stream()
                .map(e -> toEventVO(e, laneMap.get(e.getLaneId()), deviceMap.get(e.getDeviceId())))
                .collect(Collectors.toList());
    }

    private List<BoothLaneVO> loadLanes(Long parkingLotId) {
        List<ParkingLane> lanes = laneMapper.selectByLotIdIgnoreTenant(parkingLotId);

        if (lanes.isEmpty()) {
            return Collections.emptyList();
        }

        // 加载该停车场所有已启用相机
        List<Device> allCameras = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getParkingLotId, parkingLotId)
                        .eq(Device::getDeviceType, "CAMERA")
                        .eq(Device::getStatus, DeviceService.STATUS_ENABLED));

        // laneId -> cameras
        Map<Long, List<Device>> camerasByLane = allCameras.stream()
                .filter(d -> d.getLaneId() != null)
                .collect(Collectors.groupingBy(Device::getLaneId));

        // 加载设备状态快照
        List<Long> allDeviceIds = allCameras.stream().map(Device::getId).collect(Collectors.toList());
        Map<Long, DeviceStatusVO> statusMap;
        if (allDeviceIds.isEmpty()) {
            statusMap = Collections.emptyMap();
        } else {
            statusMap = deviceService.getLatestSnapshots(allDeviceIds).stream()
                    .collect(Collectors.toMap(DeviceStatusVO::getDeviceId, s -> s, (a, b) -> a));
        }

        return lanes.stream()
                .map(lane -> {
                    List<Device> laneCameras = camerasByLane.getOrDefault(lane.getId(), Collections.emptyList());

                    BoothLaneVO vo = new BoothLaneVO();
                    vo.setId(lane.getId());
                    vo.setParkingLotId(lane.getLotId());
                    vo.setName(lane.getName());
                    vo.setCode(lane.getLaneNo());
                    vo.setDirection(ParkingLaneService.intToDirectionStr(lane.getType()));
                    vo.setStatus(ParkingLaneService.intToStatusStr(lane.getStatus()));
                    vo.setGateMode(lane.getGateMode());

                    // 构建 cameras 数组
                    List<BoothLaneCameraVO> cameraVOs = new ArrayList<>();
                    for (Device camera : laneCameras) {
                        BoothLaneCameraVO cvo = new BoothLaneCameraVO();
                        cvo.setDeviceId(camera.getId());
                        cvo.setName(camera.getName());
                        cvo.setRole(camera.getCameraRole() != null
                                ? (camera.getCameraRole() == DeviceService.CAMERA_ROLE_PRIMARY ? "PRIMARY" : "BACKUP")
                                : null);
                        cvo.setDirection(camera.getRecognitionDirection() != null
                                ? (camera.getRecognitionDirection() == 1 ? "ENTRY" : "EXIT")
                                : null);

                        DeviceStatusVO status = statusMap.get(camera.getId());
                        cvo.setOnline(status != null && Boolean.TRUE.equals(status.getOnline()) && !Boolean.TRUE.equals(status.getStale()));

                        // 计算 isActive：对比 CameraFailoverService 的活跃来源
                        boolean isActive = false;
                        if (camera.getRecognitionDirection() != null && camera.getCameraRole() != null) {
                            String activeSource = cameraFailoverService.getActiveSource(
                                    lane.getId(), camera.getRecognitionDirection());
                            String roleStr = camera.getCameraRole() == DeviceService.CAMERA_ROLE_PRIMARY ? "PRIMARY" : "BACKUP";
                            isActive = roleStr.equals(activeSource);
                        } else if (laneCameras.size() == 1) {
                            // 单相机车道：该相机即为活跃
                            isActive = true;
                        }
                        cvo.setIsActive(isActive);

                        cameraVOs.add(cvo);
                    }

                    // 向后兼容：单相机场景仍填充 deviceId/deviceName
                    if (!cameraVOs.isEmpty()) {
                        // 优先选主相机，否则取第一个
                        BoothLaneCameraVO primary = cameraVOs.stream()
                                .filter(c -> "PRIMARY".equals(c.getRole()))
                                .findFirst()
                                .orElse(cameraVOs.get(0));
                        vo.setDeviceId(primary.getDeviceId());
                        vo.setDeviceName(primary.getName());
                    }

                    vo.setCameras(cameraVOs);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private List<DeviceStatusVO> loadDeviceStatuses(Long parkingLotId) {
        List<Device> devices = listEnabledDevices(parkingLotId);
        List<Long> deviceIds = devices.stream()
                .map(Device::getId)
                .collect(Collectors.toList());

        if (deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return deviceService.getLatestSnapshots(deviceIds);
    }

    /**
     * 根据停车场状态生成异常提醒。
     */
    private void generateLotAlerts(ParkingLot lot) {
        try {
            if ("DISABLED".equals(lot.getStatus())) {
                alertService.createLotDisabledAlert(lot);
            }
            if (lot.getRemainingSpaces() != null && lot.getRemainingSpaces() <= 0) {
                alertService.createLotFullAlert(lot);
            }
        } catch (Exception e) {
            log.warn("生成停车场异常提醒失败（不影响快照）: parkingLotId={}, error={}",
                    lot.getId(), e.getMessage());
        }
    }

    private List<Device> listEnabledDevices(Long parkingLotId) {
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getParkingLotId, parkingLotId)
                        .eq(Device::getStatus, DeviceService.STATUS_ENABLED)
                        .orderByAsc(Device::getId));
    }

    private List<MonitorAlertVO> loadAlerts(Long parkingLotId) {
        return alertService.findUnacknowledged(parkingLotId).stream()
                .map(MonitorAlertService::toVO)
                .collect(Collectors.toList());
    }

    private ParkingLotVO toParkingLotVO(ParkingLot lot) {
        ParkingLotVO vo = new ParkingLotVO();
        vo.setId(lot.getId());
        vo.setTenantId(lot.getTenantId());
        vo.setName(lot.getName());
        int totalSpaces = lot.getTotalSpaces() != null ? lot.getTotalSpaces() : 0;
        // 在场车辆以 parking_session 实时统计为准（parking_lot 计数列不随进出场更新）
        int currentVehicles = (int) parkingSessionService.countInByParkingLotIdIgnoreTenant(lot.getId());
        vo.setTotalSpaces(totalSpaces);
        vo.setCurrentVehicles(currentVehicles);
        vo.setRemainingSpaces(Math.max(0, totalSpaces - currentVehicles));
        vo.setStatus(lot.getStatus());
        return vo;
    }

    private BoothRecognitionEventVO toEventVO(RecognitionEventLog event,
                                               ParkingLane lane, Device device) {
        BoothRecognitionEventVO vo = new BoothRecognitionEventVO();
        vo.setEventId(event.getEventId());
        vo.setLogId(event.getId());
        vo.setPlateNumber(event.getPlateNumber());
        vo.setStandardizedPlate(event.getStandardizedPlate());
        vo.setDirection(event.getDirection());
        vo.setEventTime(event.getEventTime());
        vo.setConfidence(event.getConfidence());
        vo.setSource(event.getSource());
        vo.setStatus(event.getStatus());
        vo.setLaneId(event.getLaneId());
        vo.setLaneName(lane != null ? lane.getName() : null);
        vo.setDeviceId(event.getDeviceId());
        vo.setDeviceName(device != null ? device.getName() : null);
        vo.setImagePath(event.getImagePath());
        vo.setPlateImagePath(event.getPlateImagePath());
        return vo;
    }
}
