package com.jushan.platform.modules.booth.service;
import com.jushan.platform.modules.device.service.DeviceService;import com.jushan.platform.modules.parking.service.MockPaymentService;import com.jushan.platform.modules.parking.service.ParkingOrderService;import com.jushan.platform.modules.parking.service.BillingEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.platform.modules.parking.entity.ExitRecord;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.mapper.ExitRecordMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 临时车牌服务（任务包 3-4）。
 * <p>
 * 岗亭端手动操作临时车牌的业务逻辑：
 * <ul>
 *   <li>手动入场：创建在场记录 + 预订单 + 开闸</li>
 *   <li>出场匹配：查在场记录 → 计费 → 现金订单 → 完成记录 → 开闸 → 出场记录</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class TempPlateService {

    private static final Logger log = LoggerFactory.getLogger(TempPlateService.class);

    private final ParkingRecordMapper recordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingOrderMapper orderMapper;
    private final ParkingOrderService parkingOrderService;
    private final ExitRecordMapper exitRecordMapper;
    private final BillingEngine billingEngine;
    private final DeviceService deviceService;
    private final MockPaymentService mockPaymentService;
    private final com.jushan.platform.modules.parking.service.ParkingSessionService parkingSessionService;

    public TempPlateService(ParkingRecordMapper recordMapper,
                             ParkingLotMapper parkingLotMapper,
                             ParkingOrderMapper orderMapper,
                             ParkingOrderService parkingOrderService,
                             ExitRecordMapper exitRecordMapper,
                             BillingEngine billingEngine,
                             DeviceService deviceService,
                             MockPaymentService mockPaymentService,
                             com.jushan.platform.modules.parking.service.ParkingSessionService parkingSessionService) {
        this.recordMapper = recordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.orderMapper = orderMapper;
        this.parkingOrderService = parkingOrderService;
        this.exitRecordMapper = exitRecordMapper;
        this.billingEngine = billingEngine;
        this.deviceService = deviceService;
        this.mockPaymentService = mockPaymentService;
        this.parkingSessionService = parkingSessionService;
    }

    // ==================== 手动入场 ====================

    /**
     * 岗亭手动无牌车入场：创建在场记录 + 预订单 + 开闸。
     *
     * @param parkingLotId 车场 ID
     * @param laneId       车道 ID
     * @param tempPlate    临时车牌号
     * @param boothUserId  岗亭操作员 ID
     * @return 创建的停车记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingRecord manualEntry(Long parkingLotId, Long laneId,
                                      String tempPlate, Long boothUserId) {
        // 1. 校验临时车牌格式
        validateTempPlateFormat(tempPlate);

        // 2. 检查是否已有同临牌的 PARKING 记录
        ParkingRecord existing = recordMapper.selectOne(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStandardizedPlate, tempPlate)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING));
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "该临时车牌已有在场记录: " + tempPlate);
        }

        // 3. 获取车场信息
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }

        // 4. 创建 ParkingRecord（tempPlateFlag=1）
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(lot.getTenantId());
        record.setParkingLotId(parkingLotId);
        record.setLaneId(laneId);
        record.setStandardizedPlate(tempPlate);
        record.setTempPlateFlag(1);
        record.setStatus(ParkingRecord.STATUS_PARKING);
        record.setEntryTime(LocalDateTime.now());
        record.setEntryImagePath(null);
        recordMapper.insert(record);

        // 4.5 同步创建 ParkingSession（人工补录，纳入在场统计与通行记录）
        try {
            com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd sessionCmd =
                    new com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd();
            sessionCmd.setTenantId(record.getTenantId());
            sessionCmd.setParkingLotId(parkingLotId);
            sessionCmd.setLaneId(laneId);
            sessionCmd.setPlateNumber(tempPlate);
            sessionCmd.setVehicleType("TEMP");
            sessionCmd.setEntryOperator(boothUserId);
            sessionCmd.setEntryTrigger("manual_entry");
            parkingSessionService.entry(sessionCmd);
        } catch (Exception e) {
            log.warn("无牌车入场 ParkingSession 同步失败（不影响主业务）: plate={} error={}", tempPlate, e.getMessage());
        }

        // 5. 创建预订单
        ParkingOrder preOrder = parkingOrderService.createPreOrder(record);
        preOrder.setTempPlateFlag(1);
        orderMapper.updateById(preOrder);

        // 6. 更新停车场容量
        parkingLotMapper.update(null,
                new LambdaUpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles + 1")
                        .setSql("remaining_spaces = remaining_spaces - 1")
                        .eq(ParkingLot::getId, parkingLotId));

        // 7. 开闸放行
        try {
            deviceService.openGateByLane(laneId, "岗亭手动无牌车入场(" + tempPlate + ")");
        } catch (Exception e) {
            log.warn("岗亭手动入场开闸失败: plate={} laneId={} error={}",
                    tempPlate, laneId, e.getMessage());
        }

        log.info("岗亭手动无牌车入场: recordId={} tempPlate={} lotId={} userId={}",
                record.getId(), tempPlate, parkingLotId, boothUserId);
        return record;
    }

    // ==================== 出场匹配 ====================

    /**
     * 岗亭手动无牌车出场匹配计费。
     * <p>
     * 完整链路：查在场记录 → 计费 → 现金订单 → 模拟支付 → 完成记录 → 减容量 → 开闸 → 出场记录。
     *
     * @param tempPlate    临时车牌号
     * @param parkingLotId 车场 ID
     * @param laneId       车道 ID
     * @param boothUserId  岗亭操作员 ID
     * @return 出场处理结果（简化版，含 exitRecordId / orderId / feeCents）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExitResult handleExitMatch(String tempPlate,
                                                    Long parkingLotId,
                                                    Long laneId,
                                                    Long boothUserId) {
        // 1. 校验临时车牌格式
        validateTempPlateFormat(tempPlate);

        // 2. 查询在场记录
        ParkingRecord record = recordMapper.selectOne(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStandardizedPlate, tempPlate)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING));
        if (record == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "未找到该临时车牌的在场记录: " + tempPlate);
        }

        // 3. 计算费用
        LocalDateTime exitTime = LocalDateTime.now();
        int feeCents;
        if (record.getRuleSnapshot() != null && !record.getRuleSnapshot().isBlank()) {
            try {
                feeCents = billingEngine.calculateFeeFromSnapshot(
                        record.getRuleSnapshot(), record.getEntryTime(), exitTime);
            } catch (BusinessException e) {
                feeCents = billingEngine.calculateFee(
                        parkingLotId, record.getEntryTime(), exitTime);
            }
        } else {
            feeCents = billingEngine.calculateFee(
                    parkingLotId, record.getEntryTime(), exitTime);
        }

        // 4. 创建订单（PENDING_PAY, payScene=AT_EXIT, payChannel=CASH）
        ParkingOrder order = parkingOrderService.createOrderInternal(
                record, feeCents, null,
                ParkingOrder.PAY_SCENE_AT_EXIT, laneId, null,
                "岗亭无牌车出场计费");
        order.setTempPlateFlag(1);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
        orderMapper.updateById(order);

        // 5. 模拟支付：preparePay → confirmPay
        mockPaymentService.preparePay(order);
        boolean paid = mockPaymentService.confirmPay(order.getId(),
                "BOOTH_USER_" + boothUserId);
        if (!paid) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "模拟现金支付失败，请重试");
        }

        // 6. 完成停车记录
        record.setStatus(ParkingRecord.STATUS_COMPLETED);
        record.setExitTime(exitTime);
        recordMapper.updateById(record);

        // 6.5 同步 ParkingSession 出场
        try {
            var sessionVO = parkingSessionService.getInByPlateAndLot(tempPlate, parkingLotId);
            if (sessionVO != null) {
                var sessionExitCmd = new com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd();
                sessionExitCmd.setSessionId(sessionVO.getId());
                sessionExitCmd.setExitLaneId(laneId);
                sessionExitCmd.setExitOperator(boothUserId);
                sessionExitCmd.setFeeAmount(java.math.BigDecimal.valueOf(feeCents).movePointLeft(2));
                sessionExitCmd.setPaidAmount(java.math.BigDecimal.valueOf(feeCents).movePointLeft(2));
                sessionExitCmd.setOrderId(order.getId());
                sessionExitCmd.setParkingRecordId(record.getId());
                parkingSessionService.exit(sessionExitCmd);
            }
        } catch (Exception e) {
            log.warn("无牌车出场 ParkingSession 同步失败（不影响主业务）: plate={} error={}", tempPlate, e.getMessage());
        }

        // 7. 完成订单（PAID → COMPLETED）
        parkingOrderService.completeOrder(order.getId(), exitTime);

        // 8. 减停车场容量
        parkingLotMapper.update(null,
                new UpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles - 1")
                        .setSql("remaining_spaces = remaining_spaces + 1")
                        .eq("id", parkingLotId));

        // 9. 开闸放行
        try {
            deviceService.openGateByLane(laneId, "岗亭手动出场开闸(" + tempPlate + ")");
        } catch (Exception e) {
            log.warn("岗亭手动出场开闸失败: plate={} laneId={} error={}",
                    tempPlate, laneId, e.getMessage());
        }

        // 10. 创建出场记录
        ExitRecord exitRecord = new ExitRecord();
        exitRecord.setTenantId(record.getTenantId());
        exitRecord.setParkingLotId(parkingLotId);
        exitRecord.setParkingRecordId(record.getId());
        exitRecord.setLaneId(laneId);
        exitRecord.setStandardizedPlate(tempPlate);
        exitRecord.setExitTime(exitTime);
        exitRecord.setFeeCents(Math.max(0, feeCents));
        exitRecord.setPaidCents(Math.max(0, feeCents));
        exitRecord.setReleaseDecision(ExitRecord.DECISION_PAID);
        exitRecord.setOrderId(order.getId());
        exitRecord.setReason("岗亭手动无牌车出场，现金支付");
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());
        exitRecordMapper.insert(exitRecord);

        log.info("岗亭手动无牌车出场: recordId={} tempPlate={} feeCents={} orderId={} userId={}",
                record.getId(), tempPlate, feeCents, order.getId(), boothUserId);

        return ExitResult.of(
                ReleaseDecision.paid(),
                exitRecord.getId(), order.getId(), feeCents);
    }

    // ==================== 车牌号格式校验 ====================

    /**
     * 校验临时车牌格式：非空 + 长度≤20 + 不以空格开头。
     */
    private void validateTempPlateFormat(String tempPlate) {
        if (tempPlate == null || tempPlate.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "临时车牌号不能为空");
        }
        if (tempPlate.length() > 20) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "临时车牌号长度不能超过20位");
        }
        if (tempPlate.startsWith(" ")) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "临时车牌号不能以空格开头");
        }
    }
}
