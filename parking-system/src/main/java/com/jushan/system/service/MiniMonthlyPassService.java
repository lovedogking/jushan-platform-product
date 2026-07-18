package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.dto.MiniMonthlyPassApplyRequest;
import com.jushan.system.dto.MiniMonthlyPassRenewRequest;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.jushan.system.mapper.VehicleRenewalLogMapper;
import com.jushan.system.vo.MonthlyPassVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序端月卡生命周期服务（Phase 2）。
 * <p>
 * 覆盖小程序端月卡申请、列表、详情、支付、续期全流程，
 * 与运营端 {@link MonthlyPassService} 共享 monthly_pass 实体和 toVO 转换。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class MiniMonthlyPassService {

    private static final Logger log = LoggerFactory.getLogger(MiniMonthlyPassService.class);

    /** 月卡默认单价（分/月），当系统参数未配置时使用 */
    private static final int DEFAULT_PRICE_PER_MONTH_CENTS = 30000;

    /** 申请后的月卡暂挂状态（等待支付） */
    private static final String PASS_STATUS_PENDING = "PENDING";

    private final MonthlyPassMapper monthlyPassMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final VehicleMapper vehicleMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final VehicleRenewalLogMapper renewalLogMapper;
    private final MonthlyPassService monthlyPassService;
    private final ParamResolver paramResolver;

    public MiniMonthlyPassService(MonthlyPassMapper monthlyPassMapper,
                                   PlateBindingMapper plateBindingMapper,
                                   VehicleMapper vehicleMapper,
                                   ParkingOrderMapper parkingOrderMapper,
                                   VehicleRenewalLogMapper renewalLogMapper,
                                   MonthlyPassService monthlyPassService,
                                   ParamResolver paramResolver) {
        this.monthlyPassMapper = monthlyPassMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.vehicleMapper = vehicleMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.monthlyPassService = monthlyPassService;
        this.paramResolver = paramResolver;
    }

    // ==================== 申请月卡 ====================

    /**
     * 小程序用户申请月卡。
     * <p>
     * 校验车牌是否属于当前用户已审核通过的绑定车牌；
     * 根据车场审核模式决定自动通过或待审核。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO apply(MiniMonthlyPassApplyRequest request) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 1. 校验车牌是否属于当前用户已审核通过的绑定车牌
        validatePlateBinding(userId, plate);

        // 2. 同车牌、同车场的生效中月卡不允许重复申请
        checkDuplicateActive(tenantId, request.getParkingLotId(), plate);

        // 3. 读取审核模式
        String reviewMode = paramResolver.getString(ParamKeys.MONTHLY_FIXED_REVIEW_MODE,
                request.getParkingLotId());
        boolean autoApprove = !"MANUAL".equals(reviewMode);

        // 4. 创建月卡记录
        MonthlyPass pass = new MonthlyPass();
        pass.setTenantId(tenantId);
        pass.setParkingLotId(request.getParkingLotId());
        pass.setPlateNumber(plate);
        pass.setSource(MonthlyPass.SOURCE_MINIAPP);
        pass.setApplicantId(userId);
        pass.setReviewStatus(autoApprove ? MonthlyPass.REVIEW_APPROVED : MonthlyPass.REVIEW_PENDING);
        pass.setPassStatus(PASS_STATUS_PENDING);
        pass.setOwnerName(request.getOwnerName());
        pass.setOwnerPhone(request.getOwnerPhone());
        pass.setCreatedAt(LocalDateTime.now());
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.insert(pass);

        log.info("小程序月卡申请成功: passId={} plate={} lotId={} autoApprove={} userId={}",
                pass.getId(), plate, request.getParkingLotId(), autoApprove, userId);

        return monthlyPassService.toVO(pass);
    }

    // ==================== 我的月卡列表 ====================

    /**
     * 查询当前小程序用户的所有月卡记录。
     */
    public List<MonthlyPassVO> listMyPasses() {
        Long userId = TenantContext.requireUserId();

        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("applicant_id", userId)
                .eq("source", MonthlyPass.SOURCE_MINIAPP)
                .orderByDesc("created_at");

        List<MonthlyPass> passes = monthlyPassMapper.selectList(query);
        if (passes.isEmpty()) {
            return Collections.emptyList();
        }

        return passes.stream()
                .map(monthlyPassService::toVO)
                .collect(Collectors.toList());
    }

    // ==================== 月卡详情 ====================

    /**
     * 查询月卡详情（仅限本人）。
     */
    public MonthlyPassVO detail(Long id) {
        Long userId = TenantContext.requireUserId();
        MonthlyPass pass = getOrThrowForUser(id, userId);
        return monthlyPassService.toVO(pass);
    }

    // ==================== 支付 ====================

    /**
     * 小程序用户支付月卡费用，激活月卡。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO pay(Long id) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrowForUser(id, userId);

        // 1. 校验审核通过
        if (!MonthlyPass.REVIEW_APPROVED.equals(pass.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "月卡未通过审核，无法支付");
        }

        // 2. 校验状态，防止重复支付
        if (MonthlyPass.STATUS_ACTIVE.equals(pass.getPassStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "月卡已生效，无需重复支付");
        }

        // 3. 读取月卡单价（分/月）
        int priceCents = paramResolver.getInt(ParamKeys.MONTHLY_PASS_PRICE_PER_MONTH_CENTS,
                pass.getParkingLotId(), DEFAULT_PRICE_PER_MONTH_CENTS);

        // 4. 创建已支付月卡订单
        LocalDateTime now = LocalDateTime.now();
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(pass.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(pass.getPlateNumber());
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTHLY_PASS);
        order.setAmountCents(priceCents);
        order.setPaidAmount(priceCents);
        order.setPayableAmount(priceCents);
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_BALANCE);
        order.setPayTime(now);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        parkingOrderMapper.insert(order);

        // 5. 更新月卡为生效状态
        LocalDate today = LocalDate.now();
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setValidStartDate(today);
        pass.setValidEndDate(today.plusMonths(1));
        pass.setPaidAmountCents(priceCents);
        pass.setAmountCents(priceCents);
        pass.setPayMethod(MonthlyPass.PAY_METHOD_SIMULATED_PAY);
        pass.setUpdatedAt(now);
        monthlyPassMapper.updateById(pass);

        log.info("小程序月卡支付成功: passId={} plate={} price={}分 orderId={} userId={}",
                pass.getId(), pass.getPlateNumber(), priceCents, order.getId(), userId);

        MonthlyPassVO vo = monthlyPassService.toVO(pass);
        vo.setOrderId(order.getId());
        return vo;
    }

    // ==================== 续期 ====================

    /**
     * 小程序用户续期月卡。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO renew(Long id, MiniMonthlyPassRenewRequest request) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrowForUser(id, userId);

        // 1. 校验月卡已生效
        if (!MonthlyPass.STATUS_ACTIVE.equals(pass.getPassStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅生效中的月卡可续期");
        }

        // 2. 校验来源为小程序
        if (!MonthlyPass.SOURCE_MINIAPP.equals(pass.getSource())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "仅小程序办理的月卡支持在线续期");
        }

        // 3. 读取月卡单价，按续费月数计算总价
        int pricePerMonth = paramResolver.getInt(ParamKeys.MONTHLY_PASS_PRICE_PER_MONTH_CENTS,
                pass.getParkingLotId(), DEFAULT_PRICE_PER_MONTH_CENTS);
        int totalCents = pricePerMonth * request.getMonths();

        // 4. 延长有效期
        LocalDate oldValidEnd = pass.getValidEndDate();
        LocalDate newValidEnd = (oldValidEnd != null ? oldValidEnd : LocalDate.now())
                .plusMonths(request.getMonths());

        pass.setValidEndDate(newValidEnd);
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        // 5. 创建续费订单
        LocalDateTime now = LocalDateTime.now();
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(pass.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(pass.getPlateNumber());
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        order.setRenewalMonths(request.getMonths());
        order.setAmountCents(totalCents);
        order.setPaidAmount(totalCents);
        order.setPayableAmount(totalCents);
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_BALANCE);
        order.setPayTime(now);
        order.setOperatorId(userId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        parkingOrderMapper.insert(order);

        // 6. 写入续费日志
        VehicleRenewalLog renewalLog = new VehicleRenewalLog();
        renewalLog.setTenantId(tenantId);
        renewalLog.setParkingLotId(pass.getParkingLotId());
        renewalLog.setMonthlyPassId(pass.getId());
        renewalLog.setVehicleId(null);
        renewalLog.setPlateNumber(pass.getPlateNumber());
        renewalLog.setOrderId(order.getId());
        renewalLog.setRenewalMonths(request.getMonths());
        renewalLog.setAmountCents(totalCents);
        renewalLog.setOldValidEnd(oldValidEnd);
        renewalLog.setNewValidEnd(newValidEnd);
        renewalLog.setOperatorId(userId);
        renewalLog.setCreatedAt(now);
        renewalLog.setUpdatedAt(now);
        renewalLogMapper.insert(renewalLog);

        log.info("小程序月卡续期成功: passId={} plate={} months={} oldEnd={} newEnd={} totalCents={} userId={}",
                pass.getId(), pass.getPlateNumber(), request.getMonths(),
                oldValidEnd, newValidEnd, totalCents, userId);

        MonthlyPassVO vo = monthlyPassService.toVO(pass);
        vo.setOrderId(order.getId());
        return vo;
    }

    // ==================== 内部方法 ====================

    /**
     * 按主键和申请人查找月卡，校验归属。
     *
     * @param id     月卡主键
     * @param userId 小程序用户 ID
     * @return 月卡实体（不为 null）
     * @throws BusinessException NOT_FOUND — 不存在或已删除
     * @throws BusinessException FORBIDDEN — 不是当前用户的月卡
     */
    private MonthlyPass getOrThrowForUser(Long id, Long userId) {
        MonthlyPass pass = monthlyPassMapper.selectById(id);
        if (pass == null || pass.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        if (!userId.equals(pass.getApplicantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该月卡");
        }
        return pass;
    }

    /**
     * 校验车牌是否属于当前用户已审核通过的绑定车牌。
     * <p>
     * 查询链路：plate_binding(wx_user_id, verify_status=APPROVED) → vehicle(vehiclePlate)。
     * 注意 PlateBinding 和 Vehicle 均未使用 @TableLogic，需手动过滤 deleted_at。
     *
     * @param userId 小程序用户 ID
     * @param plate  标准化大写车牌
     * @throws BusinessException FORBIDDEN — 车牌未绑定或未通过审核
     */
    private void validatePlateBinding(Long userId, String plate) {
        List<PlateBinding> bindings = plateBindingMapper.selectList(
                new QueryWrapper<PlateBinding>()
                        .eq("wx_user_id", userId)
                        .eq("verify_status", PlateBinding.VERIFY_STATUS_APPROVED)
                        .isNull("deleted_at"));

        if (bindings.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "该车牌未绑定，请先绑定车牌");
        }

        List<Long> vehicleIds = bindings.stream()
                .map(PlateBinding::getVehicleId)
                .distinct()
                .collect(Collectors.toList());

        List<Vehicle> vehicles = vehicleMapper.selectList(
                new QueryWrapper<Vehicle>()
                        .in("id", vehicleIds)
                        .eq("vehicle_plate", plate)
                        .isNull("deleted_at"));

        if (vehicles.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "该车牌未绑定，请先绑定车牌");
        }
    }

    /**
     * 同车牌、同车场的生效中月卡不允许重复申请。
     */
    private void checkDuplicateActive(Long tenantId, Long parkingLotId, String plate) {
        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("plate_number", plate)
                .eq("pass_status", MonthlyPass.STATUS_ACTIVE);
        Long count = monthlyPassMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "该车牌已在当前车场办理月卡，不能重复申请");
        }
    }
}
