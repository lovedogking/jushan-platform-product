package com.jushan.platform.modules.miniapp.service;
import com.jushan.platform.modules.common.service.ParamResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.common.constant.ParamKeys;
import com.jushan.platform.modules.miniapp.dto.MonthlyPassCreateRequest;
import com.jushan.platform.modules.miniapp.dto.MonthlyPassRenewRequest;
import com.jushan.platform.modules.miniapp.entity.MonthlyPass;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.vehicle.entity.VehicleRenewalLog;
import com.jushan.platform.modules.miniapp.mapper.MonthlyPassMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.vehicle.mapper.VehicleRenewalLogMapper;
import com.jushan.platform.modules.miniapp.vo.MonthlyPassVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 月卡管理服务（任务包 3-1：基于 independent monthly_pass 实体）。
 * <p>
 * 替代原 facade（sys_vehicle.vehicleType=MONTHLY），提供月卡全生命周期管理：
 * <ul>
 *   <li>录入（创建 monthly_pass + 生成已支付月卡订单）</li>
 *   <li>续期（延长有效期 + 生成续费订单 + 记录日志）</li>
 *   <li>注销（置 CANCELLED）</li>
 *   <li>分页列表 + 到期预警</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Service
public class MonthlyPassService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassService.class);

    private final MonthlyPassMapper monthlyPassMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final VehicleRenewalLogMapper renewalLogMapper;
    private final ParamResolver paramResolver;

    public MonthlyPassService(MonthlyPassMapper monthlyPassMapper,
                              ParkingLotMapper parkingLotMapper,
                              ParkingOrderMapper parkingOrderMapper,
                              VehicleRenewalLogMapper renewalLogMapper,
                              ParamResolver paramResolver) {
        this.monthlyPassMapper = monthlyPassMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.paramResolver = paramResolver;
    }

    // ==================== 录入月卡 ====================

    /**
     * 录入月卡并生成已支付订单（一个事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO create(MonthlyPassCreateRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 1. 唯一性校验
        checkDuplicateActive(tenantId, request.getParkingLotId(), plate);

        // 2. 插入 monthly_pass
        MonthlyPass pass = new MonthlyPass();
        pass.setTenantId(tenantId);
        pass.setParkingLotId(request.getParkingLotId());
        pass.setPlateNumber(plate);
        pass.setPlateColor(request.getPlateColor());
        pass.setVehicleType(request.getVehicleType());
        pass.setValidStartDate(request.getValidStartDate());
        pass.setValidEndDate(request.getValidEndDate());
        pass.setAmountCents(request.getPaidAmountCents());
        pass.setPaidAmountCents(request.getPaidAmountCents());
        pass.setPayMethod(request.getPayMethod());
        // 读取审核模式决定 review_status
        String reviewMode = paramResolver.getString(ParamKeys.MONTHLY_FIXED_REVIEW_MODE, null);
        if ("MANUAL".equals(reviewMode)) {
            pass.setReviewStatus(MonthlyPass.REVIEW_PENDING);
            pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        } else {
            pass.setReviewStatus(MonthlyPass.REVIEW_APPROVED);
            pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        }
        pass.setSource(MonthlyPass.SOURCE_ADMIN);
        pass.setApplicantId(null);
        pass.setOwnerName(request.getOwnerName());
        pass.setOwnerPhone(request.getOwnerPhone());
        pass.setRemark(request.getRemark());
        pass.setCreatedAt(LocalDateTime.now());
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.insert(pass);

        // 3. 生成已支付月卡订单
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(request.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(plate);
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTHLY_PASS);
        order.setAmountCents(request.getPaidAmountCents());
        order.setPaidAmount(request.getPaidAmountCents());
        order.setPayableAmount(request.getPaidAmountCents());
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(mapPayChannel(request.getPayMethod()));
        order.setPayTime(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        log.info("月卡录入成功: passId={} plate={} lotId={} orderId={} payMethod={} paid={}",
                pass.getId(), plate, request.getParkingLotId(), order.getId(),
                request.getPayMethod(), request.getPaidAmountCents());

        return toVO(pass, getParkingLotName(request.getParkingLotId()), order.getId());
    }

    // ==================== 续期 ====================

    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO renew(Long id, MonthlyPassRenewRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrow(id, tenantId);

        LocalDate oldValidEnd = pass.getValidEndDate();

        // 计算新有效期（从当前有效期末尾顺延）
        LocalDate newValidEnd = (oldValidEnd != null ? oldValidEnd : LocalDate.now())
                .plusMonths(request.getRenewalMonths());

        // 更新月卡有效期
        pass.setValidEndDate(newValidEnd);
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        // 创建续费订单（MONTH_RENEW）
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(pass.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(pass.getPlateNumber());
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        order.setRenewalMonths(request.getRenewalMonths());
        order.setAmountCents(request.getAmountCents());
        order.setPaidAmount(request.getAmountCents());
        order.setPayableAmount(request.getAmountCents());
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
        order.setPayTime(LocalDateTime.now());
        order.setOperatorId(TenantContext.requireUserId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        // 写入续费日志
        VehicleRenewalLog logEntry = new VehicleRenewalLog();
        logEntry.setTenantId(tenantId);
        logEntry.setParkingLotId(pass.getParkingLotId());
        logEntry.setMonthlyPassId(pass.getId());    // 新体系：关联 monthly_pass
        logEntry.setVehicleId(null);                 // 旧体系不再使用
        logEntry.setPlateNumber(pass.getPlateNumber());
        logEntry.setOrderId(order.getId());
        logEntry.setRenewalMonths(request.getRenewalMonths());
        logEntry.setAmountCents(request.getAmountCents());
        logEntry.setOldValidEnd(oldValidEnd);
        logEntry.setNewValidEnd(newValidEnd);
        logEntry.setOperatorId(TenantContext.requireUserId());
        logEntry.setRemark(request.getRemark());
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        renewalLogMapper.insert(logEntry);

        log.info("月卡续期成功: passId={} plate={} months={} oldEnd={} newEnd={}",
                pass.getId(), pass.getPlateNumber(), request.getRenewalMonths(),
                oldValidEnd, newValidEnd);

        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    // ==================== 注销 ====================

    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO cancel(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrow(id, tenantId);

        pass.setPassStatus(MonthlyPass.STATUS_CANCELLED);
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        log.info("月卡注销成功: passId={} plate={}", pass.getId(), pass.getPlateNumber());

        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    // ==================== 列表查询 ====================

    public IPage<MonthlyPassVO> pageList(String plateNumber, Long parkingLotId,
                                          String passStatus, LocalDate validEndFrom, LocalDate validEndTo,
                                          int page, int size) {
        Long tenantId = TenantContext.requireTenantId();

        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId);

        if (plateNumber != null && !plateNumber.isEmpty()) {
            query.like("plate_number", plateNumber.toUpperCase());
        }
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        if (passStatus != null && !passStatus.isEmpty()) {
            query.eq("pass_status", passStatus);
        }
        if (validEndFrom != null) {
            query.ge("valid_end_date", validEndFrom);
        }
        if (validEndTo != null) {
            query.le("valid_end_date", validEndTo);
        }
        query.orderByDesc("created_at");

        IPage<MonthlyPass> passPage = monthlyPassMapper.selectPage(new Page<>(page, size), query);
        if (passPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(passPage.getRecords());

        List<MonthlyPassVO> voList = passPage.getRecords().stream()
                .map(p -> toVO(p, lotNames.get(p.getParkingLotId()), null))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(passPage.getCurrent(), passPage.getSize(), passPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 到期预警 ====================

    public IPage<MonthlyPassVO> expiringList(int page, int size) {
        Long tenantId = TenantContext.requireTenantId();

        // 从车场参数读取提醒天数（默认 7）
        String daysStr = paramResolver.getString(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, null);
        int days = 7;
        try {
            if (daysStr != null) days = Integer.parseInt(daysStr);
        } catch (NumberFormatException ignored) { }

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(days);

        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("pass_status", MonthlyPass.STATUS_ACTIVE)
                .le("valid_end_date", deadline)
                .ge("valid_end_date", today)
                .orderByAsc("valid_end_date");

        IPage<MonthlyPass> passPage = monthlyPassMapper.selectPage(new Page<>(page, size), query);
        if (passPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(passPage.getRecords());
        List<MonthlyPassVO> voList = passPage.getRecords().stream()
                .map(p -> toVO(p, lotNames.get(p.getParkingLotId()), null))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(passPage.getCurrent(), passPage.getSize(), passPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 审核列表 ====================

    /**
     * 分页查询待审核月卡（review_status = PENDING）。
     */
    public IPage<MonthlyPassVO> pageAuditPending(int page, int size, Long parkingLotId) {
        Long tenantId = TenantContext.requireTenantId();
        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("review_status", MonthlyPass.REVIEW_PENDING);
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        query.orderByDesc("created_at");

        IPage<MonthlyPass> passPage = monthlyPassMapper.selectPage(new Page<>(page, size), query);
        if (passPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(passPage.getRecords());
        List<MonthlyPassVO> voList = passPage.getRecords().stream()
                .map(p -> toVO(p, lotNames.get(p.getParkingLotId()), null))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(passPage.getCurrent(), passPage.getSize(), passPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 详情 ====================

    public MonthlyPassVO detail(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrow(id, tenantId);
        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    // ==================== 内部方法 ====================

    private MonthlyPass getOrThrow(Long id, Long tenantId) {
        MonthlyPass pass = monthlyPassMapper.selectById(id);
        if (pass == null || !tenantId.equals(pass.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        return pass;
    }

    private void checkDuplicateActive(Long tenantId, Long parkingLotId, String plate) {
        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("plate_number", plate)
                .eq("pass_status", MonthlyPass.STATUS_ACTIVE);
        Long count = monthlyPassMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车牌已在当前车场办理月卡，不能重复登记");
        }
    }

    /**
     * 缴费方式映射到订单 payChannel。
     */
    private String mapPayChannel(String payMethod) {
        return switch (payMethod) {
            case MonthlyPass.PAY_METHOD_CASH -> ParkingOrder.PAY_CHANNEL_CASH;
            default -> ParkingOrder.PAY_CHANNEL_BALANCE;
        };
    }

    private String getParkingLotName(Long parkingLotId) {
        if (parkingLotId == null) return null;
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        return lot != null ? lot.getName() : null;
    }

    private Map<Long, String> loadParkingLotNames(List<MonthlyPass> passes) {
        List<Long> lotIds = passes.stream()
                .map(MonthlyPass::getParkingLotId)
                .distinct()
                .collect(Collectors.toList());
        if (lotIds.isEmpty()) return Collections.emptyMap();
        List<ParkingLot> lots = parkingLotMapper.selectBatchIds(lotIds);
        return lots.stream().collect(Collectors.toMap(ParkingLot::getId, ParkingLot::getName));
    }

    /**
     * 公开的单参 toVO（供审核 Controller 等外部调用）。
     */
    public MonthlyPassVO toVO(MonthlyPass pass) {
        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    private MonthlyPassVO toVO(MonthlyPass pass, String parkingLotName, Long orderId) {
        MonthlyPassVO vo = new MonthlyPassVO();
        vo.setId(pass.getId());
        vo.setPlateNumber(pass.getPlateNumber());
        vo.setPlateColor(pass.getPlateColor());
        vo.setVehicleType(pass.getVehicleType());
        vo.setParkingLotId(pass.getParkingLotId());
        vo.setParkingLotName(parkingLotName);
        vo.setValidStartDate(pass.getValidStartDate());
        vo.setValidEndDate(pass.getValidEndDate());
        vo.setAmountCents(pass.getAmountCents());
        vo.setPaidAmountCents(pass.getPaidAmountCents());
        vo.setPayMethod(pass.getPayMethod());
        vo.setPassStatus(pass.getPassStatus());
        vo.setReviewStatus(pass.getReviewStatus());
        vo.setReviewRemark(pass.getReviewRemark());
        vo.setSource(pass.getSource());
        vo.setApplicantId(pass.getApplicantId());
        vo.setOrderId(orderId);
        vo.setOwnerName(pass.getOwnerName());
        vo.setOwnerPhone(pass.getOwnerPhone());
        vo.setRemark(pass.getRemark());
        vo.setCreatedAt(pass.getCreatedAt());
        return vo;
    }
}
