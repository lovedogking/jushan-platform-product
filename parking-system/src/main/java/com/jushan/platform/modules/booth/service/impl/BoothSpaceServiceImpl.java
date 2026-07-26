package com.jushan.platform.modules.booth.service.impl;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.SpaceAdjustCmd;
import com.jushan.platform.modules.booth.service.BoothSpaceService;
import com.jushan.platform.modules.booth.ws.BoothWebSocketPublisher;
import com.jushan.platform.modules.parking.dto.ParkingLotCapacityRequest;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.service.ParkingLotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗亭余位调整服务实现。
 * <p>
 * 委托 {@link ParkingLotService#updateCapacity} 执行实际 DB 更新与审计日志写入，
 * 更新完成后通过 {@link BoothWebSocketPublisher} 推送到所有岗亭客户端。
 *
 * @author Jushan Platform
 * @since 1.5.3
 */
@Slf4j
@Service
public class BoothSpaceServiceImpl implements BoothSpaceService {

    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLotService parkingLotService;
    private final BoothWebSocketPublisher wsPublisher;

    public BoothSpaceServiceImpl(ParkingLotMapper parkingLotMapper,
                                  ParkingLotService parkingLotService,
                                  BoothWebSocketPublisher wsPublisher) {
        this.parkingLotMapper = parkingLotMapper;
        this.parkingLotService = parkingLotService;
        this.wsPublisher = wsPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(SpaceAdjustCmd cmd) {
        String mode = cmd.getMode().trim().toUpperCase();
        int value = cmd.getValue();
        Long parkingLotId = cmd.getParkingLotId();
        String reason = cmd.getReason().trim();

        // 1. 校验模式
        if (!SpaceAdjustCmd.MODE_SET.equals(mode) && !SpaceAdjustCmd.MODE_ADJUST.equals(mode)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的操作模式: " + mode + "，仅支持 SET / ADJUST");
        }

        // 2. 校验停车场存在且属于当前租户
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        Long tenantId = TenantContext.getTenantId();
        // 平台用户可跨租户操作；租户用户只能操作本租户停车场
        if (tenantId != null && !tenantId.equals(lot.getTenantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作该停车场");
        }

        // 3. 计算目标值
        int targetValue;
        if (SpaceAdjustCmd.MODE_SET.equals(mode)) {
            if (value < 0) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "剩余车位数不能为负数");
            }
            targetValue = value;
        } else {
            // ADJUST 模式
            int currentRemaining = lot.getRemainingSpaces() != null ? lot.getRemainingSpaces() : 0;
            targetValue = currentRemaining + value;
            if (targetValue < 0) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "调整后剩余车位不能为负数（当前: " + currentRemaining + "，增量: " + value + "）");
            }
        }

        // 4. 如果目标值等于当前值，跳过
        int currentRemaining = lot.getRemainingSpaces() != null ? lot.getRemainingSpaces() : 0;
        if (targetValue == currentRemaining) {
            log.info("余位未变化，跳过更新: parkingLotId={}, current={}, target={}",
                    parkingLotId, currentRemaining, targetValue);
            return;
        }

        // 4b. 剩余车位不能超过总车位
        int totalSpaces = lot.getTotalSpaces() != null ? lot.getTotalSpaces() : 0;
        if (targetValue > totalSpaces) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "剩余车位数（" + targetValue + "）不能超过总车位数（" + totalSpaces + "）");
        }

        log.info("岗亭余位调整: parkingLotId={}, mode={}, current={}, target={}, reason={}",
                parkingLotId, mode, currentRemaining, targetValue, reason);

        // 5. 委托 ParkingLotService 执行实际更新（含乐观锁 + 审计日志）
        ParkingLotCapacityRequest capacityRequest = new ParkingLotCapacityRequest();
        capacityRequest.setFieldName(ParkingLotService.FIELD_REMAINING_SPACES);
        capacityRequest.setValue(targetValue);
        capacityRequest.setReason("【岗亭调整】" + reason);
        parkingLotService.updateCapacity(parkingLotId, capacityRequest);

        // 6. 查询更新后的停车场并推送 WebSocket
        ParkingLot updated = parkingLotMapper.selectByIdIgnoreTenant(parkingLotId);
        if (updated != null) {
            wsPublisher.sendSpaceUpdate(
                    parkingLotId,
                    updated.getRemainingSpaces(),
                    updated.getCurrentVehicles(),
                    updated.getTotalSpaces()
            );
        }

        log.info("岗亭余位调整完成并已推送: parkingLotId={}, remaining={}",
                parkingLotId, updated != null ? updated.getRemainingSpaces() : "?");
    }
}
