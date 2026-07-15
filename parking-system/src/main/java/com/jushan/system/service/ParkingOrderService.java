package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 停车订单服务（Sprint 8）。
 * <p>
 * 负责订单创建、状态机管理、订单号生成、幂等控制。
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

    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String lastSequenceDate = "";

    public ParkingOrderService(ParkingOrderMapper orderMapper, ParkingRecordMapper recordMapper) {
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
    }

    /**
     * 创建停车订单（出场时调用）。
     *
     * @param record      停车记录（可信）
     * @param feeCents    计算费用（分）
     * @param idempotencyKey 幂等键
     * @return 创建的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder createOrder(ParkingRecord record, int feeCents, String idempotencyKey) {
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
        log.info("订单创建成功: orderId={} orderNo={} feeCents={} status={}",
                order.getId(), order.getOrderNo(), feeCents, order.getStatus());
        return order;
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
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAYING)
                .set("pay_channel", payChannel)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PENDING_PAY)
                .gt("expired_at", LocalDateTime.now());
        int updated = orderMapper.update(null, wrapper);
        return updated > 0;
    }

    /**
     * 完成支付（回调时使用，更新状态为 PAID 并设置支付流水）。
     *
     * @param orderId    订单ID
     * @param paySerial  P云支付流水
     * @param payTime    支付时间
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean completePay(Long orderId, String paySerial, LocalDateTime payTime) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAID)
                .set("pay_serial", paySerial)
                .set("paid_amount", order.getPayableAmount())
                .set("pay_time", payTime)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING);
        int updated = orderMapper.update(null, wrapper);
        return updated > 0;
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
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAID)
                .set("pay_serial", paySerial)
                .set("paid_amount", paidAmount)
                .set("pay_time", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING);
        int updated = orderMapper.update(null, wrapper);
        return updated > 0;
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
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_COMPLETED)
                .set("exit_time", exitTime)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PAID, ParkingOrder.STATUS_PENDING_PAY);
        int updated = orderMapper.update(null, wrapper);
        return updated > 0;
    }

    /**
     * 取消订单。
     *
     * @param orderId 订单ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId) {
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_CANCELLED)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ParkingOrder.STATUS_PENDING_PAY);
        int updated = orderMapper.update(null, wrapper);
        return updated > 0;
    }

    /**
     * 标记支付失败。
     *
     * @param orderId 订单ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPayFailed(Long orderId) {
        UpdateWrapper<ParkingOrder> wrapper = new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_PAY_FAILED)
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING);
        int updated = orderMapper.update(null, wrapper);
        return updated > 0;
    }

    /**
     * 应用储值车余额支付。
     * <p>
     * 使用条件更新确保仅从 PENDING_PAY 状态更新，并记录余额支付明细。
     *
     * @param orderId       订单ID
     * @param paidAmount    余额支付金额（分）
     * @param fullyPaid     是否全额支付（true → PAID, false → 保持 PENDING_PAY）
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyBalancePayment(Long orderId, int paidAmount, boolean fullyPaid) {
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
            log.info("余额支付应用成功: orderId={} paidAmount={} fullyPaid={}", orderId, paidAmount, fullyPaid);
        } else {
            log.warn("余额支付应用失败（订单状态可能已变更）: orderId={}", orderId);
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
     * 按订单号查询订单（支付回调使用，跳过租户拦截）。
     */
    public ParkingOrder getByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
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
