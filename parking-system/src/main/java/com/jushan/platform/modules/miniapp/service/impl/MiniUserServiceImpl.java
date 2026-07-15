package com.jushan.platform.modules.miniapp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.miniapp.service.MiniUserService;
import com.jushan.platform.modules.miniapp.vo.MiniParkingRecordVO;
import com.jushan.platform.modules.parking.service.ParkingSpacePolicyService;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.jushan.system.service.WxUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序车主服务实现。
 * <p>
 * 基于 ParkingRecord 提供停车记录查询，通过 PlateBinding 校验车牌归属。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class MiniUserServiceImpl implements MiniUserService {

    private final ParkingRecordMapper parkingRecordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final VehicleMapper vehicleMapper;
    private final WxUserService wxUserService;
    private final ParkingSpacePolicyService parkingSpacePolicyService;

    public MiniUserServiceImpl(ParkingRecordMapper parkingRecordMapper,
                               ParkingLotMapper parkingLotMapper,
                               ParkingOrderMapper parkingOrderMapper,
                               PlateBindingMapper plateBindingMapper,
                               VehicleMapper vehicleMapper,
                               WxUserService wxUserService,
                               ParkingSpacePolicyService parkingSpacePolicyService) {
        this.parkingRecordMapper = parkingRecordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.vehicleMapper = vehicleMapper;
        this.wxUserService = wxUserService;
        this.parkingSpacePolicyService = parkingSpacePolicyService;
    }

    @Override
    public IPage<MiniParkingRecordVO> listParkingRecords(long current, long size) {
        List<String> plates = getBoundPlates();
        if (plates.isEmpty()) {
            return new Page<>(current, size, 0);
        }

        IPage<ParkingRecord> entityPage = parkingRecordMapper.selectPageByPlates(
                new Page<>(current, size), plates);
        return entityPage.convert(this::toMiniVO);
    }

    @Override
    public IPage<MiniParkingRecordVO> listParkingRecordsByPlate(String plateNumber, long current, long size) {
        String plate = normalizePlate(plateNumber);
        verifyPlateBelongsToCurrentUser(plate);

        List<String> plates = Collections.singletonList(plate);
        IPage<ParkingRecord> entityPage = parkingRecordMapper.selectPageByPlates(
                new Page<>(current, size), plates);
        return entityPage.convert(this::toMiniVO);
    }

    @Override
    public List<MiniParkingRecordVO> listCurrentSessions() {
        List<String> plates = getBoundPlates();
        if (plates.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParkingRecord> records = parkingRecordMapper.selectActiveByPlates(plates);
        return records.stream().map(this::toMiniVO).collect(Collectors.toList());
    }

    @Override
    public MiniParkingRecordVO getParkingRecordDetail(Long id) {
        ParkingRecord record = parkingRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车记录不存在");
        }
        verifyPlateBelongsToCurrentUser(record.getStandardizedPlate());
        return toMiniVO(record);
    }

    @Override
    public ParkingSpaceRemainVO getParkingLotRemain(Long parkingLotId) {
        return parkingSpacePolicyService.calculateRemain(parkingLotId);
    }

    @Override
    public List<String> listBoundPlates() {
        return getBoundPlates();
    }

    // ==================== 私有方法 ====================

    /**
     * 获取当前用户已审核通过的车牌列表（默认车牌优先）。
     */
    private List<String> getBoundPlates() {
        Long wxUserId;
        try {
            wxUserId = wxUserService.getCurrentWxUserId();
        } catch (Exception e) {
            log.debug("获取当前 wx 用户 ID 失败: {}", e.getMessage());
            return Collections.emptyList();
        }

        List<PlateBinding> bindings = plateBindingMapper.selectList(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, wxUserId)
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED)
                        .orderByDesc(PlateBinding::getIsDefault)
                        .orderByDesc(PlateBinding::getCreatedAt));

        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> plates = new ArrayList<>();
        for (PlateBinding binding : bindings) {
            Vehicle vehicle = vehicleMapper.selectById(binding.getVehicleId());
            if (vehicle != null && vehicle.getVehiclePlate() != null) {
                plates.add(vehicle.getVehiclePlate());
            }
        }
        return plates;
    }

    /**
     * 校验车牌是否属于当前用户。
     */
    private void verifyPlateBelongsToCurrentUser(String plate) {
        if (plate == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权查询该车牌");
        }
        List<String> boundPlates = getBoundPlates();
        if (!boundPlates.contains(plate)) {
            log.warn("用户尝试查询非绑定车牌: plate={}", plate);
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权查询该车牌");
        }
    }

    /**
     * 标准化车牌号。
     */
    private String normalizePlate(String plate) {
        if (plate == null) {
            return "";
        }
        return plate.toUpperCase().replaceAll("\\s+", "").trim();
    }

    /**
     * 将 ParkingRecord 转换为小程序 VO。
     */
    private MiniParkingRecordVO toMiniVO(ParkingRecord record) {
        MiniParkingRecordVO vo = new MiniParkingRecordVO();
        vo.setId(record.getId());
        vo.setRecordId(record.getId());
        vo.setPlateNumber(record.getStandardizedPlate());

        // 状态映射：PARKING → IN, COMPLETED → OUT, CANCELLED → OUT
        if (ParkingRecord.STATUS_PARKING.equals(record.getStatus())) {
            vo.setStatus("IN");
        } else {
            vo.setStatus("OUT");
        }

        vo.setEntryTime(record.getEntryTime());
        vo.setExitTime(record.getExitTime());

        // 停车时长（分钟）
        if (record.getEntryTime() != null) {
            LocalDateTime endTime = record.getExitTime() != null ? record.getExitTime() : LocalDateTime.now();
            vo.setDurationMinutes(ChronoUnit.MINUTES.between(record.getEntryTime(), endTime));
        }

        // 查询停车场名称
        if (record.getParkingLotId() != null) {
            ParkingLot lot = parkingLotMapper.selectById(record.getParkingLotId());
            if (lot != null) {
                vo.setParkingLotName(lot.getName());
            } else {
                vo.setParkingLotName("");
            }
        } else {
            vo.setParkingLotName("");
        }

        // 查询关联订单确定支付状态
        ParkingOrder order = findLatestOrder(record.getId());
        if (order != null) {
            // 费用从订单获取（整数分 → 元）
            int feeCents = order.getAmountCents() != null ? order.getAmountCents() : 0;
            vo.setFeeAmount(new BigDecimal(feeCents).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            vo.setFeeCents(feeCents);

            if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                    || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
                vo.setPayStatus("PAID");
            } else if (ParkingOrder.STATUS_CANCELLED.equals(order.getStatus())) {
                vo.setPayStatus("UNPAID");
            } else {
                vo.setPayStatus("UNPAID");
            }
        } else {
            // 无订单：在场状态为 UNPAID，已完成状态根据情况判断
            if (ParkingRecord.STATUS_PARKING.equals(record.getStatus())) {
                vo.setPayStatus("UNPAID");
            } else {
                // 已出场无订单，可能是免费
                vo.setPayStatus("FREE");
            }
            vo.setFeeAmount(BigDecimal.ZERO);
            vo.setFeeCents(0);
        }

        return vo;
    }

    /**
     * 查找停车记录关联的最新订单。
     */
    private ParkingOrder findLatestOrder(Long recordId) {
        List<ParkingOrder> orders = parkingOrderMapper.selectList(
                new LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getParkingRecordId, recordId)
                        .isNull(ParkingOrder::getDeletedAt)
                        .orderByDesc(ParkingOrder::getCreatedAt)
                        .last("LIMIT 1"));
        return orders.isEmpty() ? null : orders.get(0);
    }
}
