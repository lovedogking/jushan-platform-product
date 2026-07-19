package com.jushan.system.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台启动时按 gate_mode 重新同步闸机状态（GAP-02）。
 * <p>
 * 启动时查询所有 gate_mode ≠ AUTO 的车道：
 * <ul>
 *   <li>ALWAYS_OPEN → 重新下发 lockGate（适配器 set_io_lock_status 锁定常开）</li>
 *   <li>ALWAYS_CLOSE → unlockGate 取消常开后关闸</li>
 * </ul>
 * <p>
 * 设备重连（状态变为 ONLINE）时的重同步由 DeviceStatusPollingTask 触发。
 *
 * @author Jushan Platform
 * @since 1.3.0
 */
@Slf4j
@Component
public class GateModeSyncRunner {

    private final ParkingLaneMapper parkingLaneMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceAccessClient deviceAccessClient;

    public GateModeSyncRunner(ParkingLaneMapper parkingLaneMapper,
                              DeviceMapper deviceMapper,
                              DeviceAccessClient deviceAccessClient) {
        this.parkingLaneMapper = parkingLaneMapper;
        this.deviceMapper = deviceMapper;
        this.deviceAccessClient = deviceAccessClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("开始按 gate_mode 重新同步闸机状态...");

        List<ParkingLane> lanes = parkingLaneMapper.selectList(
                new LambdaQueryWrapper<ParkingLane>()
                        .ne(ParkingLane::getGateMode, ParkingLane.GATE_MODE_AUTO)
                        .isNotNull(ParkingLane::getGateMode)
        );

        int synced = 0;
        for (ParkingLane lane : lanes) {
            try {
                Device gateDevice = findGateDevice(lane.getId());
                if (gateDevice == null) {
                    log.info("车道无闸机设备，跳过 gate_mode 同步: laneId={}, gateMode={}",
                            lane.getId(), lane.getGateMode());
                    continue;
                }

                if (ParkingLane.GATE_MODE_ALWAYS_OPEN.equals(lane.getGateMode())) {
                    log.info("重同步常开: laneId={}, deviceSn={}", lane.getId(), gateDevice.getDeviceSn());
                    deviceAccessClient.lockGate(gateDevice.getDeviceSn());
                    synced++;
                } else if (ParkingLane.GATE_MODE_ALWAYS_CLOSE.equals(lane.getGateMode())) {
                    log.info("重同步常关: laneId={}, deviceSn={}", lane.getId(), gateDevice.getDeviceSn());
                    // 先取消常开锁定，再关闸
                    deviceAccessClient.unlockGate(gateDevice.getDeviceSn());
                    try {
                        Thread.sleep(500); // 等待解锁完成
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    deviceAccessClient.closeGate(gateDevice.getDeviceSn());
                    synced++;
                }
            } catch (Exception e) {
                log.warn("gate_mode 同步失败: laneId={}, gateMode={}, error={}",
                        lane.getId(), lane.getGateMode(), e.getMessage());
            }
        }

        log.info("gate_mode 同步完成: 共 {} 条非 AUTO 车道，同步 {} 条", lanes.size(), synced);
    }

    /**
     * 设备重连（OFFLINE→ONLINE）时按该车道 gate_mode 重新同步（GAP-02 闭环）。
     * <p>
     * 由 {@link DeviceStatusPollingTask} 检测到设备状态翻转时调用。
     *
     * @param laneId   车道 ID
     * @param deviceSn 设备序列号
     */
    public void syncOnReconnect(Long laneId, String deviceSn) {
        ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
        if (lane == null || lane.getGateMode() == null
                || ParkingLane.GATE_MODE_AUTO.equals(lane.getGateMode())) {
            return;
        }

        try {
            if (ParkingLane.GATE_MODE_ALWAYS_OPEN.equals(lane.getGateMode())) {
                log.info("设备重连重同步常开: laneId={}, deviceSn={}", laneId, deviceSn);
                deviceAccessClient.lockGate(deviceSn);
            } else if (ParkingLane.GATE_MODE_ALWAYS_CLOSE.equals(lane.getGateMode())) {
                log.info("设备重连重同步常关: laneId={}, deviceSn={}", laneId, deviceSn);
                deviceAccessClient.unlockGate(deviceSn);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                deviceAccessClient.closeGate(deviceSn);
            }
        } catch (Exception e) {
            log.warn("设备重连 gate_mode 同步失败: laneId={}, deviceSn={}, gateMode={}, error={}",
                    laneId, deviceSn, lane.getGateMode(), e.getMessage());
        }
    }

    private Device findGateDevice(Long laneId) {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getLaneId, laneId)
                        .eq(Device::getDeviceType, "GATE")
                        .eq(Device::getStatus, "ENABLED")
                        .last("LIMIT 1"));
        if (devices.isEmpty()) {
            // 回退到带 OPEN_GATE 能力的 CAMERA
            devices = deviceMapper.selectList(
                    new LambdaQueryWrapper<Device>()
                            .eq(Device::getLaneId, laneId)
                            .eq(Device::getDeviceType, "CAMERA")
                            .eq(Device::getStatus, "ENABLED")
                            .last("LIMIT 1"));
            if (!devices.isEmpty()) {
                Device camera = devices.get(0);
                if (camera.getCapabilities() != null && camera.getCapabilities().contains("OPEN_GATE")) {
                    return camera;
                }
            }
            return null;
        }
        return devices.get(0);
    }
}
