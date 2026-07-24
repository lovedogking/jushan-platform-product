package com.jushan.platform.modules.parking.service;
import com.jushan.platform.modules.device.service.DeviceService;
import com.jushan.platform.modules.common.service.ParamResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.platform.modules.common.constant.ParamKeys;
import com.jushan.platform.modules.miniapp.entity.MockPaymentConfig;
import com.jushan.platform.modules.miniapp.entity.MockPaymentRecord;
import com.jushan.platform.modules.parking.entity.OrderStatusLog;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.event.PaymentSuccessEvent;
import com.jushan.platform.modules.miniapp.mapper.MockPaymentConfigMapper;
import com.jushan.platform.modules.miniapp.mapper.MockPaymentRecordMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟支付服务（S0-3）。
 * <p>
 * 完全替代真实支付流程（P云 4pyun.com），提供产品级模拟支付能力。
 * 生产环境强制启用，禁止连接任何第三方支付平台。
 * <p>
 * 功能：
 * <ul>
 *   <li>创建模拟支付记录（待支付状态）</li>
 *   <li>确认支付（更新订单为已支付）</li>
 *   <li>定时扫描超时订单并关闭</li>
 *   <li>运营端手动标记支付</li>
 *   <li>按车场配置支付超时时间（任务包 1-1 起：权威来源为车场级参数 {@link ParamKeys#MOCK_PAYMENT_TIMEOUT_MINUTES}，
 *       {@code mock_payment_config.timeout_minutes} 列已废弃，仅作兼容同步）</li>
 *   <li>查询模拟支付流水</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class MockPaymentService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentService.class);

    private final MockPaymentConfigMapper configMapper;
    private final MockPaymentRecordMapper recordMapper;
    private final ParkingOrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ParamResolver paramResolver;
    private final OrderStatusLogService orderStatusLogService;
    private final DeviceService deviceService;
    private final ParkingOrderService parkingOrderService;

    public MockPaymentService(MockPaymentConfigMapper configMapper,
                               MockPaymentRecordMapper recordMapper,
                               ParkingOrderMapper orderMapper,
                               ApplicationEventPublisher eventPublisher,
                               ParamResolver paramResolver,
                               OrderStatusLogService orderStatusLogService,
                               DeviceService deviceService,
                               ParkingOrderService parkingOrderService) {
        this.configMapper = configMapper;
        this.recordMapper = recordMapper;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        this.paramResolver = paramResolver;
        this.orderStatusLogService = orderStatusLogService;
        this.deviceService = deviceService;
        this.parkingOrderService = parkingOrderService;
    }

    // ==================== 核心支付方法 ====================

    /**
     * 准备支付 — 创建模拟支付记录。
     * <p>
     * 为指定订单创建待支付的模拟流水记录，并根据车场配置更新订单过期时间。
     *
     * @param order 已创建的停车订单
     * @return 模拟支付记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long preparePay(ParkingOrder order) {
        // 获取车场配置
        MockPaymentConfig config = getOrCreateConfig(order.getTenantId(), order.getParkingLotId());

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("模拟支付未启用，但流程继续（默认启用）: parkingLotId={}", order.getParkingLotId());
        }

        // 更新订单过期时间（超时时长权威来源：任务包 1-1 车场级参数，车场级→全局→默认 15）
        int timeoutMinutes = paramResolver.getInt(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, order.getParkingLotId(), 15);
        orderMapper.updateOrderExpiry(order.getId(), LocalDateTime.now().plusMinutes(timeoutMinutes));

        // 创建模拟支付记录
        MockPaymentRecord record = new MockPaymentRecord();
        record.setTenantId(order.getTenantId());
        record.setParkingLotId(order.getParkingLotId());
        record.setOrderId(order.getId());
        record.setPlateNumber(order.getPlateNumber());
        record.setAmount(BigDecimal.valueOf(order.getPayableAmount(), 2));
        record.setStatus(MockPaymentRecord.STATUS_PENDING);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.insert(record);

        log.info("模拟支付记录已创建: orderId={} recordId={} timeoutMinutes={}",
                order.getId(), record.getId(), timeoutMinutes);
        return record.getId();
    }

    /**
     * 确认支付 — 模拟支付成功。
     * <p>
     * 由小程序/前端调用，替代真实的 wx.requestPayment 流程。
     * 将模拟支付记录标记为已支付，并更新订单状态为 PAID。
     *
     * @param orderId 订单ID
     * @param paidBy  支付人（用户ID或系统标记）
     * @return true=支付成功, false=支付失败（订单已过期或状态异常）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmPay(Long orderId, String paidBy) {
        // 查询订单
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在: orderId={}", orderId);
            return false;
        }

        // 校验订单状态
        if (!ParkingOrder.STATUS_PENDING_PAY.equals(order.getStatus())
                && !ParkingOrder.STATUS_PAYING.equals(order.getStatus())
                && !ParkingOrder.STATUS_ARREARS.equals(order.getStatus())) {
            log.warn("订单状态不允许支付: orderId={} status={}", orderId, order.getStatus());
            return false;
        }

        // 检查是否过期
        if (order.getExpiredAt() != null && order.getExpiredAt().isBefore(LocalDateTime.now())) {
            log.warn("订单已过期: orderId={} expiredAt={}", orderId, order.getExpiredAt());
            // 更新支付记录为超时
            markRecordTimeout(orderId);
            return false;
        }

        // 更新模拟支付记录
        QueryWrapper<MockPaymentRecord> recordQuery = new QueryWrapper<MockPaymentRecord>()
                .eq("order_id", orderId)
                .eq("status", MockPaymentRecord.STATUS_PENDING)
                .last("LIMIT 1");
        MockPaymentRecord record = recordMapper.selectOne(recordQuery);
        if (record != null) {
            record.setStatus(MockPaymentRecord.STATUS_PAID);
            record.setPaidAt(LocalDateTime.now());
            record.setPaidBy(paidBy);
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
        }

        // 更新订单状态为已支付
        String paySerial = "MOCK-" + orderId + "-" + System.currentTimeMillis();
        int updated;
        boolean isArrearsPay = ParkingOrder.STATUS_ARREARS.equals(order.getStatus());
        if (isArrearsPay) {
            // ARREARS 订单补缴：直接 COMPLETED（不走 PAID 中间态）
            updated = orderMapper.update(null,
                    new UpdateWrapper<ParkingOrder>()
                            .set("status", ParkingOrder.STATUS_COMPLETED)
                            .set("paid_amount", order.getPayableAmount())
                            .set("pay_time", LocalDateTime.now())
                            .set("pay_serial", paySerial)
                            .set("updated_at", LocalDateTime.now())
                            .eq("id", orderId)
                            .eq("status", ParkingOrder.STATUS_ARREARS));
        } else {
            updated = orderMapper.markPaidStatus(
                    orderId,
                    paySerial,
                    order.getPayableAmount(),
                    LocalDateTime.now()
            );
        }

        if (updated > 0) {
            // 状态流转留痕（任务包 1-2）
            String targetStatus = isArrearsPay ? ParkingOrder.STATUS_COMPLETED : ParkingOrder.STATUS_PAID;
            orderStatusLogService.record(order, order.getStatus(), targetStatus,
                    OrderStatusLog.TRIGGER_USER, null, paidBy, "模拟支付确认");
            // 发布支付成功事件（同步），监听者包括月卡续费生效
            eventPublisher.publishEvent(new PaymentSuccessEvent(order, paySerial, paidBy));

            // 任务包 2-1 + 2-3：支付后处理
            handlePostPay(order, paySerial, paidBy);
        }

        log.info("模拟支付成功: orderId={} paySerial={} paidBy={} updated={}",
                orderId, paySerial, paidBy, updated > 0);
        return updated > 0;
    }

    /**
     * 支付后处理（任务包 2-1）：根据支付场景决定后续动作。
     * <ul>
     *   <li>AT_EXIT（出口缴费）：立即开闸 + 订单完成</li>
     *   <li>ADVANCE（提前缴费）：写入 pay_window_deadline，订单保持 PAID 待出场</li>
     * </ul>
     */
    private void handlePostPay(ParkingOrder order, String paySerial, String paidBy) {
        // 任务包 2-3：合并订单支付后，逐条补缴关联的欠费订单
        String arrearsIds = order.getArrearsOrderIds();
        if (arrearsIds != null && !arrearsIds.isEmpty()) {
            processArrearsChainPayment(order, arrearsIds, paidBy);
        }

        String payScene = order.getPayScene();
        if (ParkingOrder.PAY_SCENE_AT_EXIT.equals(payScene)) {
            handleAtExitPostPay(order, paidBy);
        } else {
            handleAdvancePostPay(order);
        }
    }

    /**
     * AT_EXIT 支付后处理：开闸 + 完成订单。
     */
    private void handleAtExitPostPay(ParkingOrder order, String paidBy) {
        Long exitLaneId = order.getExitLaneId();
        if (exitLaneId != null) {
            try {
                deviceService.openGateByLane(exitLaneId, "出口缴费自动开闸（订单:" + order.getOrderNo() + "）");
                log.info("出口缴费开闸成功: orderId={} laneId={}", order.getId(), exitLaneId);
            } catch (Exception e) {
                log.warn("出口缴费开闸失败（订单仍标记为已支付，需人工处理）: orderId={} laneId={} error={}",
                        order.getId(), exitLaneId, e.getMessage());
                orderStatusLogService.record(order, ParkingOrder.STATUS_PAID,
                        ParkingOrder.STATUS_PAID, OrderStatusLog.TRIGGER_SYSTEM, null,
                        paidBy, "开闸失败: " + e.getMessage() + "，请人工开闸");
                return;
            }
        } else {
            log.warn("AT_EXIT 支付但缺 exitLaneId，跳过开闸: orderId={}", order.getId());
        }
        LocalDateTime exitTime = LocalDateTime.now();
        parkingOrderService.completeOrder(order.getId(), exitTime);
        log.info("出口缴费订单完成: orderId={} exitTime={}", order.getId(), exitTime);
    }

    /**
     * ADVANCE 支付后处理：设置支付窗口截止时间。
     */
    private void handleAdvancePostPay(ParkingOrder order) {
        int windowMinutes = paramResolver.getInt(ParamKeys.PAY_EXIT_WINDOW_MINUTES,
                order.getParkingLotId(), 15);
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(windowMinutes);
        parkingOrderService.setPayWindowDeadline(order.getId(), deadline,
                ParkingOrder.PAY_SCENE_ADVANCE);
        log.info("提前缴费窗口期已设置: orderId={} deadline={} windowMinutes={}",
                order.getId(), deadline, windowMinutes);
    }

    /**
     * 运营端手动标记支付。
     * <p>
     * 用于测试或特殊情况，由运营管理员手动将订单标记为已支付。
     *
     * @param orderId    订单ID
     * @param operatorId 操作人
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean manualMarkPaid(Long orderId, String operatorId) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }

        // 不管订单当前状态，强制标记为已支付
        String paySerial = "MANUAL-" + orderId + "-" + System.currentTimeMillis();
        int updated = orderMapper.markPaidStatus(
                orderId,
                paySerial,
                order.getPayableAmount(),
                LocalDateTime.now()
        );

        if (updated > 0) {
            // 更新或创建支付记录
            QueryWrapper<MockPaymentRecord> recordQuery = new QueryWrapper<MockPaymentRecord>()
                    .eq("order_id", orderId)
                    .last("LIMIT 1");
            MockPaymentRecord record = recordMapper.selectOne(recordQuery);
            if (record != null) {
                record.setStatus(MockPaymentRecord.STATUS_PAID);
                record.setPaidAt(LocalDateTime.now());
                record.setPaidBy(operatorId);
                record.setUpdatedAt(LocalDateTime.now());
                recordMapper.updateById(record);
            } else {
                record = new MockPaymentRecord();
                record.setTenantId(order.getTenantId());
                record.setParkingLotId(order.getParkingLotId());
                record.setOrderId(orderId);
                record.setPlateNumber(order.getPlateNumber());
                record.setAmount(BigDecimal.valueOf(order.getPayableAmount(), 2));
                record.setStatus(MockPaymentRecord.STATUS_PAID);
                record.setPaidAt(LocalDateTime.now());
                record.setPaidBy(operatorId);
                record.setCreatedAt(LocalDateTime.now());
                record.setUpdatedAt(LocalDateTime.now());
                recordMapper.insert(record);
            }

            // 状态流转留痕（任务包 1-2）：手动标记 → 已支付
            orderStatusLogService.record(order, order.getStatus(), ParkingOrder.STATUS_PAID,
                    OrderStatusLog.TRIGGER_USER, null, operatorId, "运营端手动标记支付");
            // 发布支付成功事件（同步），监听者包括月卡续费生效
            eventPublisher.publishEvent(new PaymentSuccessEvent(order, paySerial, operatorId));

            // 任务包 2-1：支付后处理
            handlePostPay(order, paySerial, operatorId);

            log.info("手动标记支付成功: orderId={} operatorId={}", orderId, operatorId);
        }

        return updated > 0;
    }

    // ==================== 定时任务 ====================

    /**
     * 每 5 分钟扫描一次超时订单（任务包 2-2）。
     * <p>
     * 查找所有待支付（PENDING_PAY/PAYING）且已超过过期时间的订单，
     * 将其标记为 CANCELLED，并将关联的模拟支付记录标记为 TIMEOUT。
     * <p>
     * <strong>超时阈值来源</strong>：订单的 {@code expired_at} 在 {@link #preparePay(ParkingOrder)}
     * 时通过 {@code paramResolver.getInt(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, parkingLotId, 15)}
     * 设置（车场级参数权威源，任务包 1-1）。
     * 超时关闭的订单将在车辆再次出口识别时由 {@link ExitService#handleExit} 重新计费，
     * 新订单通过 {@code recalc_source_order_id} 关联原 CANCELLED 订单。
     */
    @Scheduled(fixedRate = 300_000) // 5分钟
    public void timeoutClose() {
        log.debug("开始扫描超时订单...");
        LocalDateTime now = LocalDateTime.now();

        // 查询所有已过期的待支付/支付中订单
        List<ParkingOrder> expiredOrders = orderMapper.selectList(
                new QueryWrapper<ParkingOrder>()
                        .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING)
                        .lt("expired_at", now)
                        .isNull("deleted_at")
                        .last("LIMIT 100")
        );

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时订单，开始处理", expiredOrders.size());

        for (ParkingOrder order : expiredOrders) {
            try {
                // 取消订单（条件更新：仅 PENDING_PAY/PAYING + 已过期）
                int cancelled = orderMapper.cancelExpiredOrder(order.getId());
                if (cancelled > 0) {
                    // 状态流转留痕（任务包 1-2）：定时任务超时关闭 → 已取消
                    orderStatusLogService.record(order, order.getStatus(),
                            ParkingOrder.STATUS_CANCELLED, OrderStatusLog.TRIGGER_TIMER,
                            null, "系统超时关闭", "支付超时自动关闭");
                }
                // 标记支付记录超时
                markRecordTimeout(order.getId());
                log.info("超时订单已关闭: orderId={} plate={}", order.getId(), order.getPlateNumber());
            } catch (Exception e) {
                log.error("超时订单处理失败: orderId={}", order.getId(), e);
            }
        }
    }

    // ==================== 配置管理 ====================

    /**
     * 获取车场模拟支付配置。
     * 如果不存在，则创建默认配置。
     */
    public MockPaymentConfig getOrCreateConfig(Long tenantId, Long parkingLotId) {
        MockPaymentConfig config = configMapper.selectOne(
                new QueryWrapper<MockPaymentConfig>()
                        .eq("parking_lot_id", parkingLotId)
                        .isNull("deleted_at")
        );
        if (config == null) {
            config = new MockPaymentConfig();
            config.setTenantId(tenantId);
            config.setParkingLotId(parkingLotId);
            config.setTimeoutMinutes(15);
            config.setEnabled(true);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);
            log.info("已创建默认模拟支付配置: parkingLotId={}", parkingLotId);
        }
        return config;
    }

    /**
     * 查询车场配置。
     */
    public MockPaymentConfig getConfig(Long parkingLotId) {
        return configMapper.selectOne(
                new QueryWrapper<MockPaymentConfig>()
                        .eq("parking_lot_id", parkingLotId)
                        .isNull("deleted_at")
        );
    }

    /**
     * 更新车场配置。
     *
     * @param parkingLotId    车场ID
     * @param tenantId        租户ID（由调用方从可信上下文获取）
     * @param timeoutMinutes  超时时间（可选）
     * @param enabled         启用状态（可选）
     */
    @Transactional(rollbackFor = Exception.class)
    public MockPaymentConfig updateConfig(Long parkingLotId, Long tenantId,
                                           Integer timeoutMinutes, Boolean enabled) {
        MockPaymentConfig config = getOrCreateConfig(tenantId, parkingLotId);
        if (timeoutMinutes != null) {
            // 权威写入：车场级参数体系（任务包 1-1）
            paramResolver.setLotParam(parkingLotId, ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES,
                    String.valueOf(timeoutMinutes));
            // 已废弃列：仅兼容同步，便于回显与安全回滚（非权威读取源）
            config.setTimeoutMinutes(timeoutMinutes);
        }
        if (enabled != null) {
            config.setEnabled(enabled);
        }
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        log.info("模拟支付配置已更新: parkingLotId={} timeoutMinutes={} enabled={}",
                parkingLotId, config.getTimeoutMinutes(), config.getEnabled());
        return config;
    }

    // ==================== 流水查询 ====================

    /**
     * 分页查询模拟支付流水。
     */
    public IPage<MockPaymentRecord> queryRecords(Long parkingLotId, String plateNumber,
                                                  Integer status, int page, int size) {
        QueryWrapper<MockPaymentRecord> query = new QueryWrapper<>();
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        if (plateNumber != null && !plateNumber.isEmpty()) {
            query.eq("plate_number", plateNumber.toUpperCase());
        }
        if (status != null) {
            query.eq("status", status);
        }
        query.orderByDesc("created_at");

        return recordMapper.selectPage(new Page<>(page, size), query);
    }

    // ==================== 内部方法 ====================

    /**
     * 将订单关联的待支付记录标记为超时。
     */
    private void markRecordTimeout(Long orderId) {
        MockPaymentRecord record = recordMapper.selectOne(
                new QueryWrapper<MockPaymentRecord>()
                        .eq("order_id", orderId)
                        .eq("status", MockPaymentRecord.STATUS_PENDING)
                        .last("LIMIT 1")
        );
        if (record != null) {
            record.setStatus(MockPaymentRecord.STATUS_TIMEOUT);
            record.setPaidBy("系统超时关闭");
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
        }
    }

    /**
     * 处理合并订单的关联欠费补缴（任务包 2-3）。
     * <p>
     * 合并订单支付完成后，逐条将 arrearsOrderIds 中的欠费订单从 ARREARS → COMPLETED。
     * 单条失败记录日志不阻断，运营端订单中心可人工核实。
     */
    private void processArrearsChainPayment(ParkingOrder mergedOrder, String arrearsIdsJson, String paidBy) {
        try {
            String cleaned = arrearsIdsJson.replace("[", "").replace("]", "").replace(" ", "");
            if (cleaned.isEmpty()) {
                return;
            }
            String[] parts = cleaned.split(",");
            int successCount = 0;
            int failCount = 0;
            for (String part : parts) {
                try {
                    Long arrearsOrderId = Long.parseLong(part.trim());
                    boolean ok = parkingOrderService.payArrears(arrearsOrderId,
                            OrderStatusLog.TRIGGER_USER, null);
                    if (ok) {
                        successCount++;
                        log.info("合并订单关联欠费补缴成功: mergedOrderId={} arrearsOrderId={}",
                                mergedOrder.getId(), arrearsOrderId);
                    } else {
                        failCount++;
                        log.error("合并订单关联欠费补缴失败: mergedOrderId={} arrearsOrderId={}",
                                mergedOrder.getId(), arrearsOrderId);
                    }
                } catch (NumberFormatException e) {
                    failCount++;
                    log.error("合并订单 arrearsOrderIds 格式异常: mergedOrderId={} part={}",
                            mergedOrder.getId(), part);
                }
            }
            log.info("合并订单欠费补缴完成: mergedOrderId={} success={} fail={}",
                    mergedOrder.getId(), successCount, failCount);
        } catch (Exception e) {
            log.error("合并订单欠费补缴异常: mergedOrderId={}", mergedOrder.getId(), e);
        }
    }
}
