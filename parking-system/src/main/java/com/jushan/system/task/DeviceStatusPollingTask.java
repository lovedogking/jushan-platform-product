package com.jushan.system.task;

import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.vo.DeviceStatusVO;
import com.jushan.system.ws.BoothWebSocketPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
        try {
            List<Device> devices = deviceMapper.selectList(null);
            for (Device device : devices) {
                try {
                    DeviceStatusDTO statusDTO = deviceAccessClient.getStatus(device.getDeviceSn());
                    String newStatus = mapStatus(statusDTO);

                    // 状态变化时更新数据库
                    if (!newStatus.equals(device.getStatus())) {
                        String oldStatus = device.getStatus();
                        log.info("设备状态变化: deviceSn={}, oldStatus={}, newStatus={}",
                                device.getDeviceSn(), oldStatus, newStatus);
                        device.setStatus(newStatus);
                        deviceMapper.updateById(device);

                        // GAP-02 闭环: 设备 OFFLINE→ONLINE 时重新同步 gate_mode
                        if ("ONLINE".equals(newStatus) && !"ONLINE".equals(oldStatus)) {
                            ParkingLane reconnectLane = findLaneByDevice(device);
                            if (reconnectLane != null) {
                                gateModeSyncRunner.syncOnReconnect(reconnectLane.getId(), device.getDeviceSn());
                            }
                        }
                    }

                    // V1.4 GB-08: 每周期都推送设备状态快照到岗亭端（防止前端 120s 超时误判离线）
                    ParkingLane lane = findLaneByDevice(device);
                    if (lane != null && lane.getLotId() != null) {
                        DeviceStatusVO vo = buildStatusVO(device, lane);
                        boothWebSocketPublisher.sendDeviceStatus(lane.getLotId(), vo);
                    }
                } catch (Exception e) {
                    log.debug("设备状态轮询单设备失败: deviceSn={}, error={}",
                            device.getDeviceSn(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("设备状态轮询任务异常: {}", e.getMessage());
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

    private DeviceStatusVO buildStatusVO(Device device, ParkingLane lane) {
        DeviceStatusVO vo = new DeviceStatusVO();
        vo.setDeviceId(device.getId());
        vo.setDeviceName(device.getName());
        vo.setDeviceType(device.getDeviceType());
        vo.setDeviceStatus(device.getStatus());
        return vo;
    }
}
