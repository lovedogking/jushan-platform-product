package com.jushan.platform.modules.miniapp.service;
import com.jushan.platform.modules.common.service.ParamResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.entity.ParkingSpacePolicy;
import com.jushan.platform.modules.parking.entity.ParkingZone;
import com.jushan.platform.modules.parking.mapper.ParkingSpacePolicyMapper;
import com.jushan.platform.modules.parking.mapper.ParkingZoneMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.common.constant.ParamKeys;
import com.jushan.platform.modules.miniapp.dto.FixedSpaceCreateRequest;
import com.jushan.platform.modules.miniapp.dto.FixedSpaceRenewRequest;
import com.jushan.platform.modules.miniapp.entity.FixedSpaceBinding;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.miniapp.mapper.FixedSpaceBindingMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.miniapp.vo.FixedSpaceVO;
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
 * 固定车位绑定管理服务（Phase 1 A2）。
 * <p>
 * 管理「车位号 ↔ 车辆」的专属绑定关系。
 * 非绑定车辆占用该车位时按临停计费。
 * <p>
 * 绑定车辆入场时自动放行（不计费），出场时不生成临停订单。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class FixedSpaceService {

    private static final Logger log = LoggerFactory.getLogger(FixedSpaceService.class);

    /** 到期预警默认提前天数 */
    public static final int DEFAULT_EXPIRING_DAYS = 7;

    private final FixedSpaceBindingMapper bindingMapper;
    private final SysVehicleMapper vehicleMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingZoneMapper zoneMapper;
    private final ParamResolver paramResolver;
    private final ParkingSpacePolicyMapper spacePolicyMapper;
    private final ParkingOrderMapper parkingOrderMapper;

    public FixedSpaceService(FixedSpaceBindingMapper bindingMapper,
                              SysVehicleMapper vehicleMapper,
                              ParkingLotMapper parkingLotMapper,
                              ParkingZoneMapper zoneMapper,
                              ParamResolver paramResolver,
                              ParkingSpacePolicyMapper spacePolicyMapper,
                              ParkingOrderMapper parkingOrderMapper) {
        this.bindingMapper = bindingMapper;
        this.vehicleMapper = vehicleMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.zoneMapper = zoneMapper;
        this.paramResolver = paramResolver;
        this.spacePolicyMapper = spacePolicyMapper;
        this.parkingOrderMapper = parkingOrderMapper;
    }

    // ==================== 列表查询 ====================

    /**
     * 分页查询固定车位绑定列表。
     *
     * @param parkingLotId 车场ID（可选）
     * @param zoneId       区域ID（可选）
     * @param spaceNo      车位号（可选，模糊匹配）
     * @param plateNumber  车牌号（可选，模糊匹配）
     * @param status       状态（可选）
     * @param page         页码
     * @param size         每页大小
     * @return 分页固定车位列表
     */
    public IPage<FixedSpaceVO> pageList(Long parkingLotId, Long zoneId, String spaceNo,
                                         String plateNumber, Integer status,
                                         int page, int size) {
        Long tenantId = TenantContext.requireTenantId();

        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId);

        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        if (zoneId != null) {
            query.eq("zone_id", zoneId);
        }
        if (spaceNo != null && !spaceNo.isEmpty()) {
            query.like("space_no", spaceNo);
        }
        if (status != null) {
            query.eq("status", status);
        }
        if (plateNumber != null && !plateNumber.isEmpty()) {
            // 通过子查询过滤：先找到匹配车牌号的车辆ID
            QueryWrapper<SysVehicle> vehicleSubQuery = new QueryWrapper<SysVehicle>()
                    .select("id")
                    .eq("tenant_id", tenantId)
                    .like("plate_number", plateNumber.toUpperCase());
            List<Object> vehicleIds = vehicleMapper.selectObjs(vehicleSubQuery);
            if (vehicleIds.isEmpty()) {
                // 没有匹配的车辆，返回空
                return new Page<>(page, size);
            }
            query.in("vehicle_id", vehicleIds);
        }

        query.orderByDesc("created_at");

        IPage<FixedSpaceBinding> bindingPage = bindingMapper.selectPage(new Page<>(page, size), query);
        if (bindingPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        // 批量补全关联信息
        List<FixedSpaceVO> voList = enrichVOs(bindingPage.getRecords());

        IPage<FixedSpaceVO> result = new Page<>(bindingPage.getCurrent(), bindingPage.getSize(), bindingPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 到期预警列表。
     * <p>
     * 查询 {@code status=1}（生效中）且 {@code validEnd} 在 N 天内到期的绑定记录，
     * 按到期时间升序排列。
     *
     * @param days 提前天数（默认 7 天）
     * @param page 页码
     * @param size 每页大小
     * @return 分页到期预警列表
     */
    public IPage<FixedSpaceVO> expiringList(Integer days, int page, int size) {
        Long tenantId = TenantContext.requireTenantId();
        int expiringDays = (days != null && days > 0) ? days : DEFAULT_EXPIRING_DAYS;

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(expiringDays);

        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE)
                .le("valid_end", deadline)
                .ge("valid_end", today)
                .orderByAsc("valid_end");

        IPage<FixedSpaceBinding> bindingPage = bindingMapper.selectPage(new Page<>(page, size), query);
        if (bindingPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        List<FixedSpaceVO> voList = enrichVOs(bindingPage.getRecords());
        IPage<FixedSpaceVO> result = new Page<>(bindingPage.getCurrent(), bindingPage.getSize(), bindingPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 审核列表 ====================

    /**
     * 分页查询待审核固定车位绑定（review_status = PENDING）。
     */
    public IPage<FixedSpaceVO> pageAuditPending(int page, int size, Long parkingLotId) {
        Long tenantId = TenantContext.requireTenantId();
        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("review_status", FixedSpaceBinding.REVIEW_PENDING);
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        query.orderByDesc("created_at");

        IPage<FixedSpaceBinding> entityPage = bindingMapper.selectPage(new Page<>(page, size), query);
        if (entityPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(entityPage.getRecords());
        List<FixedSpaceVO> voList = entityPage.getRecords().stream()
                .map(e -> toVO(e, lotNames.get(e.getParkingLotId())))
                .collect(Collectors.toList());

        IPage<FixedSpaceVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 绑定 ====================

    /**
     * 绑定固定车位。
     * <p>
     * 校验同一车场同一车位号不能重复绑定生效中车辆，
     * 同一车辆在同一车场不能重复绑定生效中固定车位。
     * 自动查找或创建 sys_vehicle 记录。
     *
     * @param request 绑定请求
     * @return 绑定记录视图
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO create(FixedSpaceCreateRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 1. 查找或创建车辆记录
        SysVehicle vehicle = vehicleMapper.selectByPlateNumber(plate, tenantId);
        if (vehicle == null) {
            vehicle = new SysVehicle();
            vehicle.setTenantId(tenantId);
            vehicle.setParkingLotId(request.getParkingLotId());
            vehicle.setPlateNumber(plate);
            // 不设置 vehicleType，VehicleTypeDecisionService 按 TEMP 处理。
            // 固定车位免计费仅在 MQ 路径（EntryService/ExitService）中通过
            // hasActiveBinding 检查生效，booth 路径后续可通过 VehicleTypeDecisionService
            // 扩展 TYPE_FIXED_SPACE 支持。
            vehicle.setStatus(SysVehicle.STATUS_ACTIVE);
            vehicle.setCreatedAt(LocalDateTime.now());
            vehicle.setUpdatedAt(LocalDateTime.now());
            vehicleMapper.insert(vehicle);
            log.info("固定车位绑定 — 自动创建车辆记录: vehicleId={} plate={}", vehicle.getId(), plate);
        }

        final Long vehicleId = vehicle.getId();

        // 2. 校验：同一车场同一车位号不能重复绑定
        checkDuplicateSpace(tenantId, request.getParkingLotId(), request.getZoneId(), request.getSpaceNo(), null);

        // 3. 校验：同一车辆在同一车场不能重复绑定固定车位
        checkDuplicateVehicle(tenantId, request.getParkingLotId(), vehicleId, null);

        // 4. 配额检查：按区域限制固定车位数
        if (request.getZoneId() != null) {
            checkZoneQuota(tenantId, request.getParkingLotId(), request.getZoneId());
        }

        // 5. 解析审核模式
        String reviewMode = paramResolver.getString(ParamKeys.FIXED_SPACE_REVIEW_MODE,
                request.getParkingLotId());
        String reviewStatus = "AUTO".equalsIgnoreCase(reviewMode)
                ? FixedSpaceBinding.REVIEW_APPROVED : FixedSpaceBinding.REVIEW_PENDING;
        log.info("固定车位绑定 — 审核模式: reviewMode={} reviewStatus={} lotId={}",
                reviewMode, reviewStatus, request.getParkingLotId());

        // 6. 确定来源与申请人
        String source = request.getSource() != null ? request.getSource() : FixedSpaceBinding.SOURCE_ADMIN;
        Long applicantId = FixedSpaceBinding.SOURCE_MINIAPP.equals(source) ? TenantContext.requireUserId() : null;

        // 7. 创建绑定记录
        FixedSpaceBinding binding = new FixedSpaceBinding();
        binding.setTenantId(tenantId);
        binding.setParkingLotId(request.getParkingLotId());
        binding.setZoneId(request.getZoneId());
        binding.setSpaceNo(request.getSpaceNo());
        binding.setVehicleId(vehicleId);
        binding.setValidStart(request.getValidStart());
        binding.setValidEnd(request.getValidEnd());
        binding.setStatus(FixedSpaceBinding.STATUS_ACTIVE);
        binding.setPayMethod(request.getPayMethod());
        binding.setPaidAmountCents(request.getPaidAmountCents() != null ? request.getPaidAmountCents() : 0);
        binding.setReviewStatus(reviewStatus);
        binding.setSource(source);
        binding.setApplicantId(applicantId);
        binding.setRemark(request.getRemark());
        binding.setCreatedAt(LocalDateTime.now());
        binding.setUpdatedAt(LocalDateTime.now());
        bindingMapper.insert(binding);

        log.info("固定车位绑定成功: bindingId={} spaceNo={} plate={} parkingLotId={} validEnd={} reviewStatus={}",
                binding.getId(), request.getSpaceNo(), plate, request.getParkingLotId(),
                request.getValidEnd(), reviewStatus);

        // 8. 审核通过时直接生成已支付订单
        Long orderId = null;
        if (FixedSpaceBinding.REVIEW_APPROVED.equals(reviewStatus)) {
            ParkingOrder order = new ParkingOrder();
            order.setTenantId(tenantId);
            order.setParkingLotId(request.getParkingLotId());
            order.setRefId(binding.getId());
            order.setPlateNumber(plate);
            order.setOrderType(ParkingOrder.ORDER_TYPE_FIXED_SPACE);
            order.setAmountCents(binding.getPaidAmountCents());
            order.setPaidAmount(binding.getPaidAmountCents());
            order.setPayableAmount(binding.getPaidAmountCents());
            order.setStatus(ParkingOrder.STATUS_PAID);
            order.setPayChannel(mapPayChannel(request.getPayMethod()));
            order.setPayTime(LocalDateTime.now());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            parkingOrderMapper.insert(order);
            orderId = order.getId();
            log.info("固定车位绑定 — 生成已支付订单: orderId={} bindingId={} paidAmount={}",
                    order.getId(), binding.getId(), binding.getPaidAmountCents());
        }

        // 如果绑定信息中带 remark，更新到车辆（不覆盖已有）
        if (request.getRemark() != null && vehicle.getRemark() == null) {
            vehicle.setRemark(request.getRemark());
            vehicle.setUpdatedAt(LocalDateTime.now());
            vehicleMapper.updateById(vehicle);
        }

        return toVO(binding);
    }

    // ==================== 续期 ====================

    /**
     * 固定车位续期。
     *
     * @param id      绑定记录ID
     * @param request 续期请求
     * @return 更新后的绑定记录视图
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO renew(Long id, FixedSpaceRenewRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        FixedSpaceBinding binding = getBindingOrThrow(id, tenantId);

        if (!binding.getStatus().equals(FixedSpaceBinding.STATUS_ACTIVE)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅生效中的固定车位可以续期");
        }

        LocalDate oldEnd = binding.getValidEnd();

        binding.setValidEnd(request.getNewValidEnd());
        binding.setPayMethod(request.getPayMethod());
        binding.setPaidAmountCents(request.getPaidAmountCents() != null ? request.getPaidAmountCents() : 0);
        binding.setRemark(request.getRemark());
        binding.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(binding);

        log.info("固定车位续期成功: bindingId={} spaceNo={} oldEnd={} newEnd={} payMethod={} paid={}",
                id, binding.getSpaceNo(), oldEnd, request.getNewValidEnd(),
                request.getPayMethod(), request.getPaidAmountCents());

        return toVO(binding);
    }

    // ==================== 注销 ====================

    /**
     * 固定车位注销。
     * <p>
     * 将 {@code status} 改为 3（已注销），释放车位号。
     * 注销后该车辆入场按临停计费。
     *
     * @param id 绑定记录ID
     * @return 更新后的绑定记录视图
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO cancel(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        FixedSpaceBinding binding = getBindingOrThrow(id, tenantId);

        Integer oldStatus = binding.getStatus();

        binding.setStatus(FixedSpaceBinding.STATUS_DISABLED);
        binding.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(binding);

        log.info("固定车位注销成功: bindingId={} spaceNo={} plate(vehicleId={}) oldStatus={}",
                id, binding.getSpaceNo(), binding.getVehicleId(), oldStatus);

        return toVO(binding);
    }

    // ==================== 查询方法（供内部调用） ====================

    /**
     * 查询指定车牌在指定车场是否有生效中的固定车位绑定。
     * <p>
     * 此方法供 {@link EntryService} 和 {@link ExitService} 在入场/出场流程中调用。
     *
     * @param plateNumber  车牌号（标准化大写）
     * @param parkingLotId 车场ID
     * @return true 如果存在生效中的固定车位绑定
     */
    public boolean hasActiveBinding(String plateNumber, Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return false;
        }
        return hasActiveBinding(plateNumber, parkingLotId, tenantId);
    }

    /**
     * 查询指定车牌在指定车场是否有生效中的固定车位绑定（无租户上下文版）。
     *
     * @param plateNumber  车牌号
     * @param parkingLotId 车场ID
     * @param tenantId     租户ID
     * @return true 如果存在生效中的固定车位绑定
     */
    public boolean hasActiveBinding(String plateNumber, Long parkingLotId, Long tenantId) {
        if (plateNumber == null || parkingLotId == null || tenantId == null) {
            return false;
        }

        // 先查车辆ID
        SysVehicle vehicle = vehicleMapper.selectByPlateNumber(plateNumber, tenantId);
        if (vehicle == null) {
            return false;
        }

        Long count = bindingMapper.selectCount(new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("vehicle_id", vehicle.getId())
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE)
                .ge("valid_end", LocalDate.now())
                .le("valid_start", LocalDate.now()));
        return count != null && count > 0;
    }

    /**
     * 根据车辆ID查询指定车场是否有生效中的固定车位绑定。
     * <p>
     * 此方法供 {@link VehicleTypeDecisionServiceImpl} 在 booth 路径中调用，
     * 避免重复按车牌查询车辆。
     *
     * @param vehicleId    车辆ID
     * @param parkingLotId 车场ID
     * @param tenantId     租户ID
     * @return true 如果存在生效中的固定车位绑定
     */
    public boolean hasActiveBindingByVehicleId(Long vehicleId, Long parkingLotId, Long tenantId) {
        if (vehicleId == null || parkingLotId == null || tenantId == null) {
            return false;
        }

        Long count = bindingMapper.selectCount(new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("vehicle_id", vehicleId)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE)
                .ge("valid_end", LocalDate.now())
                .le("valid_start", LocalDate.now()));
        return count != null && count > 0;
    }

    // ==================== 内部方法 ====================

    /**
     * 查找绑定记录，不存在或跨租户时抛出异常。
     */
    private FixedSpaceBinding getBindingOrThrow(Long id, Long tenantId) {
        FixedSpaceBinding binding = bindingMapper.selectById(id);
        if (binding == null || !tenantId.equals(binding.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "固定车位绑定记录不存在");
        }
        return binding;
    }

    /**
     * 校验同一车场同一车位号不重复。
     */
    private void checkDuplicateSpace(Long tenantId, Long parkingLotId, Long zoneId, String spaceNo, Long excludeId) {
        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("zone_id", zoneId)
                .eq("space_no", spaceNo)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE);
        if (excludeId != null) {
            query.ne("id", excludeId);
        }

        Long count = bindingMapper.selectCount(query);
        if (count != null && count > 0) {
            String lotName = getParkingLotName(parkingLotId);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车位号已被绑定（车场=" + (lotName != null ? lotName : parkingLotId)
                            + "，车位号=" + spaceNo + "）");
        }
    }

    /**
     * 校验同一车间一车辆不重复绑定。
     */
    private void checkDuplicateVehicle(Long tenantId, Long parkingLotId, Long vehicleId, Long excludeId) {
        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("vehicle_id", vehicleId)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE);
        if (excludeId != null) {
            query.ne("id", excludeId);
        }

        Long count = bindingMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车辆已在当前车场绑定固定车位");
        }
    }

    /**
     * 批量补全关联信息（车场名称、区域名称、车牌号）。
     */
    private List<FixedSpaceVO> enrichVOs(List<FixedSpaceBinding> bindings) {
        // 加载车场名称
        List<Long> lotIds = bindings.stream()
                .map(FixedSpaceBinding::getParkingLotId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> lotNames = lotIds.isEmpty() ? Collections.emptyMap()
                : parkingLotMapper.selectBatchIds(lotIds).stream()
                .collect(Collectors.toMap(ParkingLot::getId, ParkingLot::getName));

        // 加载区域名称
        List<Long> zIds = bindings.stream()
                .filter(b -> b.getZoneId() != null)
                .map(FixedSpaceBinding::getZoneId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> zoneNames = zIds.isEmpty() ? Collections.emptyMap()
                : zoneMapper.selectBatchIds(zIds).stream()
                .collect(Collectors.toMap(ParkingZone::getId, ParkingZone::getName));

        // 加载车牌号
        List<Long> vIds = bindings.stream()
                .map(FixedSpaceBinding::getVehicleId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysVehicle> vehicles = vIds.isEmpty() ? Collections.emptyMap()
                : vehicleMapper.selectBatchIds(vIds).stream()
                .collect(Collectors.toMap(SysVehicle::getId, v -> v));

        return bindings.stream().map(b -> {
            FixedSpaceVO vo = toVO(b);
            vo.setParkingLotName(lotNames.get(b.getParkingLotId()));
            vo.setZoneName(zoneNames.get(b.getZoneId()));
            SysVehicle vehicle = vehicles.get(b.getVehicleId());
            if (vehicle != null) {
                vo.setPlateNumber(vehicle.getPlateNumber());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 单条补全信息，创建 toVO 时使用。
     */
    /**
     * 单条补全信息，创建 toVO 时使用。公开供审核 Controller 调用。
     */
    public FixedSpaceVO toVO(FixedSpaceBinding binding) {
        FixedSpaceVO vo = new FixedSpaceVO();
        vo.setId(binding.getId());
        vo.setParkingLotId(binding.getParkingLotId());
        if (binding.getParkingLotId() != null) {
            ParkingLot lot = parkingLotMapper.selectById(binding.getParkingLotId());
            if (lot != null) {
                vo.setParkingLotName(lot.getName());
            }
        }
        vo.setZoneId(binding.getZoneId());
        if (binding.getZoneId() != null) {
            ParkingZone zone = zoneMapper.selectById(binding.getZoneId());
            if (zone != null) {
                vo.setZoneName(zone.getName());
            }
        }
        vo.setSpaceNo(binding.getSpaceNo());
        vo.setVehicleId(binding.getVehicleId());
        // 加载车牌号
        if (binding.getVehicleId() != null) {
            SysVehicle vehicle = vehicleMapper.selectById(binding.getVehicleId());
            if (vehicle != null) {
                vo.setPlateNumber(vehicle.getPlateNumber());
            }
        }
        vo.setValidStart(binding.getValidStart());
        vo.setValidEnd(binding.getValidEnd());
        vo.setStatus(binding.getStatus());
        vo.setRemark(binding.getRemark());
        vo.setPayMethod(binding.getPayMethod());
        vo.setPaidAmountCents(binding.getPaidAmountCents());
        vo.setReviewStatus(binding.getReviewStatus());
        vo.setReviewRemark(binding.getReviewRemark());
        vo.setSource(binding.getSource());
        vo.setApplicantId(binding.getApplicantId());
        vo.setCreatedAt(binding.getCreatedAt());
        return vo;
    }

    private Map<Long, String> loadParkingLotNames(List<FixedSpaceBinding> bindings) {
        List<Long> lotIds = bindings.stream()
                .map(FixedSpaceBinding::getParkingLotId)
                .distinct()
                .collect(Collectors.toList());
        if (lotIds.isEmpty()) return Collections.emptyMap();
        List<ParkingLot> lots = parkingLotMapper.selectBatchIds(lotIds);
        return lots.stream().collect(Collectors.toMap(ParkingLot::getId, ParkingLot::getName));
    }

    /**
     * 两参 toVO（审核列表等批量场景使用预加载的 lotName 避免 N+1）。
     */
    private FixedSpaceVO toVO(FixedSpaceBinding binding, String parkingLotName) {
        FixedSpaceVO vo = new FixedSpaceVO();
        vo.setId(binding.getId());
        vo.setParkingLotId(binding.getParkingLotId());
        vo.setParkingLotName(parkingLotName);
        vo.setZoneId(binding.getZoneId());
        vo.setSpaceNo(binding.getSpaceNo());
        vo.setVehicleId(binding.getVehicleId());
        vo.setValidStart(binding.getValidStart());
        vo.setValidEnd(binding.getValidEnd());
        vo.setStatus(binding.getStatus());
        vo.setRemark(binding.getRemark());
        vo.setPayMethod(binding.getPayMethod());
        vo.setPaidAmountCents(binding.getPaidAmountCents());
        vo.setReviewStatus(binding.getReviewStatus());
        vo.setReviewRemark(binding.getReviewRemark());
        vo.setSource(binding.getSource());
        vo.setApplicantId(binding.getApplicantId());
        vo.setCreatedAt(binding.getCreatedAt());
        return vo;
    }

    // ==================== 审核 ====================

    /**
     * 审核通过固定车位绑定。
     * <p>
     * 设置 reviewStatus=APPROVED，生成已支付订单。
     *
     * @param id 绑定记录ID
     * @return 更新后的绑定记录视图
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO approve(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        FixedSpaceBinding binding = getBindingOrThrow(id, tenantId);

        if (!FixedSpaceBinding.REVIEW_PENDING.equals(binding.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅待审核状态的固定车位可审核通过");
        }

        updateReviewStatus(binding, FixedSpaceBinding.REVIEW_APPROVED);

        // 生成已支付订单
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(binding.getParkingLotId());
        order.setRefId(binding.getId());
        order.setPlateNumber(getPlateNumber(binding.getVehicleId()));
        order.setOrderType(ParkingOrder.ORDER_TYPE_FIXED_SPACE);
        order.setAmountCents(binding.getPaidAmountCents() != null ? binding.getPaidAmountCents() : 0);
        order.setPaidAmount(binding.getPaidAmountCents() != null ? binding.getPaidAmountCents() : 0);
        order.setPayableAmount(binding.getPaidAmountCents() != null ? binding.getPaidAmountCents() : 0);
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(mapPayChannel(binding.getPayMethod()));
        order.setPayTime(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        log.info("固定车位审核通过: bindingId={} spaceNo={} orderId={}", id, binding.getSpaceNo(), order.getId());

        return toVO(binding);
    }

    /**
     * 驳回固定车位绑定。
     * <p>
     * 设置 reviewStatus=REJECTED。
     *
     * @param id 绑定记录ID
     * @return 更新后的绑定记录视图
     */
    @Transactional(rollbackFor = Exception.class)
    public FixedSpaceVO reject(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        FixedSpaceBinding binding = getBindingOrThrow(id, tenantId);

        if (!FixedSpaceBinding.REVIEW_PENDING.equals(binding.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅待审核状态的固定车位可执行驳回");
        }

        updateReviewStatus(binding, FixedSpaceBinding.REVIEW_REJECTED);

        log.info("固定车位审核驳回: bindingId={} spaceNo={}", id, binding.getSpaceNo());

        return toVO(binding);
    }

    // ==================== 查询辅助 ====================

    /**
     * 统计指定区域内生效中的固定车位绑定数（用于配额检查）。
     *
     * @param tenantId     租户ID
     * @param parkingLotId 车场ID
     * @param zoneId       区域ID
     * @return 生效中的绑定数
     */
    public long countActiveByZone(Long tenantId, Long parkingLotId, Long zoneId) {
        if (tenantId == null || parkingLotId == null || zoneId == null) {
            return 0;
        }
        Long count = bindingMapper.selectCount(new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("zone_id", zoneId)
                .eq("status", FixedSpaceBinding.STATUS_ACTIVE));
        return count != null ? count : 0;
    }

    // ==================== 内部方法（新增） ====================

    /**
     * 更新审核状态（公共内部方法）。
     */
    private void updateReviewStatus(FixedSpaceBinding binding, String targetStatus) {
        binding.setReviewStatus(targetStatus);
        binding.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(binding);
    }

    /**
     * 区域固定车位配额检查。
     */
    private void checkZoneQuota(Long tenantId, Long parkingLotId, Long zoneId) {
        ParkingSpacePolicy policy = spacePolicyMapper.selectByZoneId(zoneId, tenantId);
        if (policy == null || policy.getFixedSpaces() == null || policy.getFixedSpaces() <= 0) {
            // 无策略或无配额限制，跳过
            return;
        }

        long activeCount = countActiveByZone(tenantId, parkingLotId, zoneId);
        log.info("固定车位配额检查: zoneId={} policyFixedSpaces={} activeCount={}",
                zoneId, policy.getFixedSpaces(), activeCount);

        if (activeCount >= policy.getFixedSpaces()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该区域固定车位数已达上限（配额=" + policy.getFixedSpaces() + "，已占用=" + activeCount + "）");
        }
    }

    /**
     * 获取车场名称（用于错误消息）。
     */
    private String getParkingLotName(Long parkingLotId) {
        if (parkingLotId == null) return null;
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        return lot != null ? lot.getName() : null;
    }

    /**
     * 缴费方式映射到订单 payChannel。
     */
    private String mapPayChannel(String payMethod) {
        return switch (payMethod) {
            case FixedSpaceBinding.PAY_METHOD_CASH -> ParkingOrder.PAY_CHANNEL_CASH;
            default -> ParkingOrder.PAY_CHANNEL_BALANCE;
        };
    }

    /**
     * 根据车辆ID查询车牌号。
     */
    private String getPlateNumber(Long vehicleId) {
        if (vehicleId == null) return null;
        SysVehicle vehicle = vehicleMapper.selectById(vehicleId);
        return vehicle != null ? vehicle.getPlateNumber() : null;
    }
}
