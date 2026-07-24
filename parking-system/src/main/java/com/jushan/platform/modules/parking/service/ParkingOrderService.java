package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.common.constant.ParamKeys;
import com.jushan.platform.modules.parking.entity.OrderStatusLog;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.enums.OrderStatus;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 停车订单服务（Sprint 8 + 任务包 1-2 状态机扩展）。
 * <p>
 * 负责订单创建、状态机管理、订单号生成、幂等控制、状态流转留痕。
 * <p>
 * <strong>状态机（V1.1 附录 10.2）</strong>：
 * <pre>
 * PRE_ORDER --出场计费--> PENDING_PAY --支付--> PAID --出场完成--> COMPLETED
 * PENDING_PAY --超时关闭--> CANCELLED
 * PENDING_PAY --允许欠费--> ARREARS --补缴--> COMPLETED
 * PAID --退款--> REFUNDED
 * PRE_ORDER --免费放行--> COMPLETED
 * </pre>
 * 全部流转经 {@link OrderStatus#assertCanTransition(String, String)} 守卫，非法流转抛业务异常；
 * 每次成功流转写入 {@code order_status_log}（{@link OrderStatusLogService}）。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>订单号全局唯一：O{lotId}{yyyyMMdd}{6位序号}</li>
 *   <li>状态迁移只允许合法路径，条件更新 + 乐观锁保护</li>
 *   <li>金额禁止负值，零元订单直接标记 COMPLETED</li>
 *   <li>幂等键 24h 内重复返回首次结果</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ParkingOrderService {

    private static final Logger log = LoggerFactory.getLogger(ParkingOrderService.class);

    private final ParkingOrderMapper orderMapper;
    private final ParkingRecordMapper recordMapper;
    private final OrderStatusLogService orderStatusLogService;
    private final com.jushan.platform.modules.parking.mapper.BillingRuleRecalcLogMapper recalcLogMapper;

    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String lastSequenceDate = "";

    public ParkingOrderService(ParkingOrderMapper orderMapper,
                               ParkingRecordMapper recordMapper,
                               OrderStatusLogService orderStatusLogService,
                               com.jushan.platform.modules.parking.mapper.BillingRuleRecalcLogMapper recalcLogMapper) {
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.orderStatusLogService = orderStatusLogService;
        this.recalcLogMapper = recalcLogMapper;
    }

    // ==================== 创建 ====================

    /**
     * 创建预订单（入场时调用，任务包 1-2）。
     * <p>
     * 临停车辆入场即生成 PRE_ORDER，金额为 0，出场计费后再流转为 PENDING_PAY。
     *
     * @param record 停车记录（可信）
     * @return 创建的预订单
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder createPreOrder(ParkingRecord record) {
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(record.getTenantId());
        order.setParkingLotId(record.getParkingLotId());
        order.setParkingRecordId(record.getId());
        order.setOrderNo(generateOrderNo(record.getParkingLotId()));
        order.setOrderType(ParkingOrder.ORDER_TYPE_PARKING);
        order.setPlateNumber(record.getStandardizedPlate());
        order.setAmountCents(0);
        order.setDiscountAmount(0);
        order.setPointsDiscount(0);
        order.setPayableAmount(0);
        order.setPaidAmount(0);
        order.setStatus(ParkingOrder.STATUS_PRE_ORDER);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderMapper.insert(order);
        orderStatusLogService.record(order, null, ParkingOrder.STATUS_PRE_ORDER,
                currentTriggerSource(), currentOperatorId(), null, "入场生成预订单");
        log.info("预订单创建成功: orderId={} orderNo={} recordId={}",
                order.getId(), order.getOrderNo(), record.getId());
        return order;
    }

    /**
     * 创建停车订单（出场时调用/兼容旧无预订单数据）。
     *
     * @param record         停车记录（可信）
     * @param feeCents       计算费用（分）
     * @param idempotencyKey 幂等键
     * @param payScene       支付场景（AT_EXIT / ADVANCE）
     * @return 创建的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder createOrder(ParkingRecord record, int feeCents, String idempotencyKey,
                                     String payScene) {
        return createOrderInternal(record, feeCents, idempotencyKey, payScene, null, null, null);
    }

    /**
     * 内部建单，支持支付场景、出口车道、重算来源。
     */
    public ParkingOrder createOrderInternal(ParkingRecord record, int feeCents,
                                               String idempotencyKey, String payScene,
                                               Long exitLaneId, Long recalcSourceOrderId,
                                               String remark) {
        // 幂等检查
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            ParkingOrder existing = orderMapper.selectOne(
                    new QueryWrapper<ParkingOrder>()
                            .eq("idempotency_key", idempotencyKey)
                            .isNull("deleted_at"));
            if (existing != null) {
                log.info("订单幂等返回: idempotencyKey={}, orderId={}", idempotencyKey, existing.getId());
                return existing;
            }
        }

        ParkingOrder order = new ParkingOrder();
        order.setTenantId(record.getTenantId());
        order.setParkingLotId(record.getParkingLotId());
        order.setParkingRecordId(record.getId());
        order.setOrderNo(generateOrderNo(record.getParkingLotId()));
        order.setOrderType(ParkingOrder.ORDER_TYPE_PARKING);
        order.setPlateNumber(record.getStandardizedPlate());
        order.setAmountCents(Math.max(0, feeCents));
        order.setDiscountAmount(0);
        order.setPointsDiscount(0);
        order.setPayableAmount(Math.max(0, feeCents));
        order.setPaidAmount(0);
        order.setIdempotencyKey(idempotencyKey);
        order.setPayScene(payScene);
        order.setExitLaneId(exitLaneId);
        order.setRecalcSourceOrderId(recalcSourceOrderId);

        // 零元订单直接完成
        if (feeCents <= 0) {
            order.setStatus(ParkingOrder.STATUS_COMPLETED);
            order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
            order.setPayTime(LocalDateTime.now());
        } else {
            order.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        }

        // 订单有效期 15 分钟
        order.setExpiredAt(LocalDateTime.now().plusMinutes(15));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderMapper.insert(order);
        String logRemark = remark != null ? remark : "出场建单";
        orderStatusLogService.record(order, null, order.getStatus(),
                currentTriggerSource(), currentOperatorId(), null, logRemark);
        log.info("订单创建成功: orderId={} orderNo={} feeCents={} status={} payScene={}",
                order.getId(), order.getOrderNo(), feeCents, order.getStatus(), payScene);
        return order;
    }

    /**
     * 创建停车订单（出场时调用/兼容旧无预订单数据）——保留兼容签名。
     */
    public ParkingOrder createOrder(ParkingRecord record, int feeCents, String idempotencyKey) {
        return createOrder(record, feeCents, idempotencyKey, null);
    }

    // ==================== 状态流转 ====================

    /**
     * 预订单出场计费 → 待支付（PRE_ORDER → PENDING_PAY，任务包 1-2）。
     *
     * @param orderId     预订单 ID
     * @param feeCents    计费金额（分）
     * @param expiredAt   支付过期时间
     * @param payScene    支付场景（AT_EXIT / ADVANCE），任务包 2-1
     * @param exitLaneId  出口车道ID（AT_EXIT 场景），可为 null
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean preOrderToPending(Long orderId, int feeCents, LocalDateTime expiredAt,
                                      String payScene, Long exitLaneId) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_PENDING_PAY.equals(from)) {
            return true; // 幂等：已计费
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_PENDING_PAY);

        int amount = Math.max(0, feeCents);
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PENDING_PAY)
                .set("amount_cents", amount)
                .set("payable_amount", amount)
                .set("expired_at", expiredAt)
                .set("pay_scene", payScene)
                .set("exit_lane_id", exitLaneId)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PRE_ORDER);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_PENDING_PAY,
                    currentTriggerSource(), currentOperatorId(), null,
                    "出场计费，应付" + amount + "分，场景:" + payScene);
            return true;
        }
        return false;
    }

    /**
     * 预订单出场计费 → 待支付——保留兼容签名。
     */
    public boolean preOrderToPending(Long orderId, int feeCents, LocalDateTime expiredAt) {
        return preOrderToPending(orderId, feeCents, expiredAt, null, null);
    }

    /**
     * 预订单免费放行 → 已完成（PRE_ORDER → COMPLETED，任务包 1-2）。
     *
     * @param orderId  预订单 ID
     * @param exitTime 出场时间
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean preOrderToCompleted(Long orderId, LocalDateTime exitTime) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_COMPLETED.equals(from)) {
            return true; // 幂等
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_COMPLETED);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_COMPLETED)
                .set("exit_time", exitTime)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PRE_ORDER);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_COMPLETED,
                    currentTriggerSource(), currentOperatorId(), null, "免费放行");
            return true;
        }
        return false;
    }

    /**
     * 订单支付（更新状态为 PAYING）。
     *
     * @param orderId    订单ID
     * @param payChannel 支付渠道
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean startPaying(Long orderId, String payChannel) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_PAYING.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_PAYING);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAYING)
                .set("pay_channel", payChannel)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PENDING_PAY)
                .gt("expired_at", LocalDateTime.now());
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_PAYING,
                    currentTriggerSource(), currentOperatorId(), null, "发起支付:" + payChannel);
            return true;
        }
        return false;
    }

    /**
     * 完成支付（回调时使用，更新状态为 PAID 并设置支付流水）。
     *
     * @param orderId   订单ID
     * @param paySerial P云支付流水
     * @param payTime   支付时间
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean completePay(Long orderId, String paySerial, LocalDateTime payTime) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_PAID.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_PAID);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAID)
                .set("pay_serial", paySerial)
                .set("paid_amount", order.getPayableAmount())
                .set("pay_time", payTime)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_PAID,
                    currentTriggerSource(), currentOperatorId(), null, "支付完成");
            return true;
        }
        return false;
    }

    /**
     * 支付成功回调（更新状态为 PAID）。
     *
     * @param orderId    订单ID
     * @param paySerial  P云支付流水
     * @param paidAmount 实际支付金额（分）
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(Long orderId, String paySerial, int paidAmount) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_PAID.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_PAID);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAID)
                .set("pay_serial", paySerial)
                .set("paid_amount", paidAmount)
                .set("pay_time", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_PAID,
                    currentTriggerSource(), currentOperatorId(), null, "标记已支付");
            return true;
        }
        return false;
    }

    /**
     * 完成订单（出场放行时调用）。
     *
     * @param orderId  订单ID
     * @param exitTime 出场时间
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long orderId, LocalDateTime exitTime) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_COMPLETED.equals(from)) {
            return true; // 幂等：已完成
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_COMPLETED);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_COMPLETED)
                .set("exit_time", exitTime)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PAID, ParkingOrder.STATUS_PENDING_PAY);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_COMPLETED,
                    currentTriggerSource(), currentOperatorId(), null, "出场完成");
            return true;
        }
        return false;
    }

    /**
     * 取消订单（超时关闭/手动关闭）。
     *
     * @param orderId 订单ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_CANCELLED.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_CANCELLED);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_CANCELLED)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PENDING_PAY);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_CANCELLED,
                    currentTriggerSource(), currentOperatorId(), null, "订单取消");
            return true;
        }
        return false;
    }

    /**
     * 标记支付失败。
     *
     * @param orderId 订单ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPayFailed(Long orderId) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_PAY_FAILED.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_PAY_FAILED);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAY_FAILED)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_PAY_FAILED,
                    currentTriggerSource(), currentOperatorId(), null, "支付失败");
            return true;
        }
        return false;
    }

    /**
     * 允许欠费（待支付 → 欠费中，PENDING_PAY → ARREARS，任务包 1-2）。
     * <p>
     * 供"允许欠费放行"策略调用（策略读取与放行链路在收费闭环任务包落地）。
     *
     * @param orderId       订单ID
     * @param triggerSource 触发源
     * @param operatorId    操作人（可为 null）
     * @param remark        备注
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean allowArrears(Long orderId, String triggerSource, Long operatorId, String remark) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_ARREARS.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_ARREARS);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_ARREARS)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PENDING_PAY);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_ARREARS,
                    triggerSource != null ? triggerSource : currentTriggerSource(),
                    operatorId, null, remark != null ? remark : "允许欠费放行");
            return true;
        }
        return false;
    }

    /**
     * 欠费补缴（欠费中 → 已完成，ARREARS → COMPLETED，任务包 1-2）。
     *
     * @param orderId       订单ID
     * @param triggerSource 触发源
     * @param operatorId    操作人（可为 null）
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean payArrears(Long orderId, String triggerSource, Long operatorId) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (ParkingOrder.STATUS_COMPLETED.equals(from)) {
            return true;
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_COMPLETED);

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_COMPLETED)
                .set("paid_amount", order.getPayableAmount())
                .set("pay_time", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_ARREARS);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            orderStatusLogService.record(order, from, ParkingOrder.STATUS_COMPLETED,
                    triggerSource != null ? triggerSource : currentTriggerSource(),
                    operatorId, null, "欠费补缴");
            return true;
        }
        return false;
    }

    /**
     * 应用储值车余额支付。
     * <p>
     * 使用条件更新确保仅从 PENDING_PAY 状态更新，并记录余额支付明细。
     *
     * @param orderId    订单ID
     * @param paidAmount 余额支付金额（分）
     * @param fullyPaid  是否全额支付（true → PAID, false → 保持 PENDING_PAY）
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyBalancePayment(Long orderId, int paidAmount, boolean fullyPaid) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String from = order.getStatus();
        if (fullyPaid) {
            OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_PAID);
        }

        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("pay_channel", ParkingOrder.PAY_CHANNEL_BALANCE)
                .set("paid_amount", paidAmount)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PENDING_PAY);

        if (fullyPaid) {
            wrapper.set("status", ParkingOrder.STATUS_PAID);
            wrapper.set("pay_time", LocalDateTime.now());
        }

        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            if (fullyPaid) {
                orderStatusLogService.record(order, from, ParkingOrder.STATUS_PAID,
                        currentTriggerSource(), currentOperatorId(), null, "储值车余额全额支付");
            }
            log.info("余额支付应用成功: orderId={} paidAmount={} fullyPaid={}", orderId, paidAmount, fullyPaid);
        } else {
            log.warn("余额支付应用失败（订单状态可能已变更）: orderId={}", orderId);
        }
        return updated > 0;
    }

    /**
     * 模拟退款（已支付 → 已退款，PAID → REFUNDED，任务包 1-2）。
     * <p>
     * 仅 PAID 订单可退，其他状态发起被拒；记录退款原因/时间/操作人；本期无真实资金流动。
     *
     * @param orderId    订单ID
     * @param reason     退款原因（必填）
     * @param operatorId 操作人 ID
     * @return 是否成功
     * @throws BusinessException 订单不存在 / 非 PAID 状态 / 原因为空 / 并发变更
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean refund(Long orderId, String reason, Long operatorId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "退款原因不能为空");
        }
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在");
        }
        String from = order.getStatus();
        // 仅 PAID 可退
        if (!ParkingOrder.STATUS_PAID.equals(from)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅已支付(PAID)订单可发起退款，当前状态: " + OrderStatus.fromCode(from).getLabel());
        }
        OrderStatus.assertCanTransition(from, ParkingOrder.STATUS_REFUNDED);

        LocalDateTime now = LocalDateTime.now();
        String trimmedReason = reason.trim();
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_REFUNDED)
                .set("refund_reason", trimmedReason)
                .set("refund_time", now)
                .set("refund_operator_id", operatorId)
                .set("updated_at", now)
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PAID);
        int updated = orderMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变更，退款失败，请刷新后重试");
        }
        orderStatusLogService.record(order, from, ParkingOrder.STATUS_REFUNDED,
                OrderStatusLog.TRIGGER_USER, operatorId, null, "退款原因: " + trimmedReason);
        log.info("模拟退款成功: orderId={} operatorId={} reason={}", orderId, operatorId, trimmedReason);
        return true;
    }

    // ==================== 查询 ====================

    /**
     * 查询停车记录当前可复用的订单，无则返回 null。
     * <p>
     * 任务包 1-2：为保证"入场→查费→缴费→出场"全链路单一订单，出场与小程序查费/缴费均复用本方法定位的订单：
     * 命中 PRE_ORDER 时出场计费/免费放行；命中 PENDING_PAY/PAYING/PAID 时（提前缴费/岗亭等已建单）直接复用，避免重复建单。
     * 终态订单（COMPLETED/CANCELLED/REFUNDED/PAY_FAILED）不作为可复用订单。
     * <p>
     * 任务包 2-1：PAID 状态订单需区分窗口期内（可复用直接放行）vs 窗口期外（需重算）。
     */
    public ParkingOrder findReusableOrderForRecord(Long parkingRecordId) {
        if (parkingRecordId == null) {
            return null;
        }
        return orderMapper.selectOne(
                new QueryWrapper<ParkingOrder>()
                        .eq("parking_record_id", parkingRecordId)
                        .in("status", ParkingOrder.STATUS_PRE_ORDER, ParkingOrder.STATUS_PENDING_PAY,
                                ParkingOrder.STATUS_PAYING, ParkingOrder.STATUS_PAID)
                        .isNull("deleted_at")
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
    }

    /**
     * 查询停车记录下最近一条 CANCELLED 订单，供超时重算路径使用，任务包 2-2。
     */
    public ParkingOrder findCancelledOrderForRecord(Long parkingRecordId) {
        if (parkingRecordId == null) {
            return null;
        }
        return orderMapper.selectLatestCancelledByRecordId(parkingRecordId);
    }

    /**
     * 更新待支付订单金额（出场重识别金额重算），任务包 2-2。
     * <p>
     * 条件更新：仅 PENDING_PAY/PAYING 状态下生效，返回受影响行数。
     *
     * @param orderId        订单 ID
     * @param newAmountCents 重算后的金额（分）
     * @param newExpiredAt   新的支付过期时间
     * @return true 更新成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePendingOrderAmount(Long orderId, int newAmountCents, LocalDateTime newExpiredAt) {
        int updated = orderMapper.updatePendingOrderAmount(orderId, newAmountCents, newExpiredAt);
        if (updated > 0) {
            ParkingOrder order = orderMapper.selectById(orderId);
            if (order != null) {
                orderStatusLogService.record(order, order.getStatus(), order.getStatus(),
                        currentTriggerSource(), currentOperatorId(), null,
                        "出场重识别金额重算，应付" + newAmountCents + "分");
            }
            log.info("订单金额已重算: orderId={} newAmount={}", orderId, newAmountCents);
        }
        return updated > 0;
    }

    /**
     * 写入计费重算审计日志，任务包 2-2。
     *
     * @param record              停车记录
     * @param originalOrderId     原订单 ID（EXIT_RESCAN 时与 newOrderId 相同）
     * @param newOrderId          新/更新后的订单 ID
     * @param originalAmountCents 原金额（分）
     * @param newAmountCents      新金额（分）
     * @param triggerReason       触发原因（EXIT_RESCAN / TIMEOUT_RECALC）
     */
    public void insertRecalcLog(ParkingRecord record, Long originalOrderId, Long newOrderId,
                                 Integer originalAmountCents, Integer newAmountCents,
                                 String triggerReason) {
        com.jushan.platform.modules.parking.entity.BillingRuleRecalcLog recalcLog =
                new com.jushan.platform.modules.parking.entity.BillingRuleRecalcLog();
        recalcLog.setTenantId(record.getTenantId());
        recalcLog.setParkingLotId(record.getParkingLotId());
        recalcLog.setParkingRecordId(record.getId());
        recalcLog.setPlateNumber(record.getStandardizedPlate());
        recalcLog.setOriginalOrderId(originalOrderId);
        recalcLog.setFeeCents(newAmountCents);
        recalcLog.setOriginalAmountCents(originalAmountCents);
        recalcLog.setNewAmountCents(newAmountCents);
        recalcLog.setTriggerReason(triggerReason);
        recalcLog.setRecalcTime(java.time.LocalDateTime.now());
        recalcLog.setCreatedAt(java.time.LocalDateTime.now());
        recalcLogMapper.insert(recalcLog);
        log.info("重算审计日志已写入: recordId={} originalOrderId={} newOrderId={} amount {}->{} reason={}",
                record.getId(), originalOrderId, newOrderId, originalAmountCents, newAmountCents, triggerReason);
    }

    /**
     * 查询停车记录下 PAID 订单，供窗口期判定，任务包 2-1。
     */
    public ParkingOrder findPaidOrderForRecord(Long parkingRecordId) {
        if (parkingRecordId == null) {
            return null;
        }
        return orderMapper.selectPaidByRecordId(parkingRecordId);
    }

    /**
     * 窗口期判定结果，任务包 2-1。
     * @param order PAID 状态订单
     * @param payWindowDeadline 窗口截止时间
     */
    public boolean isWithinPayWindow(ParkingOrder order, LocalDateTime payWindowDeadline) {
        return PayWindowResult.isWithinWindow(order, payWindowDeadline);
    }

    /**
     * 窗口期判定：是否在窗口期内，任务包 2-1。
     */
    public record PayWindowResult(boolean withinWindow, LocalDateTime deadline) {
        public static boolean isWithinWindow(ParkingOrder order, LocalDateTime payWindowDeadline) {
            return payWindowDeadline != null && !LocalDateTime.now().isAfter(payWindowDeadline);
        }
    }

    /**
     * 创建重算订单（超期未出场重计费），任务包 2-1。
     * <p>
     * 原订单保持 PAID（状态日志备注"超期未出场"），以实际停车时长重新计费生成新 PENDING_PAY 订单，
     * 新订单通过 recalc_source_order_id 关联原订单。
     *
     * @param record        停车记录
     * @param feeCents      重新计算的费用（分）
     * @param sourceOrderId 原订单 ID
     * @param exitLaneId    出口车道ID（用于 AT_EXIT 支付后开闸）
     * @return 新创建的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder createRecalcOrder(ParkingRecord record, int feeCents, Long sourceOrderId,
                                           Long exitLaneId) {
        // 对原订单追加"超期未出场"状态日志
        if (sourceOrderId != null) {
            ParkingOrder sourceOrder = orderMapper.selectById(sourceOrderId);
            if (sourceOrder != null) {
                orderStatusLogService.record(sourceOrder, sourceOrder.getStatus(),
                        sourceOrder.getStatus(), currentTriggerSource(), currentOperatorId(),
                        null, "超期未出场，触发重算，生成新订单");
            }
        }

        String remark = "超期未出场重算";
        if (sourceOrderId != null) {
            remark += "，原订单:" + sourceOrderId;
        }
        return createOrderInternal(record, feeCents, null,
                ParkingOrder.PAY_SCENE_AT_EXIT, exitLaneId, sourceOrderId, remark);
    }

    /**
     * 设置订单的支付窗口截止时间和支付场景（供 MockPaymentService pay 后使用），任务包 2-1。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setPayWindowDeadline(Long orderId, LocalDateTime deadline, String payScene) {
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("pay_window_deadline", deadline)
                .set("pay_scene", payScene)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId);
        int updated = orderMapper.update(null, wrapper);
        if (updated > 0) {
            ParkingOrder order = orderMapper.selectById(orderId);
            orderStatusLogService.record(order, order.getStatus(), order.getStatus(),
                    currentTriggerSource(), currentOperatorId(), null,
                    "设置支付窗口截止时间:" + deadline + " 场景:" + payScene);
        }
        return updated > 0;
    }

    /**
     * 按 ID 查询订单。
     */
    public ParkingOrder getById(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    /**
     * 按车牌和车场查询所有欠费中订单。
     * <p>
     * 用于再次出场时检测是否存在未补缴的欠费，根据 arrears.reexit_strategy 决定处理方式。
     * 任务包 2-3。
     *
     * @param plateNumber  标准化车牌号
     * @param parkingLotId 停车场 ID
     * @return 欠费中订单列表（按创建时间倒序），无数据则空列表
     */
    public List<ParkingOrder> getArrearsOrdersByPlate(String plateNumber, Long parkingLotId) {
        return orderMapper.selectArrearsByPlate(parkingLotId, plateNumber);
    }

    /**
     * 按订单号查询订单（支付回调使用，跳过租户拦截）。
     */
    public ParkingOrder getByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    // ==================== 收入报表口径（任务包 1-2 预留，报表在任务包 6-1 实现） ====================

    /**
     * 判断订单是否计入收入统计（实付金额口径）。
     * <p>
     * 已退款（REFUNDED）订单不计入实付收入。
     * DashboardMapper 金额类查询已同步加 status != 'REFUNDED'，任务包 6-1 收入报表统一使用本口径。
     */
    public static boolean isCountedInRevenue(String status) {
        return !ParkingOrder.STATUS_REFUNDED.equals(status);
    }

    // ==================== 内部辅助 ====================

    /**
     * 当前触发源：存在登录用户上下文视为 USER，否则视为 SYSTEM（识别事件/出场等自动链路）。
     */
    private String currentTriggerSource() {
        return TenantContext.getUserId() != null
                ? OrderStatusLog.TRIGGER_USER : OrderStatusLog.TRIGGER_SYSTEM;
    }

    private Long currentOperatorId() {
        return TenantContext.getUserId();
    }

    /**
     * 生成订单号：O{lotId}{yyyyMMdd}{6位序号}。
     */
    private synchronized String generateOrderNo(Long lotId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!dateStr.equals(lastSequenceDate)) {
            sequence.set(0);
            lastSequenceDate = dateStr;
        }
        int seq = sequence.incrementAndGet();
        return String.format("O%d%s%06d", lotId, dateStr, seq);
    }
}
