package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.dto.VehicleRenewalCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.service.VehicleRenewalService;
import com.jushan.platform.modules.vehicle.vo.RenewalOrderVO;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.ParkingLotMapper;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 月卡管理服务（Phase 1 A1）。
 * <p>
 * 基于 {@code sys_vehicle} 表（{@code vehicleType=MONTHLY}）提供独立的月卡管理能力。
 * 不与普通车辆管理共享视图，避免功能混淆。
 * <p>
 * 职责：
 * <ul>
 *   <li>月卡列表查询（分页+筛选）</li>
 *   <li>月卡登记（创建 MONTHLY 车辆）</li>
 *   <li>月卡续期（延长有效期+记录续费日志）</li>
 *   <li>月卡注销（转为 FREE 类型，按临停计费）</li>
 *   <li>到期预警查询（N天内到期，N暂写死为7天）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class MonthlyPassService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassService.class);

    /** 到期预警默认提前天数（等 A3 系统参数管理完成后替换为配置值） */
    public static final int DEFAULT_EXPIRING_DAYS = 7;

    private final SysVehicleMapper vehicleMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final VehicleRenewalService renewalService;
    private final VehicleRenewalLogMapper renewalLogMapper;

    public MonthlyPassService(SysVehicleMapper vehicleMapper,
                              ParkingLotMapper parkingLotMapper,
                              VehicleRenewalService renewalService,
                              VehicleRenewalLogMapper renewalLogMapper) {
        this.vehicleMapper = vehicleMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.renewalService = renewalService;
        this.renewalLogMapper = renewalLogMapper;
    }

    // ==================== 月卡列表 ====================

    /**
     * 分页查询月卡列表。
     *
     * @param plateNumber  车牌号（可选，模糊匹配）
     * @param parkingLotId 车场ID（可选）
     * @param status       状态（可选）
     * @param validEndFrom 有效期开始（可选）
     * @param validEndTo   有效期结束（可选）
     * @param page         页码
     * @param size         每页大小
     * @return 分页月卡列表
     */
    public IPage<MonthlyPassVO> pageList(String plateNumber, Long parkingLotId,
                                          String status, LocalDate validEndFrom, LocalDate validEndTo,
                                          int page, int size) {
        Long tenantId = TenantContext.requireTenantId();

        QueryWrapper<SysVehicle> query = new QueryWrapper<SysVehicle>()
                .eq("vehicle_type", SysVehicle.TYPE_MONTHLY)
                .eq("tenant_id", tenantId);

        if (plateNumber != null && !plateNumber.isEmpty()) {
            query.like("plate_number", plateNumber.toUpperCase());
        }
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        if (status != null && !status.isEmpty()) {
            query.eq("status", status);
        }
        if (validEndFrom != null) {
            query.ge("valid_end_date", validEndFrom);
        }
        if (validEndTo != null) {
            query.le("valid_end_date", validEndTo);
        }

        query.orderByDesc("created_at");

        IPage<SysVehicle> vehiclePage = vehicleMapper.selectPage(new Page<>(page, size), query);
        if (vehiclePage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        // 批量补全场名称
        Map<Long, String> lotNames = loadParkingLotNames(vehiclePage.getRecords());

        List<MonthlyPassVO> voList = vehiclePage.getRecords().stream()
                .map(v -> toMonthlyPassVO(v, lotNames.get(v.getParkingLotId())))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(vehiclePage.getCurrent(), vehiclePage.getSize(), vehiclePage.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 到期预警列表。
     * <p>
     * 查询 {@code status=ACTIVE} 且 {@code validEndDate} 在 N 天内到期的月卡，
     * 按到期时间升序排列。
     *
     * @param days 提前天数（默认 7 天）
     * @param page 页码
     * @param size 每页大小
     * @return 分页到期预警列表
     */
    public IPage<MonthlyPassVO> expiringList(Integer days, int page, int size) {
        Long tenantId = TenantContext.requireTenantId();
        int expiringDays = (days != null && days > 0) ? days : DEFAULT_EXPIRING_DAYS;

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(expiringDays);

        QueryWrapper<SysVehicle> query = new QueryWrapper<SysVehicle>()
                .eq("vehicle_type", SysVehicle.TYPE_MONTHLY)
                .eq("tenant_id", tenantId)
                .eq("status", SysVehicle.STATUS_ACTIVE)
                .le("valid_end_date", deadline)
                .ge("valid_end_date", today) // 不展示已过期的
                .orderByAsc("valid_end_date");

        IPage<SysVehicle> vehiclePage = vehicleMapper.selectPage(new Page<>(page, size), query);
        if (vehiclePage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(vehiclePage.getRecords());
        List<MonthlyPassVO> voList = vehiclePage.getRecords().stream()
                .map(v -> toMonthlyPassVO(v, lotNames.get(v.getParkingLotId())))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(vehiclePage.getCurrent(), vehiclePage.getSize(), vehiclePage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 月卡登记 ====================

    /**
     * 登记月卡（创建 MONTHLY 类型车辆）。
     * <p>
     * 校验同一车场同一车牌不能重复办理月卡（ACTIVE/EXPIRED 状态拒绝）。
     *
     * @param request 登记请求
     * @return 月卡视图
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO create(MonthlyPassCreateRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 校验重复：同一车场同一车牌不能重复办理月卡
        checkDuplicateMonthlyPass(tenantId, request.getParkingLotId(), plate, null);

        // 创建 MONTHLY 车辆
        SysVehicle vehicle = new SysVehicle();
        vehicle.setTenantId(tenantId);
        vehicle.setParkingLotId(request.getParkingLotId());
        vehicle.setPlateNumber(plate);
        vehicle.setPlateColor(request.getPlateColor());
        vehicle.setVehicleType(SysVehicle.TYPE_MONTHLY);
        vehicle.setValidStartDate(request.getValidStartDate());
        vehicle.setValidEndDate(request.getValidEndDate());
        vehicle.setOwnerName(request.getOwnerName());
        vehicle.setOwnerPhone(request.getOwnerPhone());
        vehicle.setRemark(request.getRemark());
        vehicle.setStatus(SysVehicle.STATUS_ACTIVE);
        vehicle.setCreatedAt(LocalDateTime.now());
        vehicle.setUpdatedAt(LocalDateTime.now());
        vehicleMapper.insert(vehicle);

        log.info("月卡登记成功: vehicleId={} plate={} parkingLotId={} validEnd={}",
                vehicle.getId(), plate, request.getParkingLotId(), request.getValidEndDate());

        return toMonthlyPassVO(vehicle, getParkingLotName(request.getParkingLotId()));
    }

    // ==================== 月卡续期 ====================

    /**
     * 月卡续期。
     * <p>
     * 通过 {@link VehicleRenewalService} 创建续费订单并立即生效，
     * 然后写入 {@code vehicle_renewal_log} 记录。
     * <p>
     * 管理员在柜台确认收款后调用此接口，不经过模拟支付流程。
     *
     * @param id      车辆ID
     * @param request 续期请求
     * @return 月卡视图
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO renew(Long id, MonthlyPassRenewRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        SysVehicle vehicle = getMonthlyPassOrThrow(id, tenantId);

        // 记录续费前有效期
        LocalDate oldValidEnd = vehicle.getValidEndDate();

        // 1. 创建续费订单（PENDING_PAY）
        VehicleRenewalCmd cmd = new VehicleRenewalCmd();
        cmd.setRenewalMonths(request.getRenewalMonths());
        cmd.setPayChannel(com.jushan.system.entity.ParkingOrder.PAY_CHANNEL_CASH);
        cmd.setAmountCents(request.getAmountCents());
        RenewalOrderVO renewalOrder = renewalService.createRenewalOrder(id, cmd);

        // 2. 立即标记生效（管理员已确认收款）
        String paySerial = "ADMIN-RENEW-" + renewalOrder.getOrderId() + "-" + System.currentTimeMillis();
        renewalService.applyRenewalEffect(renewalOrder.getOrderId(), paySerial);

        // 3. 写入续费记录
        VehicleRenewalLog logEntry = new VehicleRenewalLog();
        logEntry.setTenantId(tenantId);
        logEntry.setParkingLotId(vehicle.getParkingLotId());
        logEntry.setVehicleId(id);
        logEntry.setPlateNumber(vehicle.getPlateNumber());
        logEntry.setOrderId(renewalOrder.getOrderId());
        logEntry.setRenewalMonths(request.getRenewalMonths());
        logEntry.setAmountCents(request.getAmountCents());
        logEntry.setOldValidEnd(oldValidEnd);
        logEntry.setNewValidEnd(oldValidEnd != null
                ? oldValidEnd.plusMonths(request.getRenewalMonths())
                : LocalDate.now().plusMonths(request.getRenewalMonths()));
        logEntry.setOperatorId(TenantContext.requireUserId());
        logEntry.setRemark(request.getRemark());
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        renewalLogMapper.insert(logEntry);

        log.info("月卡续期成功: vehicleId={} plate={} months={} amount={} oldEnd={} newEnd={}",
                id, vehicle.getPlateNumber(), request.getRenewalMonths(),
                request.getAmountCents(), oldValidEnd, logEntry.getNewValidEnd());

        // 重新查询最新数据
        vehicle = vehicleMapper.selectById(id);
        return toMonthlyPassVO(vehicle, getParkingLotName(vehicle.getParkingLotId()));
    }

    // ==================== 月卡注销 ====================

    /**
     * 月卡注销。
     * <p>
     * 将 {@code vehicleType} 改为 {@code FREE}，{@code status} 改为 {@code DISABLED}。
     * 注销后该车辆按临停计费，入场时不再自动放行。
     *
     * @param id 车辆ID
     * @return 月卡视图
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO cancel(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        SysVehicle vehicle = getMonthlyPassOrThrow(id, tenantId);

        String oldVehicleType = vehicle.getVehicleType();
        String oldStatus = vehicle.getStatus();

        vehicle.setVehicleType(SysVehicle.TYPE_FREE);
        vehicle.setStatus(SysVehicle.STATUS_DISABLED);
        vehicle.setUpdatedAt(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);

        log.info("月卡注销成功: vehicleId={} plate={} oldType={} oldStatus={}",
                id, vehicle.getPlateNumber(), oldVehicleType, oldStatus);

        return toMonthlyPassVO(vehicle, getParkingLotName(vehicle.getParkingLotId()));
    }

    // ==================== 内部方法 ====================

    /**
     * 查找月卡车辆，非 MONTHLY 类型或跨租户时抛出异常。
     */
    private SysVehicle getMonthlyPassOrThrow(Long id, Long tenantId) {
        SysVehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡车辆不存在");
        }
        if (!SysVehicle.TYPE_MONTHLY.equals(vehicle.getVehicleType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该车辆不是月卡，无法操作");
        }
        return vehicle;
    }

    /**
     * 校验同一车场同一车牌是否已存在有效月卡。
     *
     * @param tenantId      租户ID
     * @param parkingLotId  车场ID
     * @param plate         车牌号（已大写）
     * @param excludeVehicleId 排除的车辆ID（更新时使用）
     */
    private void checkDuplicateMonthlyPass(Long tenantId, Long parkingLotId, String plate, Long excludeVehicleId) {
        QueryWrapper<SysVehicle> query = new QueryWrapper<SysVehicle>()
                .eq("vehicle_type", SysVehicle.TYPE_MONTHLY)
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("plate_number", plate);
        if (excludeVehicleId != null) {
            query.ne("id", excludeVehicleId);
        }
        // 排除已注销的（DISABLED 状态允许重新办理）
        query.in("status", SysVehicle.STATUS_ACTIVE, SysVehicle.STATUS_EXPIRED);

        Long count = vehicleMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该车牌已在当前车场办理月卡，不能重复登记");
        }
    }

    /**
     * 批量加载车场名称。
     */
    private Map<Long, String> loadParkingLotNames(List<SysVehicle> vehicles) {
        List<Long> lotIds = vehicles.stream()
                .map(SysVehicle::getParkingLotId)
                .distinct()
                .collect(Collectors.toList());
        if (lotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ParkingLot> lots = parkingLotMapper.selectBatchIds(lotIds);
        return lots.stream().collect(Collectors.toMap(ParkingLot::getId, ParkingLot::getName));
    }

    /**
     * 查询单个车场名称。
     */
    private String getParkingLotName(Long parkingLotId) {
        if (parkingLotId == null) return null;
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        return lot != null ? lot.getName() : null;
    }

    /**
     * SysVehicle → MonthlyPassVO 转换。
     */
    private MonthlyPassVO toMonthlyPassVO(SysVehicle vehicle, String parkingLotName) {
        MonthlyPassVO vo = new MonthlyPassVO();
        vo.setId(vehicle.getId());
        vo.setPlateNumber(vehicle.getPlateNumber());
        vo.setPlateColor(vehicle.getPlateColor());
        vo.setParkingLotId(vehicle.getParkingLotId());
        vo.setParkingLotName(parkingLotName);
        vo.setVehicleType(vehicle.getVehicleType());
        vo.setValidStartDate(vehicle.getValidStartDate());
        vo.setValidEndDate(vehicle.getValidEndDate());
        vo.setStatus(vehicle.getStatus());
        vo.setOwnerName(vehicle.getOwnerName());
        vo.setOwnerPhone(vehicle.getOwnerPhone());
        vo.setRemark(vehicle.getRemark());
        vo.setCreatedAt(vehicle.getCreatedAt());
        return vo;
    }
}
