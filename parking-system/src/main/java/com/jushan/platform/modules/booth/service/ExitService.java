package com.jushan.platform.modules.booth.service;
import com.jushan.platform.modules.miniapp.service.FixedSpaceService;import com.jushan.platform.modules.device.service.DeviceService;import com.jushan.platform.modules.parking.service.PrepaidDeductionService;import com.jushan.platform.modules.parking.service.ParkingOrderService;import com.jushan.platform.modules.parking.service.FeeCalculationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.entity.ExitRecord;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.entity.OrderStatusLog;
import com.jushan.platform.modules.booth.event.RecognitionEventPayload;
import com.jushan.platform.modules.parking.mapper.ExitRecordMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.booth.ws.BoothWebSocketPublisher;
import com.jushan.platform.modules.common.dto.RemoteGateAlertDTO;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import com.jushan.framework.lock.DistributedLock;
import com.jushan.platform.modules.parking.mapper.BillingRuleRecalcLogMapper;
import com.jushan.platform.modules.parking.entity.BillingRuleRecalcLog;
import com.jushan.platform.modules.common.service.ParamResolver;
import com.jushan.platform.modules.common.constant.ParamKeys;
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
    private final FeeCalculationService feeCalculationService;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final ParkingSessionService parkingSessionService;
    private final PrepaidDeductionService prepaidDeductionService;
    private final FixedSpaceService fixedSpaceService;
    private final DeviceService deviceService;
    private final DistributedLock distributedLock;
    private final BillingRuleRecalcLogMapper recalcLogMapper;
    private final ParkingOrderMapper orderMapper;
    private final ParamResolver paramResolver;
    private final VehicleTypeDecisionService vehicleTypeDecisionService;

    public ExitService(ParkingRecordMapper recordMapper,
                        ExitRecordMapper exitRecordMapper,
                        ParkingOrderService parkingOrderService,
                        ParkingLotMapper parkingLotMapper,
                        FeeCalculationService feeCalculationService,
                        BoothWebSocketPublisher boothWebSocketPublisher,
                        ParkingSessionService parkingSessionService,
                        PrepaidDeductionService prepaidDeductionService,
                        FixedSpaceService fixedSpaceService,
                        DeviceService deviceService,
                        DistributedLock distributedLock,
                        BillingRuleRecalcLogMapper recalcLogMapper,
                        ParkingOrderMapper orderMapper,
                        ParamResolver paramResolver,
                        VehicleTypeDecisionService vehicleTypeDecisionService) {
        this.recordMapper = recordMapper;
        this.exitRecordMapper = exitRecordMapper;
        this.parkingOrderService = parkingOrderService;
        this.parkingLotMapper = parkingLotMapper;
        this.feeCalculationService = feeCalculationService;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.parkingSessionService = parkingSessionService;
        this.prepaidDeductionService = prepaidDeductionService;
        this.fixedSpaceService = fixedSpaceService;
        this.deviceService = deviceService;
        this.distributedLock = distributedLock;
        this.recalcLogMapper = recalcLogMapper;
        this.orderMapper = orderMapper;
        this.paramResolver = paramResolver;
        this.vehicleTypeDecisionService = vehicleTypeDecisionService;
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

        // 2b. 计算费用（优先使用 ParkingSession 的 fee_rule_snapshot）
        int feeCents;
        com.jushan.platform.modules.parking.vo.ParkingSessionVO sessionVO =
                parkingSessionService.getInByPlateAndLot(standardizedPlate, parkingLotId);
        if (isFixedSpace) {
            feeCents = 0;
            log.info("固定车位车辆出场，跳过计费: plate={} parkingLotId={}", standardizedPlate, parkingLotId);
        } else {
            String snapshotJson = sessionVO != null ? sessionVO.getFeeRuleSnapshot() : null;
            try {
                feeCents = feeCalculationService.calculateFeeCents(
                        parkingLotId, null,
                        sessionVO != null ? sessionVO.getVehicleType() : null,
                        sessionVO != null ? sessionVO.getPlateColor() : null,
                        record.getEntryTime(), exitTime, snapshotJson);
                log.info("使用FeeRule计费: recordId={} useSnapshot={}",
                        record.getId(), snapshotJson != null && !snapshotJson.isBlank());
            } catch (BusinessException e) {
                log.warn("FeeRule计费失败，按0费放行: recordId={} error={}",
                        record.getId(), e.getMessage());
                feeCents = 0;
            }
        }

        // 黑白名单出场判定（任务包 3-3 新增）
        if (vehicleTypeDecisionService != null) {
            VehicleTypeDecisionVO exitDecision = vehicleTypeDecisionService.decide(
                    standardizedPlate, parkingLotId, record.getTenantId());

            if ("WHITE".equals(exitDecision.getVehicleType())) {
                // 白名单免费放行
                log.info("白名单车辆出场零费放行: plate={} lotId={}", standardizedPlate, parkingLotId);
                feeCents = 0;
            }

            if ("BLACK".equals(exitDecision.getVehicleType())
                    && Boolean.FALSE.equals(exitDecision.getAllowExit())) {
                log.warn("黑名单车辆禁止出场: plate={} lotId={}", standardizedPlate, parkingLotId);
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        exitDecision.getDecisionReason());
            }
        }

        // 2c. 欠费检测（任务包 2-3）：检测该车牌是否有未补缴的欠费订单
        if (!isFixedSpace) {
            List<ParkingOrder> arrearsOrders = parkingOrderService.getArrearsOrdersByPlate(
                    standardizedPlate, parkingLotId);
            if (!arrearsOrders.isEmpty()) {
                ExitResult arrearsResult = handleArrearsReentry(record, arrearsOrders, feeCents,
                        exitTime, payload);
                if (arrearsResult != null) {
                    return arrearsResult;
                }
            }
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
                // 任务包 2-2：建单逻辑收敛——加分布式锁防重复建单，PENDING_PAY 更新金额、
                // CANCELLED 新建并关联原订单。
                String lockKey = "exit:order:lock:" + record.getId();
                boolean locked = distributedLock.tryLock(lockKey, 3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    log.warn("获取出口分布式锁失败，降级处理: recordId={}", record.getId());
                }
                try {
                    ParkingOrder existing = parkingOrderService.findReusableOrderForRecord(record.getId());

                    // 路径 A：存在有效待支付/支付中订单 → 按当前时长重算金额并更新
                    if (existing != null && (ParkingOrder.STATUS_PENDING_PAY.equals(existing.getStatus())
                            || ParkingOrder.STATUS_PAYING.equals(existing.getStatus()))) {
                        int originalAmount = existing.getAmountCents() != null ? existing.getAmountCents() : 0;
                        // 以实际停车时长重新计费
                        String snapshotJson = sessionVO != null ? sessionVO.getFeeRuleSnapshot() : null;
                        int recalcFeeCents;
                        try {
                            recalcFeeCents = feeCalculationService.calculateFeeCents(
                                    parkingLotId, null,
                                    sessionVO != null ? sessionVO.getVehicleType() : null,
                                    sessionVO != null ? sessionVO.getPlateColor() : null,
                                    record.getEntryTime(), exitTime, snapshotJson);
                        } catch (BusinessException e) {
                            log.warn("出场重识别计费失败，按原金额保留: recordId={} error={}", record.getId(), e.getMessage());
                            recalcFeeCents = originalAmount;
                        }
                        parkingOrderService.updatePendingOrderAmount(existing.getId(), recalcFeeCents,
                                LocalDateTime.now().plusMinutes(15));
                        parkingOrderService.insertRecalcLog(record, existing.getId(), existing.getId(),
                                originalAmount, recalcFeeCents, "EXIT_RESCAN");
                        order = parkingOrderService.getById(existing.getId());
                        log.info("出场重识别：更新已有 PENDING_PAY 订单金额 {}->{} orderId={}",
                                originalAmount, recalcFeeCents, existing.getId());
                    }

                    // 路径 B：存在 CANCELLED 历史订单，无有效待支付 → 新建并关联
                    else if (existing == null) {
                        ParkingOrder cancelled = parkingOrderService.findCancelledOrderForRecord(record.getId());
                        if (cancelled != null) {
                            int cancelledAmount = cancelled.getAmountCents() != null ? cancelled.getAmountCents() : 0;
                            // 以实际停车时长重新计费
                            String snapshotJson = sessionVO != null ? sessionVO.getFeeRuleSnapshot() : null;
                            int recalcFeeCents;
                            try {
                                recalcFeeCents = feeCalculationService.calculateFeeCents(
                                        parkingLotId, null,
                                        sessionVO != null ? sessionVO.getVehicleType() : null,
                                        sessionVO != null ? sessionVO.getPlateColor() : null,
                                        record.getEntryTime(), exitTime, snapshotJson);
                            } catch (BusinessException e) {
                                log.warn("超时关单后重算计费失败，按0费: recordId={} error={}", record.getId(), e.getMessage());
                                recalcFeeCents = 0;
                            }
                            order = parkingOrderService.createOrderInternal(record, recalcFeeCents, null,
                                    ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId(), cancelled.getId(),
                                    "超时关单后重算，原订单:" + cancelled.getId());
                            parkingOrderService.insertRecalcLog(record, cancelled.getId(), order.getId(),
                                    cancelledAmount, recalcFeeCents, "TIMEOUT_RECALC");
                            log.info("超时关单后重算：原订单 {} cancelledAmount={} 新订单 {} newAmount={}",
                                    cancelled.getId(), cancelledAmount, order.getId(), recalcFeeCents);
                        } else {
                            // 兼容期：旧在场记录无任何订单
                            order = parkingOrderService.createOrder(record, feeCents, null,
                                    ParkingOrder.PAY_SCENE_AT_EXIT);
                        }
                    }

                    // PRE_ORDER 路径（不变）
                    else if (ParkingOrder.STATUS_PRE_ORDER.equals(existing.getStatus())) {
                        if (feeCents <= 0) {
                            parkingOrderService.preOrderToCompleted(existing.getId(), exitTime);
                        } else {
                            parkingOrderService.preOrderToPending(existing.getId(), feeCents,
                                    LocalDateTime.now().plusMinutes(15),
                                    ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId());
                        }
                        order = parkingOrderService.getById(existing.getId());
                    }
                } finally {
                    if (locked) {
                        distributedLock.unlock(lockKey);
                    }
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
     * <p>
     * 任务包 2-3：根据车场参数 exit.unpaid_strategy 决定未支付车辆的处理方式。
     */
    private ReleaseDecision resolveReleaseDecision(int feeCents, ParkingOrder order) {
        if (order == null) {
            return ReleaseDecision.pendingPayment();
        }
        if (feeCents == 0) {
            return ReleaseDecision.zeroFee();
        }
        // 已支付订单
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            return ReleaseDecision.paid();
        }
        // P020 扩展：月卡/白名单等授权放行
        // 未支付订单：读取车场未支付出场策略
        String unpaidStrategy = paramResolver.getString(
                ParamKeys.EXIT_UNPAID_STRATEGY, order.getParkingLotId());
        if (ParamKeys.EXIT_UNPAID_ALLOW_ARREARS.equals(unpaidStrategy)) {
            // ALLOW_ARREARS：开闸放行，订单转 ARREARS
            parkingOrderService.allowArrears(order.getId(), OrderStatusLog.TRIGGER_SYSTEM, null,
                    "未支付欠费放行（车场策略）");
            log.info("欠费放行: orderId={} plate={} strategy={}",
                    order.getId(), order.getPlateNumber(), unpaidStrategy);
            return ReleaseDecision.arrearsAllowed();
        }
        // BLOCK 或其他未知值：拦截不放行
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
                int totalSpaces = updated.getTotalSpaces() != null ? updated.getTotalSpaces() : 0;
                int currentVehicles = (int) parkingSessionService.countInByParkingLotIdIgnoreTenant(parkingLotId);
                boothWebSocketPublisher.sendSpaceUpdate(
                        parkingLotId,
                        Math.max(0, totalSpaces - currentVehicles),
                        currentVehicles,
                        totalSpaces);
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
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
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
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
        exitRecord.setLaneId(payload.getLaneId());
        exitRecord.setDeviceId(payload.getDeviceId());
        exitRecord.setReason("未匹配到同停车场在场记录");
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());

        exitRecordMapper.insert(exitRecord);
        return exitRecord;
    }

    /**
     * 处理欠费车辆再次出场（任务包 2-3）。
     * <p>
     * 根据车场参数 arrears.reexit_strategy 决定处理方式：
     * MUST_PAY → 创建合并订单拦截补缴
     * REMIND_ONLY → 放行并推送提醒
     *
     * @param record        当前停车记录
     * @param arrearsOrders 该车未补缴的欠费订单列表
     * @param feeCents      本次出场计费金额（分）
     * @param exitTime      出场时间
     * @param payload       识别事件载荷
     * @return EXIT 结果（若短路直接返回），null 表示走正常后续流程
     */
    private ExitResult handleArrearsReentry(ParkingRecord record,
                                             List<ParkingOrder> arrearsOrders,
                                             int feeCents,
                                             LocalDateTime exitTime,
                                             RecognitionEventPayload payload) {
        String reexitStrategy = paramResolver.getString(
                ParamKeys.ARREARS_REEXIT_STRATEGY, record.getParkingLotId());

        // 计算欠费总额
        int arrearsTotalCents = arrearsOrders.stream()
                .mapToInt(o -> o.getPayableAmount() != null ? o.getPayableAmount() : 0)
                .sum();

        if (ParamKeys.ARREARS_MUST_PAY.equals(reexitStrategy)) {
            // MUST_PAY：创建合并计费订单
            int totalCents = arrearsTotalCents + feeCents;

            // 构建 arrearsOrderIds JSON 数组
            StringBuilder idsJson = new StringBuilder("[");
            for (int i = 0; i < arrearsOrders.size(); i++) {
                if (i > 0) idsJson.append(",");
                idsJson.append(arrearsOrders.get(i).getId());
            }
            idsJson.append("]");

            // 创建合并订单（PENDING_PAY，金额 = 欠费 + 本次）
            ParkingOrder mergedOrder = parkingOrderService.createOrderInternal(
                    record, totalCents, null,
                    ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId(), null,
                    "欠费合并计费：欠费" + arrearsTotalCents + "分 + 本次" + feeCents + "分");
            mergedOrder.setArrearsOrderIds(idsJson.toString());

            // 回写 arrearsOrderIds 到数据库（createOrderInternal 未设该字段）
            UpdateWrapper<ParkingOrder> updateWrapper = new UpdateWrapper<>();
            updateWrapper.set("arrears_order_ids", idsJson.toString())
                    .eq("id", mergedOrder.getId());
            orderMapper.update(null, updateWrapper);

            log.info("欠费合并计费: mergedOrderId={} plate={} arrearsTotal={} currentFee={} mergedTotal={} arrearsIds={}",
                    mergedOrder.getId(), record.getStandardizedPlate(),
                    arrearsTotalCents, feeCents, totalCents, idsJson);

            // 创建出场记录（拦截）
            ExitRecord exitRecord = new ExitRecord();
            exitRecord.setTenantId(record.getTenantId());
            exitRecord.setParkingLotId(record.getParkingLotId());
            exitRecord.setParkingRecordId(record.getId());
            exitRecord.setExitEventId(payload.getLogId());
            exitRecord.setLaneId(payload.getLaneId());
            exitRecord.setDeviceId(payload.getDeviceId());
            exitRecord.setStandardizedPlate(record.getStandardizedPlate());
            exitRecord.setExitTime(exitTime);
            exitRecord.setFeeCents(totalCents);
            exitRecord.setPaidCents(0);
            exitRecord.setReleaseDecision(ExitRecord.DECISION_ARREARS_MUST_PAY);
            // 记录相机来源
            if (payload.getCameraSource() != null) {
                exitRecord.setCameraSource(payload.getCameraSource());
            }
            exitRecord.setOrderId(mergedOrder.getId());
            exitRecord.setReason("欠费合并计费：欠费" + (arrearsTotalCents / 100.0) + "元 + 本次" + (feeCents / 100.0) + "元 = " + (totalCents / 100.0) + "元");
            exitRecord.setCreatedAt(LocalDateTime.now());
            exitRecord.setUpdatedAt(LocalDateTime.now());
            exitRecordMapper.insert(exitRecord);

            // 推送 WebSocket 通知岗亭端
            RemoteGateAlertDTO alertDto = new RemoteGateAlertDTO();
            alertDto.setReason("欠费车辆出场，需补缴欠费" + (arrearsTotalCents / 100.0) + "元");
            alertDto.setOperatorName("系统（欠费检测）");
            alertDto.setOperationTime(LocalDateTime.now().toString());
            boothWebSocketPublisher.sendRemoteGateAlert(record.getParkingLotId(), alertDto);

            return ExitResult.of(ReleaseDecision.arrearsMustPay(),
                    exitRecord.getId(), mergedOrder.getId(), totalCents);
        }

        // REMIND_ONLY（默认回退）：放行，推送提醒
        log.info("欠费提醒放行: plate={} arrearsCount={} arrearsTotal={}",
                record.getStandardizedPlate(), arrearsOrders.size(), arrearsTotalCents);

        // 直接放行（不入 createOrder 流程，也不转 ARREARS 状态）
        boolean recordCompleted = completeParkingRecord(record, payload, exitTime);
        if (recordCompleted) {
            decrementVehicleCount(record.getParkingLotId());
        }
        syncParkingSessionExit(record, payload, exitTime, feeCents, null);

        ExitRecord exitRecord = createExitRecordForArrears(record, payload, feeCents,
                ExitRecord.DECISION_ARREARS_REMIND,
                "欠费提醒放行，待补缴欠费" + (arrearsTotalCents / 100.0) + "元", exitTime);

        // 推送 WebSocket 通知
        RemoteGateAlertDTO remindDto = new RemoteGateAlertDTO();
        remindDto.setReason("欠费提醒放行（待补缴" + (arrearsTotalCents / 100.0) + "元）");
        remindDto.setOperatorName("系统（欠费检测）");
        remindDto.setOperationTime(LocalDateTime.now().toString());
        boothWebSocketPublisher.sendRemoteGateAlert(record.getParkingLotId(), remindDto);

        return ExitResult.of(ReleaseDecision.arrearsRemind(),
                exitRecord.getId(), null, feeCents);
    }

    /**
     * 为欠费场景创建出场记录（简化版，不依赖 ParkingOrder）。
     */
    private ExitRecord createExitRecordForArrears(ParkingRecord record,
                                                   RecognitionEventPayload payload,
                                                   int feeCents,
                                                   String decisionCode,
                                                   String reason,
                                                   LocalDateTime exitTime) {
        ExitRecord exitRecord = new ExitRecord();
        exitRecord.setTenantId(record.getTenantId());
        exitRecord.setParkingLotId(record.getParkingLotId());
        exitRecord.setParkingRecordId(record.getId());
        exitRecord.setExitEventId(payload.getLogId());
        exitRecord.setLaneId(payload.getLaneId());
        exitRecord.setDeviceId(payload.getDeviceId());
        exitRecord.setStandardizedPlate(record.getStandardizedPlate());
        exitRecord.setExitTime(exitTime);
        exitRecord.setFeeCents(feeCents);
        exitRecord.setPaidCents(0);
        exitRecord.setReleaseDecision(decisionCode);
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
        exitRecord.setOrderId(0L);
        exitRecord.setReason(reason);
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());
        exitRecordMapper.insert(exitRecord);
        return exitRecord;
    }
}
