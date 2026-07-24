package com.jushan.platform.modules.parking.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.dto.MockRecognitionRequest;
import com.jushan.platform.modules.device.entity.Device;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.booth.entity.RecognitionEventLog;
import com.jushan.platform.modules.device.mapper.DeviceMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.common.mybatis.TenantIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mock/人工识别事件处理服务（T28）。
 * <p>
 * 仅用于 local/test 环境。职责：
 * <ol>
 *   <li>校验请求参数（设备存在、启用、方向匹配）</li>
 *   <li>从可信设备记录推导 tenantId、parkingLotId、laneId</li>
 *   <li>构造标准 {@link RecognitionEventCmd} 并委托 {@link RecognitionEventService} 处理</li>
 * </ol>
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>不信任请求中的任何租户/停车场数据</li>
 *   <li>tenantId 从 parking_lot.tenant_id 推导</li>
 *   <li>parkingLotId 从 device.parking_lot_id 推导</li>
 *   <li>laneId 从 device.lane_id 推导</li>
 *   <li>方向必须与车道方向匹配（ENTRY 设备必须绑定 ENTRY 车道）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class MockRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(MockRecognitionService.class);

    private final DeviceMapper deviceMapper;
    private final ParkingLaneMapper laneMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final RecognitionEventService recognitionEventService;

    public MockRecognitionService(DeviceMapper deviceMapper,
                                   ParkingLaneMapper laneMapper,
                                   ParkingLotMapper parkingLotMapper,
                                   RecognitionEventService recognitionEventService) {
        this.deviceMapper = deviceMapper;
        this.laneMapper = laneMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.recognitionEventService = recognitionEventService;
    }

    /**
     * 处理 Mock/人工识别事件（GAP-07: 移除 RabbitMQ 消费链路后直连 RecognitionEventService）。
     *
     * @param request Mock 触发请求
     * @return 持久化后的事件日志
     */
    @TenantIgnore(reason = "Mock/人工识别事件：从可信设备记录推导租户信息，无需租户拦截")
    public RecognitionEventLog processMockEvent(MockRecognitionRequest request) {
        // 1. 查询设备（必须是 CAMERA 类型、已启用）
        Device device = deviceMapper.selectByIdIgnoreTenant(request.getDeviceId());
        if (device == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "设备不存在: " + request.getDeviceId());
        }
        if (!"ENABLED".equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "设备已停用: " + request.getDeviceId());
        }
        if (!"CAMERA".equals(device.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "设备类型不是相机，无法产生识别事件: deviceType=" + device.getDeviceType());
        }

        // 2. 从设备推导停车场
        ParkingLot parkingLot = parkingLotMapper.selectByIdIgnoreTenant(device.getParkingLotId());
        if (parkingLot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "设备所属停车场不存在: parkingLotId=" + device.getParkingLotId());
        }
        if (!"ENABLED".equals(parkingLot.getStatus())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "停车场已停用: " + parkingLot.getName());
        }

        // 3. 校验车道方向匹配
        ParkingLane lane = null;
        if (device.getLaneId() != null) {
            lane = laneMapper.selectByIdIgnoreTenant(device.getLaneId());
            if (lane == null) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND,
                        "设备绑定车道不存在: laneId=" + device.getLaneId());
            }
            if (lane.getStatus() == null || lane.getStatus() != 1) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "车道已停用: " + lane.getName());
            }
            String direction = request.getDirection();
            if (lane.getType() != null && lane.getType() != 3) {
                String laneDirStr = lane.getType() == 1 ? "ENTRY" : "EXIT";
                if (!direction.equals(laneDirStr)) {
                    throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                            String.format("方向不匹配: 事件方向=%s, 车道方向=%s (lane=%s)",
                                    direction, laneDirStr, lane.getName()));
                }
            }
        }

        // 4. GAP-07: 直连 RecognitionEventService.handleEvent()（移除 RabbitMQ 消费链路后）
        RecognitionEventCmd cmd = new RecognitionEventCmd();
        cmd.setPlateNumber(request.getPlateNumber());
        cmd.setDirection(request.getDirection());
        cmd.setLaneId(device.getLaneId());
        cmd.setParkingLotId(parkingLot.getId());
        cmd.setCaptureImage(request.getImagePath());

        log.info("Mock 识别事件直连处理: plate={} direction={} parkingLot={}",
                cmd.getPlateNumber(), cmd.getDirection(), parkingLot.getName());

        // 设置租户上下文（Mock 无 JWT Token，需手动注入）
        TenantContext.Snapshot previousContext = TenantContext.get();
        try {
            TenantContext.set(new TenantContext.Snapshot(
                    parkingLot.getTenantId(),
                    0L,
                    "platform",
                    null,
                    null
            ));
            recognitionEventService.handleEvent(cmd);
        } finally {
            if (previousContext != null) {
                TenantContext.set(previousContext);
            } else {
                TenantContext.clear();
            }
        }

        // 返回日志占位（原 RecognitionEventLog 已不再由本服务持久化）
        RecognitionEventLog logEntry = new RecognitionEventLog();
        logEntry.setId(0L); // 占位：Map.of 不允许 null 值，避免 Controller 组包 NPE
        logEntry.setEventId("MOCK-" + java.util.UUID.randomUUID());
        logEntry.setPlateNumber(request.getPlateNumber());
        logEntry.setDirection(request.getDirection());
        logEntry.setSource("MOCK");
        logEntry.setEventTime(java.time.LocalDateTime.now());
        return logEntry;
    }
}
