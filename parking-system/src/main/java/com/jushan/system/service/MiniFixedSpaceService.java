package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.dto.MiniFixedSpaceApplyRequest;
import com.jushan.system.dto.MiniFixedSpaceRenewRequest;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.jushan.system.mapper.VehicleRenewalLogMapper;
import com.jushan.system.vo.FixedSpaceVO;
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
 * 小程序端固定车位生命周期服务。
 * <p>
 * 覆盖小程序端固定车位申请、列表、详情、支付、续期全流程，
 * 与运营端 {@link FixedSpaceService} 共享 fixed_space_binding 实体和 toVO 转换。
 * 对标 {@link MiniMonthlyPassService}。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class MiniFixedSpaceService {

    private static final Logger log = LoggerFactory.getLogger(MiniFixedSpaceService.class);

    /** 固定车位默认单价（分/月），当系统参数未配置时使用 */
    private static final int DEFAULT_PRICE_PER_MONTH_CENTS = 30000;

    private final FixedSpaceBindingMapper bindingMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final VehicleMapper vehicleMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final VehicleRenewalLogMapper renewalLogMapper;
    private final FixedSpaceService fixedSpaceService;
    private final ParamResolver paramResolver;

    public MiniFixedSpaceService(FixedSpaceBindingMapper bindingMapper,
                                  PlateBindingMapper plateBindingMapper,
                                  VehicleMapper vehicleMapper,
                                  ParkingOrderMapper parkingOrderMapper,
                                  VehicleRenewalLogMapper renewalLogMapper,
                                  FixedSpaceService fixedSpaceService,
                                  ParamResolver paramResolver) {
        this.bindingMapper = bindingMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.vehicleMapper = vehicleMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.fixedSpaceService = fixedSpaceService;
        this.paramResolver = paramResolver;
    }

    // ==================== 申请固定车位 ====================

    /**
     * 小程序用户申请固定车位。
     * <ol>
     *   <li>校验车牌是否属于当前用户已审核通过的绑定车牌</li>
     *   <li>同车辆、同车场已有生效中固定车位 → 拒绝</li>
     *   <li>车位号已被占用 → 拒绝</li>
     *   <li>根据车场审核模式决定自动通过或待审核</li>
     *   <li>创建绑定记录（status 暂不设 ACTIVE，等待支付）</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO apply(MiniFixedSpaceApplyRequest request) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 1. 校验车牌是否属于当前用户已审核通过的绑定车牌
        Long vehicleId = validatePlateBindingAndGetVehicleId(userId, plate, tenantId);

        // 2. 同车辆、同车场的生效中固定车位不允许重复申请
        checkDuplicateActive(tenantId, request.getParkingLotId(), vehicleId);

        // 3. 车位号未被占用
        checkSpaceNotOccupied(tenantId, request.getParkingLotId(), request.getSpaceNo());

        // 4. 读取审核模式
        String reviewMode = paramResolver.getString(ParamKeys.FIXED_SPACE_REVIEW_MODE,
                request.getParkingLotId());
        boolean autoApprove = !"MANUAL".equals(reviewMode);

        // 5. 创建绑定记录
        FixedSpaceBinding binding = new FixedSpaceBinding();
        binding.setTenantId(tenantId);
        binding.setParkingLotId(request.getParkingLotId());
        binding.setZoneId(request.getZoneId());
        binding.setSpaceNo(request.getSpaceNo());
        binding.setVehicleId(vehicleId);
        binding.setSource(FixedSpaceBinding.SOURCE_MINIAPP);
        binding.setApplicantId(userId);
        binding.setReviewStatus(autoApprove ? FixedSpaceBinding.REVIEW_APPROVED : FixedSpaceBinding.REVIEW_PENDING);
        // status 暂不设 ACTIVE，支付后设置
        binding.setCreatedAt(LocalDateTime.now());
        binding.setUpdatedAt(LocalDateTime.now());
        bindingMapper.insert(binding);

        log.info("小程序固定车位申请成功: bindingId={} spaceNo={} plate={} lotId={} autoApprove={} userId={}",
                binding.getId(), request.getSpaceNo(), plate, request.getParkingLotId(), autoApprove, userId);

        return fixedSpaceService.toVO(binding);
    }

    // ==================== 我的固定车位列表 ====================

    /**
     * 查询当前小程序用户的所有固定车位绑定记录。
     */
    public List<FixedSpaceVO> listMyBindings() {
        Long userId = TenantContext.requireUserId();

        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("applicant_id", userId)
                .eq("source", FixedSpaceBinding.SOURCE_MINIAPP)
                .orderByDesc("created_at");

        List<FixedSpaceBinding> bindings = bindingMapper.selectList(query);
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }

        return bindings.stream()
                .map(fixedSpaceService::toVO)
                .collect(Collectors.toList());
    }

    // ==================== 固定车位详情 ====================

    /**
     * 查询固定车位详情（仅限本人）。
     */
    public FixedSpaceVO detail(Long id) {
        Long userId = TenantContext.requireUserId();
        FixedSpaceBinding binding = getOrThrowForUser(id, userId);
        return fixedSpaceService.toVO(binding);
    }

    // ==================== 支付 ====================

    /**
     * 小程序用户支付固定车位费用，激活绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO pay(Long id) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        FixedSpaceBinding binding = getOrThrowForUser(id, userId);

        // 1. 校验审核通过
        if (!FixedSpaceBinding.REVIEW_APPROVED.equals(binding.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "固定车位未通过审核，无法支付");
        }

        // 2. 校验状态，防止重复支付
        if (FixedSpaceBinding.STATUS_ACTIVE == (binding.getStatus() != null ? binding.getStatus() : 0)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "固定车位已生效，无需重复支付");
        }

        // 3. 读取固定车位单价（分/月）
        int priceCents = paramResolver.getInt(ParamKeys.FIXED_SPACE_PRICE_PER_MONTH_CENTS,
                binding.getParkingLotId(), DEFAULT_PRICE_PER_MONTH_CENTS);

        // 4. 获取车牌号
        String plateNumber = getPlateNumber(binding.getVehicleId());

        // 5. 创建已支付固定车位订单
        LocalDateTime now = LocalDateTime.now();
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(binding.getParkingLotId());
        order.setRefId(binding.getId());
        order.setPlateNumber(plateNumber);
        order.setOrderType(ParkingOrder.ORDER_TYPE_FIXED_SPACE);
        order.setAmountCents(priceCents);
        order.setPaidAmount(priceCents);
        order.setPayableAmount(priceCents);
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_BALANCE);
        order.setPayTime(now);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        parkingOrderMapper.insert(order);

        // 6. 更新绑定为生效状态
        LocalDate today = LocalDate.now();
        binding.setStatus(FixedSpaceBinding.STATUS_ACTIVE);
        binding.setValidStart(today);
        binding.setValidEnd(today.plusMonths(1));
        binding.setPaidAmountCents(priceCents);
        binding.setPayMethod(FixedSpaceBinding.PAY_METHOD_SIMULATED_PAY);
        binding.setUpdatedAt(now);
        bindingMapper.updateById(binding);

        log.info("小程序固定车位支付成功: bindingId={} spaceNo={} plate={} price={}分 orderId={} userId={}",
                binding.getId(), binding.getSpaceNo(), plateNumber, priceCents, order.getId(), userId);

        return fixedSpaceService.toVO(binding);
    }

    // ==================== 续期 ====================

    /**
     * 小程序用户续期固定车位。
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO renew(Long id, MiniFixedSpaceRenewRequest request) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        FixedSpaceBinding binding = getOrThrowForUser(id, userId);

        // 1. 校验固定车位已生效
        if (!Integer.valueOf(FixedSpaceBinding.STATUS_ACTIVE).equals(binding.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅生效中的固定车位可续期");
        }

        // 2. 校验来源为小程序
        if (!FixedSpaceBinding.SOURCE_MINIAPP.equals(binding.getSource())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "仅小程序办理的固定车位支持在线续期");
        }

        // 3. 读取单价，按续费月数计算总价
        int pricePerMonth = paramResolver.getInt(ParamKeys.FIXED_SPACE_PRICE_PER_MONTH_CENTS,
                binding.getParkingLotId(), DEFAULT_PRICE_PER_MONTH_CENTS);
        int totalCents = pricePerMonth * request.getMonths();

        // 4. 延长有效期
        LocalDate oldValidEnd = binding.getValidEnd();
        LocalDate newValidEnd = (oldValidEnd != null ? oldValidEnd : LocalDate.now())
                .plusMonths(request.getMonths());

        binding.setValidEnd(newValidEnd);
        binding.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(binding);

        // 5. 获取车牌号
        String plateNumber = getPlateNumber(binding.getVehicleId());

        // 6. 创建续费订单
        LocalDateTime now = LocalDateTime.now();
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(binding.getParkingLotId());
        order.setRefId(binding.getId());
        order.setPlateNumber(plateNumber);
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

        // 7. 写入续费日志
        VehicleRenewalLog renewalLog = new VehicleRenewalLog();
        renewalLog.setTenantId(tenantId);
        renewalLog.setParkingLotId(binding.getParkingLotId());
        renewalLog.setMonthlyPassId(binding.getId());
        renewalLog.setVehicleId(binding.getVehicleId());
        renewalLog.setPlateNumber(plateNumber);
        renewalLog.setOrderId(order.getId());
        renewalLog.setRenewalMonths(request.getMonths());
        renewalLog.setAmountCents(totalCents);
        renewalLog.setOldValidEnd(oldValidEnd);
        renewalLog.setNewValidEnd(newValidEnd);
        renewalLog.setOperatorId(userId);
        renewalLog.setCreatedAt(now);
        renewalLog.setUpdatedAt(now);
        renewalLogMapper.insert(renewalLog);

        log.info("小程序固定车位续期成功: bindingId={} spaceNo={} plate={} months={} oldEnd={} newEnd={} totalCents={} userId={}",
                binding.getId(), binding.getSpaceNo(), plateNumber, request.getMonths(),
                oldValidEnd, newValidEnd, totalCents, userId);

        return fixedSpaceService.toVO(binding);
    }

    // ==================== 可用车位查询 ====================

    /**
     * 查询指定车场中未被占用的固定车位列表。
     * <p>
     * 逻辑：从 fixed_space_binding 中查出所有 status=ACTIVE 的已占用 spaceNo，
     * 然后从 parking_zone 和 parking_space_policy 表获取该车场全部车位信息，减去已占用部分。
     * <p>
     * 简化实现：直接返回该车场下所有未被 ACTIVE 绑定占用的车位信息。
     *
     * @param parkingLotId 车场 ID
     * @return 可用车位列表
     */
    public List<java.util.Map<String, Object>> getAvailableSpaces(Long parkingLotId) {
        Long tenantId = TenantContext.requireTenantId();

        // 1. 查询当前车场所有已被 ACTIVE 固定车位占用的 spaceNo
        List<FixedSpaceBinding> occupiedBindings = bindingMapper.selectList(
                new QueryWrapper<FixedSpaceBinding>()
                        .eq("tenant_id", tenantId)
                        .eq("parking_lot_id", parkingLotId)
                        .eq("status", FixedSpaceBinding.STATUS_ACTIVE)
                        .isNull("deleted_at"));

        java.util.Set<String> occupiedSpaceNos = occupiedBindings.stream()
                .map(FixedSpaceBinding::getSpaceNo)
                .collect(Collectors.toSet());

        // 2. 查询该车场所有生效中的绑定记录（用于返回所有定义过的车位）
        // 注意：车位可能没有在任何 binding 中出现，此时视为"未定义"。
        // 此处简化：返回固定车位绑定中所有出现过的空间（去重），标记是否被占用。
        List<FixedSpaceBinding> allBindings = bindingMapper.selectList(
                new QueryWrapper<FixedSpaceBinding>()
                        .eq("tenant_id", tenantId)
                        .eq("parking_lot_id", parkingLotId)
                        .isNull("deleted_at"));

        java.util.Map<String, FixedSpaceBinding> latestBySpaceNo = new java.util.LinkedHashMap<>();
        for (FixedSpaceBinding b : allBindings) {
            if (b.getSpaceNo() != null) {
                latestBySpaceNo.putIfAbsent(b.getSpaceNo(), b);
            }
        }

        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, FixedSpaceBinding> entry : latestBySpaceNo.entrySet()) {
            String spaceNo = entry.getKey();
            FixedSpaceBinding ref = entry.getValue();
            boolean occupied = occupiedSpaceNos.contains(spaceNo);

            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("spaceNo", spaceNo);
            item.put("zoneId", ref.getZoneId());
            item.put("zoneName", null); // 可由前端使用 parking_zone 查询补全或后续增强
            item.put("occupied", occupied);
            result.add(item);
        }

        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 按主键和申请人查找固定车位绑定，校验归属。
     *
     * @param id     绑定记录主键
     * @param userId 小程序用户 ID
     * @return 绑定实体（不为 null）
     * @throws BusinessException NOT_FOUND — 不存在或已删除
     * @throws BusinessException FORBIDDEN — 不是当前用户的绑定
     */
    private FixedSpaceBinding getOrThrowForUser(Long id, Long userId) {
        FixedSpaceBinding binding = bindingMapper.selectById(id);
        if (binding == null || binding.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "固定车位绑定不存在");
        }
        if (!userId.equals(binding.getApplicantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该固定车位绑定");
        }
        return binding;
    }

    /**
     * 校验车牌是否属于当前用户已审核通过的绑定车牌，并返回车辆 ID。
     * <p>
     * 查询链路：plate_binding(wx_user_id, verify_status=APPROVED) → vehicle(vehiclePlate)。
     *
     * @param userId   小程序用户 ID
     * @param plate    标准化大写车牌
     * @param tenantId 租户 ID
     * @return 车辆 ID
     * @throws BusinessException FORBIDDEN — 车牌未绑定或未通过审核
     */
    private Long validatePlateBindingAndGetVehicleId(Long userId, String plate, Long tenantId) {
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

        return vehicles.get(0).getId();
    }

    /**
     * 同车辆、同车场的生效中固定车位不允许重复申请。
     */
    private void checkDuplicateActive(Long tenantId, Long parkingLotId, Long vehicleId) {
        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("vehicle_id", vehicleId)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE);
        Long count = bindingMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "该车辆已在当前车场绑定固定车位，不能重复申请");
        }
    }

    /**
     * 校验车位号未被其他生效中固定车位占用。
     */
    private void checkSpaceNotOccupied(Long tenantId, Long parkingLotId, String spaceNo) {
        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("space_no", spaceNo)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE)
                .isNull("deleted_at");
        Long count = bindingMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "该车位号已被占用（车位号=" + spaceNo + "）");
        }
    }

    /**
     * 根据车辆 ID 查询车牌号。
     */
    private String getPlateNumber(Long vehicleId) {
        if (vehicleId == null) return null;
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        return vehicle != null ? vehicle.getVehiclePlate() : null;
    }
}
