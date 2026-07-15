package com.jushan.platform.modules.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.vehicle.dto.VehicleRenewalCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.service.VehicleRenewalService;
import com.jushan.platform.modules.vehicle.vo.RenewalOrderVO;
import com.jushan.platform.modules.vehicle.vo.RenewalPreviewVO;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 月卡/固定车续费服务实现。
 * <p>
 * 闭环：管理员发起续费（创建 MONTH_RENEW 订单）→ 支付回调/查询确认 → 回写有效期。
 * 时间计算使用 {@link LocalDate}，正确处理跨月、跨年。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class VehicleRenewalServiceImpl implements VehicleRenewalService {

    /** 支付回调等无用户上下文场景的操作人标识 */
    private static final long SYSTEM_OPERATOR_ID = -1L;
    private static final String SYSTEM_OPERATOR_NAME = "PAY_CALLBACK";

    private final SysVehicleMapper vehicleMapper;
    private final ParkingOrderMapper orderMapper;
    private final ParkingSessionMapper sessionMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String lastSequenceDate = "";

    public VehicleRenewalServiceImpl(SysVehicleMapper vehicleMapper,
                                     ParkingOrderMapper orderMapper,
                                     ParkingSessionMapper sessionMapper,
                                     AuditService auditService,
                                     ObjectMapper objectMapper) {
        this.vehicleMapper = vehicleMapper;
        this.orderMapper = orderMapper;
        this.sessionMapper = sessionMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenewalOrderVO createRenewalOrder(Long vehicleId, VehicleRenewalCmd cmd) {
        Long tenantId = TenantContext.requireTenantId();

        SysVehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        // 仅月租车 / 储值车可续费
        String type = vehicle.getVehicleType();
        if (!SysVehicle.TYPE_MONTHLY.equals(type) && !SysVehicle.TYPE_PREPAID.equals(type)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅月租车/储值车可发起续费");
        }
        if (cmd.getRenewalMonths() == null || cmd.getRenewalMonths() <= 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "续费月数必须大于0");
        }
        if (cmd.getAmountCents() == null || cmd.getAmountCents() < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "续费金额非法");
        }

        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(vehicle.getParkingLotId());
        order.setRefId(vehicleId);
        order.setOrderNo(generateOrderNo(vehicle.getParkingLotId()));
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        order.setPlateNumber(vehicle.getPlateNumber());
        order.setAmountCents(cmd.getAmountCents());
        order.setDiscountAmount(0);
        order.setPointsDiscount(0);
        order.setPayableAmount(cmd.getAmountCents());
        order.setPaidAmount(0);
        order.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        order.setPayChannel(cmd.getPayChannel());
        order.setRenewalMonths(cmd.getRenewalMonths());
        order.setOperatorId(TenantContext.requireUserId());
        order.setExpiredAt(LocalDateTime.now().plusMinutes(15));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        log.info("月卡续费订单创建成功: orderId={} orderNo={} vehicleId={} months={} amount={}",
                order.getId(), order.getOrderNo(), vehicleId, cmd.getRenewalMonths(), cmd.getAmountCents());
        return toRenewalOrderVO(order);
    }

    @Override
    public RenewalPreviewVO previewRenewal(Long vehicleId, int renewalMonths) {
        Long tenantId = TenantContext.requireTenantId();

        SysVehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        LocalDate oldEnd = vehicle.getValidEndDate();
        LocalDate today = LocalDate.now();
        LocalDate base = (oldEnd == null || oldEnd.isBefore(today)) ? today : oldEnd;
        LocalDate newEnd = base.plusMonths(renewalMonths);

        RenewalPreviewVO vo = new RenewalPreviewVO();
        vo.setVehicleId(vehicleId);
        vo.setPlateNumber(vehicle.getPlateNumber());
        vo.setVehicleType(vehicle.getVehicleType());
        vo.setCurrentEndDate(oldEnd);
        vo.setNewEndDate(newEnd);
        vo.setRenewalMonths(renewalMonths);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenewalOrderVO applyRenewalEffect(Long orderId, String paySerial) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "续费订单不存在");
        }
        if (!ParkingOrder.ORDER_TYPE_MONTH_RENEW.equals(order.getOrderType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "非月卡续费订单");
        }

        // 幂等守卫：仅当订单处于未完结状态时才置为 COMPLETED 并触发续费生效
        int applied = orderMapper.update(null, new UpdateWrapper<ParkingOrder>()
                .set("status", ParkingOrder.STATUS_COMPLETED)
                .set("pay_serial", paySerial)
                .set("pay_time", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now())
                .eq("id", orderId)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING, ParkingOrder.STATUS_PAID));
        if (applied == 0) {
            // 已生效（幂等），返回当前结果
            log.info("续费订单已生效，幂等返回: orderId={}", orderId);
            return toRenewalOrderVO(orderMapper.selectById(orderId));
        }

        // 以订单可信租户建立上下文，保证后续车辆/在场记录查询被正确限定的同时可正常执行
        TenantContext.Snapshot prev = TenantContext.get();
        TenantContext.set(new TenantContext.Snapshot(
                order.getTenantId(), order.getOperatorId(), TenantContext.USER_TYPE_TENANT, "", ""));
        try {
            doApplyEffect(order, paySerial);
        } finally {
            if (prev != null) {
                TenantContext.set(prev);
            } else {
                TenantContext.clear();
            }
        }

        return toRenewalOrderVO(orderMapper.selectById(orderId));
    }

    /**
     * 执行续费生效：延长有效期、回写在场车辆类型（原月卡过期场景）、记录审计。
     */
    private void doApplyEffect(ParkingOrder order, String paySerial) {
        Long vehicleId = order.getRefId();
        SysVehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "续费关联车辆不存在");
        }

        LocalDate oldEnd = vehicle.getValidEndDate();
        String oldStatus = vehicle.getStatus();
        LocalDate today = LocalDate.now();
        boolean wasExpired = (oldEnd != null && oldEnd.isBefore(today));

        // 计算新有效期：过期/无有效期从今天起算，否则从原有效期顺延
        LocalDate base = (oldEnd == null || oldEnd.isBefore(today)) ? today : oldEnd;
        LocalDate newEnd = base.plusMonths(order.getRenewalMonths());

        // 更新车辆有效期与状态（乐观锁由 MyBatis-Plus version 字段保护）
        UpdateWrapper<SysVehicle> uw = new UpdateWrapper<SysVehicle>()
                .set("valid_end_date", newEnd)
                .set("updated_at", LocalDateTime.now());
        if (SysVehicle.STATUS_EXPIRED.equals(oldStatus) || SysVehicle.STATUS_DISABLED.equals(oldStatus)) {
            uw.set("status", SysVehicle.STATUS_ACTIVE);
        }
        uw.eq("id", vehicleId);
        vehicleMapper.update(null, uw);

        // PRD §4.1 入场策略第 4 条：原月卡已过期且当前在场 → 更新在场车辆类型为固定车
        boolean inParkUpdated = false;
        if (wasExpired) {
            ParkingSession session = sessionMapper.selectInByPlateAndLot(
                    vehicle.getPlateNumber(), vehicle.getParkingLotId(), order.getTenantId());
            if (session != null) {
                sessionMapper.update(null, new UpdateWrapper<ParkingSession>()
                        .set("vehicle_type", vehicle.getVehicleType())
                        .set("updated_at", LocalDateTime.now())
                        .eq("id", session.getId()));
                inParkUpdated = true;
                log.info("过期月卡续费后将在场车辆类型更新为固定车: sessionId={} vehicleType={}",
                        session.getId(), vehicle.getVehicleType());
            }
        }

        // 审计日志：操作人、续费月数、金额、续费前/后有效期
        String beforeValue = toJson(Map.of(
                "validEndDate", oldEnd == null ? "" : oldEnd.toString(),
                "status", oldStatus,
                "vehicleType", vehicle.getVehicleType()));
        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("validEndDate", newEnd.toString());
        afterMap.put("status", SysVehicle.STATUS_EXPIRED.equals(oldStatus)
                || SysVehicle.STATUS_DISABLED.equals(oldStatus) ? SysVehicle.STATUS_ACTIVE : oldStatus);
        afterMap.put("renewalMonths", order.getRenewalMonths());
        afterMap.put("amountCents", order.getPayableAmount());
        afterMap.put("orderNo", order.getOrderNo());
        afterMap.put("payChannel", order.getPayChannel());
        afterMap.put("paySerial", paySerial);
        afterMap.put("inParkUpdated", inParkUpdated);
        String afterValue = toJson(afterMap);

        Long operatorId = order.getOperatorId() != null ? order.getOperatorId() : SYSTEM_OPERATOR_ID;
        String operatorName = order.getOperatorId() != null ? null : SYSTEM_OPERATOR_NAME;
        auditService.writeAuditLog(
                order.getTenantId(), "vehicle", String.valueOf(vehicleId), "vehicle_renew",
                operatorId, operatorName,
                beforeValue, afterValue,
                AuditService.RESULT_SUCCESS, null, "月卡续费-支付成功生效", null);

        log.info("月卡续费生效完成: orderId={} vehicleId={} oldEnd={} newEnd={} inParkUpdated={}",
                order.getId(), vehicleId, oldEnd, newEnd, inParkUpdated);
    }

    @Override
    public RenewalOrderVO applyRenewalByPlate(Long tenantId, Long parkingLotId, String plateNumber, String paySerial) {
        if (plateNumber == null || plateNumber.isEmpty()) {
            return null;
        }
        SysVehicle vehicle = vehicleMapper.selectByPlateNumber(plateNumber.toUpperCase(), tenantId);
        if (vehicle == null) {
            log.warn("续费回调未匹配到车辆: tenantId={} plate={}", tenantId, plateNumber);
            return null;
        }

        ParkingOrder order = orderMapper.selectOne(new QueryWrapper<ParkingOrder>()
                .eq("ref_id", vehicle.getId())
                .eq("order_type", ParkingOrder.ORDER_TYPE_MONTH_RENEW)
                .in("status", ParkingOrder.STATUS_PENDING_PAY, ParkingOrder.STATUS_PAYING)
                .eq("tenant_id", tenantId)
                .isNull("deleted_at")
                .orderByDesc("created_at")
                .last("LIMIT 1"));
        if (order == null) {
            log.warn("续费回调未找到待生效订单: vehicleId={} plate={}", vehicle.getId(), plateNumber);
            return null;
        }

        return applyRenewalEffect(order.getId(), paySerial);
    }

    // ==================== 内部方法 ====================

    private RenewalOrderVO toRenewalOrderVO(ParkingOrder order) {
        RenewalOrderVO vo = new RenewalOrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setAmountCents(order.getPayableAmount());
        vo.setStatus(order.getStatus());
        vo.setVehicleId(order.getRefId());
        vo.setRenewalMonths(order.getRenewalMonths());
        vo.setPlateNumber(order.getPlateNumber());
        if (order.getRefId() != null) {
            SysVehicle v = vehicleMapper.selectById(order.getRefId());
            if (v != null) {
                vo.setNewValidEndDate(v.getValidEndDate());
            }
        }
        return vo;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private synchronized String generateOrderNo(Long lotId) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!dateStr.equals(lastSequenceDate)) {
            sequence.set(0);
            lastSequenceDate = dateStr;
        }
        int seq = sequence.incrementAndGet();
        // MR 前缀区分月卡续费订单，避免与主订单号 O 前缀冲突
        return String.format("MR%d%s%06d", lotId, dateStr, seq);
    }
}
