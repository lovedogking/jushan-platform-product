package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.system.entity.ExitRecord;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.ExitRecordMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.ws.BoothWebSocketPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 出口识别处理服务（P004）。
 * <p>
 * 处理 EXIT 方向识别事件，完成：
 * <ol>
 *   <li>匹配同停车场未结（PARKING）停车记录</li>
 *   <li>调用计费引擎计算费用</li>
 *   <li>根据费用和支付状态决定放行策略</li>
 *   <li>生成订单（骨架）和出场记录</li>
 *   <li>更新停车记录状态（条件更新）</li>
 * </ol>
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>不直接调用 Device Access 开闸（gate/open 在 v0.2 中未实现）</li>
 *   <li>未支付车辆不自动放行</li>
 *   <li>tenantId / parkingLotId 从可信停车记录推导，不信任外部传入</li>
 *   <li>停车记录状态使用条件更新，禁止整实体覆盖</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ExitService {

    private static final Logger log = LoggerFactory.getLogger(ExitService.class);

    private final ParkingRecordMapper recordMapper;
    private final ExitRecordMapper exitRecordMapper;
    private final ParkingOrderService parkingOrderService;
    private final ParkingLotMapper parkingLotMapper;
    private final BillingEngine billingEngine;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final ParkingSessionService parkingSessionService;
    private final PrepaidDeductionService prepaidDeductionService;
    private final FixedSpaceService fixedSpaceService;
    private final DeviceService deviceService;

    public ExitService(ParkingRecordMapper recordMapper,
                        ExitRecordMapper exitRecordMapper,
                        ParkingOrderService parkingOrderService,
                        ParkingLotMapper parkingLotMapper,
                        BillingEngine billingEngine,
                        BoothWebSocketPublisher boothWebSocketPublisher,
                        ParkingSessionService parkingSessionService,
                        PrepaidDeductionService prepaidDeductionService,
                        FixedSpaceService fixedSpaceService,
                        DeviceService deviceService) {
        this.recordMapper = recordMapper;
        this.exitRecordMapper = exitRecordMapper;
        this.parkingOrderService = parkingOrderService;
        this.parkingLotMapper = parkingLotMapper;
        this.billingEngine = billingEngine;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.parkingSessionService = parkingSessionService;
        this.prepaidDeductionService = prepaidDeductionService;
        this.fixedSpaceService = fixedSpaceService;
        this.deviceService = deviceService;
    }

    /**
     * 处理车辆出场。
     * <p>
     * 匹配到未结记录时：计算费用 → 生成订单 → 决定放行策略 → 创建出场记录。
     * 未匹配到记录时：创建 NO_RECORD 出场记录，供运营排查。
     *
     * @param payload           识别事件载荷（校验通过，tenantId/parkingLotId 已从可信记录推导）
     * @param standardizedPlate 标准化车牌号
     * @return 出场处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ExitResult handleExit(RecognitionEventPayload payload, String standardizedPlate) {
        Long parkingLotId = payload.getParkingLotId();
        LocalDateTime exitTime = payload.getEventTime() != null
                ? payload.getEventTime() : LocalDateTime.now();

        // 1. 匹配未结停车记录
        ParkingRecord record = findActiveRecord(parkingLotId, standardizedPlate);

        if (record == null) {
            log.warn("出口未匹配到在场记录: plate={} parkingLotId={}",
                    standardizedPlate, parkingLotId);
            ExitRecord noRecord = createNoRecordExit(payload, standardizedPlate, exitTime);
            return ExitResult.noRecord(noRecord.getId(), "未找到在场记录");
        }

        // 2a. 检查固定车位绑定（生效中的固定车位车辆跳过计费）
        boolean isFixedSpace = record.getTenantId() != null
                && fixedSpaceService.hasActiveBinding(standardizedPlate, parkingLotId, record.getTenantId());

        // 2b. 计算费用（NEW_ENTRY_ONLY 优先使用入场快照）
        int feeCents;
        if (isFixedSpace) {
            feeCents = 0;
            log.info("固定车位车辆出场，跳过计费: plate={} parkingLotId={}", standardizedPlate, parkingLotId);
        } else if (record.getRuleSnapshot() != null && !record.getRuleSnapshot().isBlank()) {
            try {
                feeCents = billingEngine.calculateFeeFromSnapshot(
                        record.getRuleSnapshot(), record.getEntryTime(), exitTime);
                log.info("使用入场规则快照计费: recordId={} snapshotHash={}",
                        record.getId(), record.getRuleSnapshot().hashCode());
            } catch (BusinessException e) {
                log.warn("规则快照计费失败，回退到当前规则: recordId={} error={}",
                        record.getId(), e.getMessage());
                feeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), exitTime);
            }
        } else {
            feeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), exitTime);
        }

        // 3. 获取订单（任务包 1-2）：优先复用该记录现有订单（入场预订单/提前缴费订单）；
        //    预订单→计费置待支付（零费→直接完成）；已建单（待支付/支付中/已支付）直接复用；
        //    查不到时按现行逻辑建单（兼容旧无预订单在场数据）。固定车位车辆免费通行，不生成临停订单。
        //    任务包 2-1：PAID 订单需判定窗口期（窗口期内自动开闸 / 超期重算）。
        ParkingOrder order = null;
        boolean windowPass = false;
        if (!isFixedSpace) {
            ParkingOrder paidOrder = parkingOrderService.findPaidOrderForRecord(record.getId());
            if (paidOrder != null) {
                // 已支付订单：判定窗口期
                if (paidOrder.getPayWindowDeadline() != null
                        && !LocalDateTime.now().isAfter(paidOrder.getPayWindowDeadline())) {
                    // 窗口期内：自动开闸放行
                    order = paidOrder;
                    windowPass = true;
                    log.info("窗口期内出场，自动放行: orderId={} plate={} deadline={}",
                            order.getId(), standardizedPlate, paidOrder.getPayWindowDeadline());
                } else {
                    // 超期未出场：保留原订单 PAID，重新计费生成新订单
                    log.info("支付窗口期已过，触发重算: orderId={} plate={} deadline={}",
                            paidOrder.getId(), standardizedPlate, paidOrder.getPayWindowDeadline());
                    ParkingOrder recalcOrder = parkingOrderService.createRecalcOrder(
                            record, feeCents, paidOrder.getId(), payload.getLaneId());
                    order = recalcOrder;
                    // 推送 WebSocket：超期重算通知
                    boothWebSocketPublisher.sendPaymentCompleted(
                            record.getParkingLotId(), recalcOrder.getId(),
                            recalcOrder.getOrderNo(), record.getStandardizedPlate(), feeCents);
                }
            } else {
                ParkingOrder existing = parkingOrderService.findReusableOrderForRecord(record.getId());
                if (existing == null) {
                    // 兼容期：旧在场记录无预订单，保持原出场建单逻辑
                    order = parkingOrderService.createOrder(record, feeCents, null,
                            ParkingOrder.PAY_SCENE_AT_EXIT);
                } else if (ParkingOrder.STATUS_PRE_ORDER.equals(existing.getStatus())) {
                    if (feeCents <= 0) {
                        // 免费放行：预订单直接完成（PRE_ORDER → COMPLETED）
                        parkingOrderService.preOrderToCompleted(existing.getId(), exitTime);
                    } else {
                        // 出场计费：预订单 → 待支付（PRE_ORDER → PENDING_PAY），场景 AT_EXIT
                        parkingOrderService.preOrderToPending(existing.getId(), feeCents,
                                LocalDateTime.now().plusMinutes(15),
                                ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId());
                    }
                    order = parkingOrderService.getById(existing.getId());
                } else {
                    // 提前缴费/岗亭等已建订单（待支付/支付中），直接复用，避免重复建单
                    order = existing;
                }
            }
        }

        // 3b. 窗口期内自动开闸放行（在订单完成前先开闸）
        if (windowPass) {
            try {
                // 开闸：使用 payload 中的 laneId
                deviceService.openGateByLane(payload.getLaneId(), "窗口期内出场自动开闸");
            } catch (Exception e) {
                log.warn("窗口期自动开闸失败: orderId={} laneId={} error={}",
                        order.getId(), payload.getLaneId(), e.getMessage());
                // 开闸失败不阻断流程，订单仍标记完成
            }
        }

        // 3b. 储值车余额自动扣费（在订单创建后、放行决策前，窗口期内已付订单跳过）
        int actualPaidCents = 0;
        if (feeCents > 0 && !windowPass) {
            PrepaidDeductionService.DeductionResult deduction =
                    prepaidDeductionService.tryDeduct(record, feeCents, order);
            if (deduction.isApplicable() && deduction.getDeductedCents() > 0) {
                actualPaidCents = deduction.getDeductedCents();
                boolean fullyPaid = deduction.isFullyCovered();
                parkingOrderService.applyBalancePayment(order.getId(), actualPaidCents, fullyPaid);
                // 重新查询订单以获取更新后的状态
                order = parkingOrderService.getById(order.getId());
                log.info("储值车余额扣费完成: orderId={} paidCents={} fullyPaid={} walletLogId={}",
                        order.getId(), actualPaidCents, fullyPaid, deduction.getWalletLogId());
            }
        }

        // 4. 决定放行策略
        ReleaseDecision decision = resolveReleaseDecision(feeCents, order);

        // 5. 仅当允许放行时，才更新停车记录状态（条件更新）
        boolean recordCompleted = false;
        if (decision.isAllowExit()) {
            recordCompleted = completeParkingRecord(record, payload, exitTime);
            if (!recordCompleted) {
                log.warn("停车记录状态更新失败（可能已被并发处理）: recordId={}", record.getId());
            } else {
                // 6. 停车记录成功完成后再减少在场车辆计数
                decrementVehicleCount(parkingLotId);
            }
        }

        // 7. 创建出场记录（传入实际已付金额）
        ExitRecord exitRecord = createExitRecord(payload, record, order, feeCents,
                decision.getDecisionCode(), decision.getReason(), exitTime, actualPaidCents);

        // 8. 若可放行，更新订单为已完成（固定车位无订单则跳过）
        if (decision.isAllowExit() && order != null) {
            parkingOrderService.completeOrder(order.getId(), exitTime);
        }

        // 9. 同步更新 ParkingSession 状态（出场完成）
        if (decision.isAllowExit()) {
            Long orderId = order != null ? order.getId() : null;
            syncParkingSessionExit(record, payload, exitTime, feeCents, orderId);
        }

        Long orderIdForResult = order != null ? order.getId() : null;
        log.info("出场处理完成: recordId={} plate={} feeCents={} paidCents={} decision={} orderId={}",
                record.getId(), standardizedPlate, feeCents, actualPaidCents,
                decision.getDecisionCode(), orderIdForResult);

        return ExitResult.of(decision, exitRecord.getId(), orderIdForResult, feeCents);
    }

    /**
     * 查询指定车牌在指定停车场的活跃（PARKING）记录。
     */
    private ParkingRecord findActiveRecord(Long parkingLotId, String standardizedPlate) {
        List<ParkingRecord> records = recordMapper.selectList(
                new QueryWrapper<ParkingRecord>()
                        .eq("parking_lot_id", parkingLotId)
                        .eq("standardized_plate", standardizedPlate)
                        .eq("status", "PARKING")
                        .orderByDesc("entry_time")
                        .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 决定放行策略。
     */
    private ReleaseDecision resolveReleaseDecision(int feeCents, ParkingOrder order) {
        if (feeCents == 0) {
            return ReleaseDecision.zeroFee();
        }
        // 已支付订单
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            return ReleaseDecision.paid();
        }
        // P020 扩展：月卡/白名单等授权放行
        return ReleaseDecision.pendingPayment();
    }

    /**
     * 使用条件更新完成停车记录。
     */
    private boolean completeParkingRecord(ParkingRecord record, RecognitionEventPayload payload, LocalDateTime exitTime) {
        UpdateWrapper<ParkingRecord> wrapper = new UpdateWrapper<ParkingRecord>()
                .setSql("status = 'COMPLETED'")
                .setSql("exit_time = '" + exitTime + "'")
                .setSql("exit_event_id = " + payload.getLogId())
                .setSql("updated_at = '" + LocalDateTime.now() + "'")
                .eq("id", record.getId())
                .eq("status", "PARKING");
        return recordMapper.update(null, wrapper) > 0;
    }

    /**
     * 减少停车场在场车辆计数。
     */
    private void decrementVehicleCount(Long parkingLotId) {
        int updated = parkingLotMapper.update(null,
                new UpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles - 1")
                        .setSql("remaining_spaces = remaining_spaces + 1")
                        .eq("id", parkingLotId));

        if (updated == 0) {
            log.warn("停车场容量减少影响0行（可能记录不存在）: parkingLotId={}", parkingLotId);
        } else {
            log.info("停车场容量已减少: parkingLotId={}", parkingLotId);
            pushSpaceUpdate(parkingLotId);
        }
    }

    /**
     * 同步更新 ParkingSession 为出场状态。
     */
    private void syncParkingSessionExit(ParkingRecord record, RecognitionEventPayload payload,
                                         LocalDateTime exitTime, int feeCents, Long orderId) {
        if (orderId == null) {
            log.info("ParkingSession 出场同步跳过（无订单）: plate={} lotId={}",
                    record.getStandardizedPlate(), record.getParkingLotId());
            return;
        }
        if (parkingSessionService == null) {
            return;
        }
        try {
            var sessionVO = parkingSessionService.getInByPlateAndLot(
                    record.getStandardizedPlate(), record.getParkingLotId());
            if (sessionVO != null) {
                var exitCmd = new com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd();
                exitCmd.setSessionId(sessionVO.getId());
                exitCmd.setExitLaneId(payload.getLaneId());
                exitCmd.setFeeAmount(BigDecimal.valueOf(feeCents).movePointLeft(2));
                exitCmd.setParkingRecordId(record.getId());
                exitCmd.setOrderId(orderId);
                parkingSessionService.exit(exitCmd);
                log.info("ParkingSession 出场同步成功: sessionId={} recordId={} plate={}",
                        sessionVO.getId(), record.getId(), record.getStandardizedPlate());
            } else {
                log.warn("ParkingSession 出场同步未找到在场记录: plate={} lotId={}",
                        record.getStandardizedPlate(), record.getParkingLotId());
            }
        } catch (Exception e) {
            log.warn("ParkingSession 出场同步失败（不影响主业务）: recordId={} error={}",
                    record.getId(), e.getMessage());
        }
    }

    /**
     * 推送车位变化到岗亭前端。
     */
    private void pushSpaceUpdate(Long parkingLotId) {
        if (boothWebSocketPublisher == null) {
            return;
        }
        try {
            ParkingLot updated = parkingLotMapper.selectById(parkingLotId);
            if (updated != null) {
                boothWebSocketPublisher.sendSpaceUpdate(
                        parkingLotId,
                        updated.getRemainingSpaces(),
                        updated.getCurrentVehicles(),
                        updated.getTotalSpaces());
            }
        } catch (Exception e) {
            log.warn("出场车位变化 WebSocket 推送失败（不影响主业务）: parkingLotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }

    /**
     * 创建出场记录。
     */
    private ExitRecord createExitRecord(RecognitionEventPayload payload,
                                         ParkingRecord record,
                                         ParkingOrder order,
                                         int feeCents,
                                         String decisionCode,
                                         String reason,
                                         LocalDateTime exitTime,
                                         int actualPaidCents) {
        ExitRecord exitRecord = new ExitRecord();
        exitRecord.setTenantId(record.getTenantId());
        exitRecord.setParkingLotId(record.getParkingLotId());
        exitRecord.setParkingRecordId(record.getId());
        exitRecord.setExitEventId(payload.getLogId());
        exitRecord.setLaneId(payload.getLaneId());
        exitRecord.setDeviceId(payload.getDeviceId());
        exitRecord.setStandardizedPlate(record.getStandardizedPlate());
        exitRecord.setExitTime(exitTime);
        exitRecord.setFeeCents(Math.max(0, feeCents));
        exitRecord.setPaidCents(actualPaidCents);
        exitRecord.setReleaseDecision(decisionCode);
        exitRecord.setOrderId(order != null ? order.getId() : 0L);
        exitRecord.setReason(reason);
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());

        exitRecordMapper.insert(exitRecord);
        return exitRecord;
    }

    /**
     * 创建无在场记录的出场记录（排查用）。
     */
    private ExitRecord createNoRecordExit(RecognitionEventPayload payload,
                                           String standardizedPlate,
                                           LocalDateTime exitTime) {
        ExitRecord exitRecord = new ExitRecord();
        exitRecord.setTenantId(payload.getTenantId());
        exitRecord.setParkingLotId(payload.getParkingLotId());
        exitRecord.setExitEventId(payload.getLogId());
        exitRecord.setStandardizedPlate(standardizedPlate);
        exitRecord.setExitTime(exitTime);
        exitRecord.setFeeCents(0);
        exitRecord.setPaidCents(0);
        exitRecord.setReleaseDecision(ExitRecord.DECISION_NO_RECORD);
        exitRecord.setLaneId(payload.getLaneId());
        exitRecord.setDeviceId(payload.getDeviceId());
        exitRecord.setReason("未匹配到同停车场在场记录");
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());

        exitRecordMapper.insert(exitRecord);
        return exitRecord;
    }
}
