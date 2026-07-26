package com.jushan.platform.modules.device.service;

import com.jushan.platform.modules.device.client.DeviceAccessClient;
import com.jushan.platform.modules.device.entity.Device;
import com.jushan.platform.modules.device.mapper.DeviceMapper;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备白名单同步服务。
 * <p>
 * 当平台月卡/VIP/固定车变更时，将白名单实时同步到岗亭车道设备，
 * 使设备在网络断开时仍可本地判定放行。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class DeviceWhitelistSyncService {

    private static final List<String> SYNC_TYPES = List.of("MONTHLY", "VIP", "FIXED");

    private final DeviceMapper deviceMapper;
    private final ParkingLaneMapper laneMapper;
    private final DeviceAccessClient deviceAccessClient;

    public DeviceWhitelistSyncService(DeviceMapper deviceMapper,
                                      ParkingLaneMapper laneMapper,
                                      DeviceAccessClient deviceAccessClient) {
        this.deviceMapper = deviceMapper;
        this.laneMapper = laneMapper;
        this.deviceAccessClient = deviceAccessClient;
    }

    /**
     * 车辆新增或变更为白名单类型时，同步到相关设备。
     */
    public void syncVehicleToDevices(SysVehicle vehicle, List<Long> laneIds) {
        if (vehicle == null || vehicle.getVehicleType() == null) return;
        if (!SYNC_TYPES.contains(vehicle.getVehicleType())) return;
        String plate = vehicle.getPlateNumber();
        if (plate == null || plate.isBlank()) return;

        for (Long laneId : laneIds) {
            try {
                ParkingLane lane = laneMapper.selectById(laneId);
                if (lane == null) continue;
                Device device = resolveDevice(lane);
                if (device == null || !"ENABLED".equals(device.getStatus())) continue;

                // 通过 DA 下发白名单 ADD
                deviceAccessClient.syncWhitelist(device.getDeviceSn(), "ADD", plate);
                log.info("白名单同步(ADD): vehicleId={}, plate={}, type={}, laneId={}, deviceSn={}",
                        vehicle.getId(), plate, vehicle.getVehicleType(), laneId, device.getDeviceSn());
            } catch (Exception e) {
                log.warn("白名单同步失败: vehicleId={}, plate={}, laneId={}, error={}",
                        vehicle.getId(), plate, laneId, e.getMessage());
            }
        }
    }

    /**
     * 车辆删除或变更为非白名单类型时，从设备移除。
     */
    public void removeVehicleFromDevices(SysVehicle vehicle, List<Long> laneIds) {
        if (vehicle == null) return;
        String plate = vehicle.getPlateNumber();
        if (plate == null || plate.isBlank()) return;

        for (Long laneId : laneIds) {
            try {
                ParkingLane lane = laneMapper.selectById(laneId);
                if (lane == null) continue;
                Device device = resolveDevice(lane);
                if (device == null) continue;

                deviceAccessClient.syncWhitelist(device.getDeviceSn(), "DELETE", plate);
                log.info("白名单同步(DELETE): vehicleId={}, plate={}, laneId={}, deviceSn={}",
                        vehicle.getId(), plate, laneId, device.getDeviceSn());
            } catch (Exception e) {
                log.warn("白名单移除同步失败: vehicleId={}, plate={}, laneId={}, error={}",
                        vehicle.getId(), plate, laneId, e.getMessage());
            }
        }
    }

    private Device resolveDevice(ParkingLane lane) {
        if (lane.getGateDeviceId() != null) {
            return deviceMapper.selectById(lane.getGateDeviceId());
        }
        return null;
    }
}
