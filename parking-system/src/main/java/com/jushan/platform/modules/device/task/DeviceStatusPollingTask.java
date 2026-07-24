package com.jushan.platform.modules.device.task;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.device.client.DeviceAccessClient;
import com.jushan.platform.modules.device.client.dto.DeviceStatusDTO;
import com.jushan.platform.modules.device.entity.Device;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.device.mapper.DeviceMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.device.vo.DeviceStatusVO;
import com.jushan.platform.modules.booth.ws.BoothWebSocketPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备状态轮询定时任务（GAP-05）。
 * <p>
 * 每 30 秒轮询适配器设备状态，状态变化时通过 WebSocket 推送到岗亭端
 * /topic/booth/{lotId}/device-status。
 *
 * @author Jushan Platform
 * @since 1.3.0
 */
@Slf4j
@Component
public class DeviceStatusPollingTask {

    private final DeviceMapper deviceMapper;
    private final ParkingLaneMapper parkingLaneMapper;
    private final DeviceAccessClient deviceAccessClient;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final GateModeSyncRunner gateModeSyncRunner;

    /** 各设备最近一次轮询到的连接状态（内存态，用于变化检测与重连判断） */
    private final Map<String, String> lastKnownStatus = new ConcurrentHashMap<>();

    public DeviceStatusPollingTask(DeviceMapper deviceMapper,
                                   ParkingLaneMapper parkingLaneMapper,
                                   DeviceAccessClient deviceAccessClient,
                                   BoothWebSocketPublisher boothWebSocketPublisher,
                                   GateModeSyncRunner gateModeSyncRunner) {
        this.deviceMapper = deviceMapper;
        this.parkingLaneMapper = parkingLaneMapper;
        this.deviceAccessClient = deviceAccessClient;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.gateModeSyncRunner = gateModeSyncRunner;
    }

    @Scheduled(fixedRate = 30_000)
    public void pollDeviceStatus() {
        // 定时任务无登录上下文：注入平台用户快照，跳过租户行级拦截（全平台设备轮询）
        TenantContext.Snapshot previous = TenantContext.get();
        TenantContext.set(new TenantContext.Snapshot(null, 0L, TenantContext.USER_TYPE_PLATFORM, null, null));
        try {
            List<Device> devices = deviceMapper.selectList(null);
            for (Device device : devices) {
                try {
                    DeviceStatusDTO statusDTO = deviceAccessClient.getStatus(device.getDeviceSn());
                    String newStatus = mapStatus(statusDTO);

                    // 连接状态变化检测（内存态，不写 device.status —— 该列是 ENABLED/DISABLED 启用标记）
                    String oldStatus = lastKnownStatus.put(device.getDeviceSn(), newStatus);
                    if (oldStatus != null && !newStatus.equals(oldStatus)) {
                        log.info("设备连接状态变化: deviceSn={}, oldStatus={}, newStatus={}",
                                device.getDeviceSn(), oldStatus, newStatus);

                        // GAP-02 闭环: 设备 OFFLINE→ONLINE 时重新同步 gate_mode
                        if ("ONLINE".equals(newStatus)) {
                            ParkingLane reconnectLane = findLaneByDevice(device);
                            if (reconnectLane != null) {
                                gateModeSyncRunner.syncOnReconnect(reconnectLane.getId(), device.getDeviceSn());
                            }
                        }
                    }

                    // V1.4 GB-08: 每周期都推送设备状态快照到岗亭端（防止前端 120s 超时误判离线）
                    ParkingLane lane = findLaneByDevice(device);
                    if (lane != null && lane.getLotId() != null) {
                        DeviceStatusVO vo = buildStatusVO(device, lane, newStatus);
                        boothWebSocketPublisher.sendDeviceStatus(lane.getLotId(), vo);
                    }
                } catch (Exception e) {
                    log.debug("设备状态轮询单设备失败: deviceSn={}, error={}",
                            device.getDeviceSn(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("设备状态轮询任务异常: {}", e.getMessage());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private String mapStatus(DeviceStatusDTO dto) {
        if (dto == null) {
            return "OFFLINE";
        }
        if (Boolean.TRUE.equals(dto.getOnline())) {
            return "ONLINE";
        }
        return "OFFLINE";
    }

    private ParkingLane findLaneByDevice(Device device) {
        if (device.getLaneId() == null) {
            return null;
        }
        try {
            return parkingLaneMapper.selectByIdIgnoreTenant(device.getLaneId());
        } catch (Exception e) {
            return null;
        }
    }

    private DeviceStatusVO buildStatusVO(Device device, ParkingLane lane, String connStatus) {
        DeviceStatusVO vo = new DeviceStatusVO();
        vo.setDeviceId(device.getId());
        vo.setDeviceName(device.getName());
        vo.setDeviceType(device.getDeviceType());
        vo.setDeviceStatus(connStatus);
        return vo;
    }
}
