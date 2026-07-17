package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.dto.FeePreviewRequest;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.event.PlateStandardizer;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.jushan.system.vo.ParkingFeeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 停车费用查询服务（P007）。
 * <p>
 * 为小程序和岗亭提供当前停车费用查询、结算预览能力。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>小程序端必须校验车牌是否属于当前微信用户</li>
 *   <li>岗亭端必须校验停车场是否在当前用户授权范围内</li>
 *   <li>金额统一使用整数分，不引入浮点数</li>
 *   <li>不修改停车记录和订单状态</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ParkingFeeService {

    private static final Logger log = LoggerFactory.getLogger(ParkingFeeService.class);

    private final ParkingRecordMapper parkingRecordMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final VehicleMapper vehicleMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final BillingEngine billingEngine;
    private final ParkingLotScopeResolver parkingLotScopeResolver;
    private final WxUserService wxUserService;

    public ParkingFeeService(ParkingRecordMapper parkingRecordMapper,
                             ParkingOrderMapper parkingOrderMapper,
                             VehicleMapper vehicleMapper,
                             PlateBindingMapper plateBindingMapper,
                             ParkingLotMapper parkingLotMapper,
                             BillingEngine billingEngine,
                             ParkingLotScopeResolver parkingLotScopeResolver,
                             WxUserService wxUserService) {
        this.parkingRecordMapper = parkingRecordMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.vehicleMapper = vehicleMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.billingEngine = billingEngine;
        this.parkingLotScopeResolver = parkingLotScopeResolver;
        this.wxUserService = wxUserService;
    }

    // ==================== 小程序端 ====================

    /**
     * 小程序：按车牌查询当前停车费用。
     *
     * @param rawPlate 原始车牌号
     * @return 费用信息
     */
    public ParkingFeeVO queryByPlateForWx(String rawPlate) {
        Long wxUserId = wxUserService.getCurrentWxUserId();
        String plate = normalizePlate(rawPlate);
        verifyPlateBelongsToWxUser(wxUserId, plate);

        List<ParkingRecord> records = parkingRecordMapper.selectList(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getStandardizedPlate, plate)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING));

        if (records.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "未找到该车牌的在场记录");
        }
        if (records.size() > 1) {
            log.warn("小程序用户 {} 查询车牌 {} 发现多条在场记录，要求使用记录 ID 查询", wxUserId, plate);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车牌存在多条在场记录，请使用停车记录 ID 查询");
        }

        return buildFeeVo(records.get(0), LocalDateTime.now(), false);
    }

    /**
     * 小程序：按停车记录 ID 查询费用。
     *
     * @param recordId 停车记录 ID
     * @return 费用信息
     */
    public ParkingFeeVO queryByRecordIdForWx(Long recordId) {
        Long wxUserId = wxUserService.getCurrentWxUserId();
        ParkingRecord record = getRecordOrThrow(recordId);
        verifyPlateBelongsToWxUser(wxUserId, record.getStandardizedPlate());
        return buildFeeVo(record, resolveExitTime(record), false);
    }

    /**
     * 小程序：结算预览 / 重新计费。
     *
     * @param request 预览请求
     * @return 费用信息
     */
    public ParkingFeeVO previewForWx(FeePreviewRequest request) {
        Long wxUserId = wxUserService.getCurrentWxUserId();
        ParkingRecord record = getRecordOrThrow(request.getRecordId());
        verifyPlateBelongsToWxUser(wxUserId, record.getStandardizedPlate());
        LocalDateTime previewExitTime = resolvePreviewExitTime(request.getPreviewExitTime(), record);
        return buildFeeVo(record, previewExitTime, true);
    }

    // ==================== 岗亭端 ====================

    /**
     * 岗亭：按停车场 + 车牌查询当前停车费用。
     *
     * @param rawPlate     原始车牌号
     * @param parkingLotId 停车场 ID
     * @return 费用信息
     */
    public ParkingFeeVO queryByPlateForBooth(String rawPlate, Long parkingLotId) {
        parkingLotScopeResolver.validateAccess(parkingLotId);
        String plate = normalizePlate(rawPlate);
        ParkingRecord record = findActiveRecord(parkingLotId, plate);
        return buildFeeVo(record, LocalDateTime.now(), false);
    }

    /**
     * 岗亭：按停车记录 ID 查询费用。
     *
     * @param recordId 停车记录 ID
     * @return 费用信息
     */
    public ParkingFeeVO queryByRecordIdForBooth(Long recordId) {
        ParkingRecord record = getRecordOrThrow(recordId);
        parkingLotScopeResolver.validateAccess(record.getParkingLotId());
        return buildFeeVo(record, resolveExitTime(record), false);
    }

    /**
     * 岗亭：结算预览 / 重新计费。
     *
     * @param request 预览请求
     * @return 费用信息
     */
    public ParkingFeeVO previewForBooth(FeePreviewRequest request) {
        ParkingRecord record = getRecordOrThrow(request.getRecordId());
        parkingLotScopeResolver.validateAccess(record.getParkingLotId());
        LocalDateTime previewExitTime = resolvePreviewExitTime(request.getPreviewExitTime(), record);
        return buildFeeVo(record, previewExitTime, true);
    }

    // ==================== 私有方法 ====================

    private ParkingRecord getRecordOrThrow(Long recordId) {
        if (recordId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车记录 ID 不能为空");
        }
        ParkingRecord record = parkingRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车记录不存在");
        }
        return record;
    }

    private ParkingRecord findActiveRecord(Long parkingLotId, String plate) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }
        if (plate == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车牌号不能为空");
        }
        List<ParkingRecord> records = parkingRecordMapper.selectList(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStandardizedPlate, plate)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING)
                        .orderByDesc(ParkingRecord::getEntryTime)
                        .last("LIMIT 1"));
        if (records.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "未找到该车牌的在场记录");
        }
        return records.get(0);
    }

    private String normalizePlate(String rawPlate) {
        String plate = PlateStandardizer.normalize(rawPlate);
        if (plate == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车牌号不能为空");
        }
        if (!PlateStandardizer.isValidFormat(plate)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车牌号格式不正确");
        }
        return plate;
    }

    private void verifyPlateBelongsToWxUser(Long wxUserId, String plate) {
        Vehicle vehicle = vehicleMapper.selectOne(
                new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getVehiclePlate, plate));
        if (vehicle == null) {
            log.warn("小程序用户 {} 查询未登记车牌 {}", wxUserId, plate);
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权查询该车牌");
        }
        Long count = plateBindingMapper.selectCount(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, wxUserId)
                        .eq(PlateBinding::getVehicleId, vehicle.getId())
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED));
        if (count == null || count == 0) {
            log.warn("小程序用户 {} 无权查询车牌 {}", wxUserId, plate);
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权查询该车牌");
        }
    }

    private LocalDateTime resolveExitTime(ParkingRecord record) {
        if (ParkingRecord.STATUS_COMPLETED.equals(record.getStatus())) {
            return record.getExitTime();
        }
        if (ParkingRecord.STATUS_CANCELLED.equals(record.getStatus())) {
            return record.getEntryTime();
        }
        return LocalDateTime.now();
    }

    private LocalDateTime resolvePreviewExitTime(LocalDateTime previewExitTime, ParkingRecord record) {
        LocalDateTime exitTime = previewExitTime != null ? previewExitTime : LocalDateTime.now();
        if (!ParkingRecord.STATUS_PARKING.equals(record.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车辆已离场或记录已取消，无法预览结算");
        }
        if (exitTime.isBefore(record.getEntryTime())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "预览出场时间不能早于入场时间");
        }
        return exitTime;
    }

    private ParkingFeeVO buildFeeVo(ParkingRecord record, LocalDateTime exitTime, boolean isPreview) {
        ParkingFeeVO vo = new ParkingFeeVO();
        vo.setRecordId(record.getId());
        vo.setParkingLotId(record.getParkingLotId());
        vo.setPlate(record.getStandardizedPlate());
        vo.setEntryTime(record.getEntryTime());
        vo.setExitTime(record.getExitTime());
        vo.setStatus(record.getStatus());
        vo.setPayable(ParkingRecord.STATUS_PARKING.equals(record.getStatus()));

        ParkingLot lot = parkingLotMapper.selectById(record.getParkingLotId());
        if (lot != null) {
            vo.setParkingLotName(lot.getName());
        }

        if (isPreview) {
            vo.setPreviewExitTime(exitTime);
        }

        switch (record.getStatus()) {
            case ParkingRecord.STATUS_COMPLETED -> buildCompletedFee(vo, record);
            case ParkingRecord.STATUS_CANCELLED -> buildCancelledFee(vo, record);
            case ParkingRecord.STATUS_PARKING -> buildParkingFee(vo, record, exitTime);
            default -> throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "未知的停车记录状态: " + record.getStatus());
        }

        return vo;
    }

    private void buildCompletedFee(ParkingFeeVO vo, ParkingRecord record) {
        if (record.getEntryTime() != null && record.getExitTime() != null) {
            vo.setDurationMinutes(Duration.between(record.getEntryTime(), record.getExitTime()).toMinutes());
        }
        ParkingOrder order = parkingOrderMapper.selectOne(
                new LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getParkingRecordId, record.getId())
                        .in(ParkingOrder::getStatus,
                                ParkingOrder.STATUS_COMPLETED, ParkingOrder.STATUS_PAID)
                        .orderByDesc(ParkingOrder::getCreatedAt)
                        .last("LIMIT 1"));
        if (order != null) {
            vo.setFeeCents(order.getAmountCents());
        } else {
            vo.setFeeCents(0);
        }
        vo.setPayable(false);
        vo.setHasPendingOrder(false);
    }

    private void buildCancelledFee(ParkingFeeVO vo, ParkingRecord record) {
        vo.setDurationMinutes(0L);
        vo.setFeeCents(0);
        vo.setPayable(false);
        vo.setHasPendingOrder(false);
        vo.setMessage("记录已取消");
    }

    private void buildParkingFee(ParkingFeeVO vo, ParkingRecord record, LocalDateTime exitTime) {
        int feeCents = billingEngine.calculateFee(record.getParkingLotId(), record.getEntryTime(), exitTime);
        long durationMinutes = Duration.between(record.getEntryTime(), exitTime).toMinutes();
        vo.setFeeCents(feeCents);
        vo.setDurationMinutes(durationMinutes);

        BillingRuleVersion version = billingEngine.findActiveVersion(record.getParkingLotId());
        if (version != null) {
            vo.setRuleVersionId(version.getId());
            vo.setRuleVersion("v" + version.getVersion());
            Integer freeMinutes = version.getFreeMinutes();
            vo.setFreeMinutes(freeMinutes != null && freeMinutes > 0 ? freeMinutes : 0);
            if (vo.getFreeMinutes() > 0) {
                vo.setFreeExitDeadline(record.getEntryTime().plusMinutes(vo.getFreeMinutes()));
            }
        } else {
            vo.setFreeMinutes(0);
        }

        ParkingOrder pendingOrder = parkingOrderMapper.selectOne(
                new LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getParkingRecordId, record.getId())
                        .in(ParkingOrder::getStatus,
                                ParkingOrder.STATUS_PRE_ORDER, ParkingOrder.STATUS_PENDING_PAY)
                        .orderByDesc(ParkingOrder::getCreatedAt)
                        .last("LIMIT 1"));
        if (pendingOrder != null) {
            vo.setHasPendingOrder(true);
            vo.setPendingOrderId(pendingOrder.getId());
            vo.setPendingOrderAmountCents(pendingOrder.getAmountCents());
        } else {
            vo.setHasPendingOrder(false);
        }

        if (feeCents == 0 && vo.getFreeMinutes() > 0 && durationMinutes <= vo.getFreeMinutes()) {
            vo.setMessage("当前仍在免费时段内");
        }
    }
}
