package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.dto.MockRecognitionRequest;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.event.EventSource;
import com.jushan.system.event.PlateStandardizer;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.event.RecognitionEventPublisher;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mybatis.TenantIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * Mock/人工识别事件处理服务（T28）。
 * <p>
 * 仅用于 local/test 环境。职责：
 * <ol>
 *   <li>校验请求参数（设备存在、启用、方向匹配）</li>
 *   <li>从可信设备记录推导 tenantId、parkingLotId、laneId</li>
 *   <li>构造标准 {@link RecognitionEventPayload} 并委托 {@link RecognitionEventPublisher} 发布</li>
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
    private final RecognitionEventPublisher eventPublisher;

    public MockRecognitionService(DeviceMapper deviceMapper,
                                   ParkingLaneMapper laneMapper,
                                   ParkingLotMapper parkingLotMapper,
                                   RecognitionEventPublisher eventPublisher) {
        this.deviceMapper = deviceMapper;
        this.laneMapper = laneMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理 Mock/人工识别事件。
     *
     * @param request Mock 触发请求
     * @return 持久化后的事件日志
     */
    @TenantIgnore(reason = "Mock/人工识别事件：从可信设备记录推导租户信息，无需租户拦截")
    public RecognitionEventLog processMockEvent(MockRecognitionRequest request) {
        // 1. 查询设备（必须是 CAMERA 类型、已启用）
        // Mock 接口无租户上下文，使用忽略租户拦截器的方法
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
            // 入场事件只能匹配入口方向；出场事件只能匹配出口方向
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

        // 4. 标准化车牌号
        String standardizedPlate = PlateStandardizer.normalize(request.getPlateNumber());
        if (standardizedPlate == null || standardizedPlate.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车牌号标准化后为空");
        }

        // 5. 构造标准事件载荷（tenantId 和 parkingLotId 从可信记录推导）
        RecognitionEventPayload payload = RecognitionEventPayload.of(
                UUID.randomUUID().toString(),
                standardizedPlate,
                request.getDirection(),
                EventSource.MOCK)
                .deviceId(device.getId())
                .laneId(device.getLaneId())
                .parkingLotId(parkingLot.getId())
                .tenantId(parkingLot.getTenantId())
                .confidence(request.getConfidence())
                .imagePath(request.getImagePath())
                .plateImagePath(request.getPlateImagePath())
                .rawData("Mock trigger via T28 test entry");

        // 6. 发布事件（持久化 + MQ）
        RecognitionEventLog logEntry = eventPublisher.publish(payload);

        log.info("Mock 识别事件处理完成: eventId={} plate={} standardized={} direction={} parkingLot={} tenant={}",
                payload.getEventId(), request.getPlateNumber(), payload.getPlateNumber(),
                payload.getDirection(), parkingLot.getName(), parkingLot.getTenantId());

        return logEntry;
    }
}
