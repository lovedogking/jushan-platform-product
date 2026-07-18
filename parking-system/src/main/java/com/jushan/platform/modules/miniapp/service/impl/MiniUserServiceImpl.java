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
import com.jushan.platform.modules.miniapp.entity.ProxyPayRecord;
import com.jushan.platform.modules.miniapp.mapper.ProxyPayRecordMapper;
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
    private final ProxyPayRecordMapper proxyPayRecordMapper;

    public MiniUserServiceImpl(ParkingRecordMapper parkingRecordMapper,
                               ParkingLotMapper parkingLotMapper,
                               ParkingOrderMapper parkingOrderMapper,
                               PlateBindingMapper plateBindingMapper,
                               VehicleMapper vehicleMapper,
                               WxUserService wxUserService,
                               ParkingSpacePolicyService parkingSpacePolicyService,
                               ProxyPayRecordMapper proxyPayRecordMapper) {
        this.parkingRecordMapper = parkingRecordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.vehicleMapper = vehicleMapper;
        this.wxUserService = wxUserService;
        this.parkingSpacePolicyService = parkingSpacePolicyService;
        this.proxyPayRecordMapper = proxyPayRecordMapper;
    }

    @Override
    public IPage<MiniParkingRecordVO> listParkingRecords(long current, long size, String tab) {
        List<String> plates = getBoundPlates();
        Long wxUserId = getCurrentWxUserIdQuietly();

        if ("in_progress".equals(tab)) {
            // 在场中：只查 status=PARKING 的记录
            if (plates.isEmpty()) {
                return new Page<>(current, size, 0);
            }
            List<ParkingRecord> activeRecords = parkingRecordMapper.selectActiveByPlates(plates);
            // 手动分页
            int start = (int) ((current - 1) * size);
            int end = Math.min(start + (int) size, activeRecords.size());
            if (start >= activeRecords.size()) {
                return new Page<>(current, size, 0);
            }
            List<ParkingRecord> pagedRecords = activeRecords.subList(start, end);
            List<MiniParkingRecordVO> vos = pagedRecords.stream().map(this::toMiniVO).collect(Collectors.toList());
            Page<MiniParkingRecordVO> page = new Page<>(current, size, activeRecords.size());
            page.setRecords(vos);
            return page;
        }

        if ("pending_pay".equals(tab)) {
            // 待支付/欠费中：查询有 UNPAID/ARREARS 订单的<b>所有</b>记录（含已出场欠费）
            if (plates.isEmpty()) {
                return new Page<>(current, size, 0);
            }
            List<ParkingRecord> allRecords = parkingRecordMapper.selectByPlates(plates);
            // 限制最大扫描量防止内存溢出
            if (allRecords.size() > 500) {
                allRecords = allRecords.subList(0, 500);
            }
            List<MiniParkingRecordVO> results = new ArrayList<>();
            for (ParkingRecord record : allRecords) {
                ParkingOrder order = findLatestOrder(record.getId());
                if (order != null && (ParkingOrder.STATUS_PENDING_PAY.equals(order.getStatus())
                        || ParkingOrder.STATUS_ARREARS.equals(order.getStatus()))) {
                    results.add(toMiniVO(record));
                }
            }
            // 分页截取
            int start = (int) ((current - 1) * size);
            int end = Math.min(start + (int) size, results.size());
            if (start >= results.size()) {
                return new Page<>(current, size, 0);
            }
            List<MiniParkingRecordVO> paged = results.subList(start, end);
            Page<MiniParkingRecordVO> page = new Page<>(current, size, results.size());
            page.setRecords(paged);
            return page;
        }

        if ("completed".equals(tab)) {
            // 已完成：已支付/已出场的记录 + 代缴记录
            List<MiniParkingRecordVO> results = new ArrayList<>();

            // 1. 自己名下已完成记录
            if (!plates.isEmpty()) {
                List<ParkingRecord> allRecords = parkingRecordMapper.selectByPlates(plates);
                // 限制最大扫描量
                if (allRecords.size() > 500) {
                    allRecords = allRecords.subList(0, 500);
                }
                for (ParkingRecord record : allRecords) {
                    MiniParkingRecordVO vo = toMiniVO(record);
                    if ("PAID".equals(vo.getPayStatus()) && "OUT".equals(vo.getStatus())) {
                        results.add(vo);
                    }
                }
            }

            // 2. 代缴记录（当前用户作为代缴人）
            if (wxUserId != null) {
                List<ProxyPayRecord> proxyRecords = proxyPayRecordMapper.selectList(
                        new LambdaQueryWrapper<ProxyPayRecord>()
                                .eq(ProxyPayRecord::getPayerId, wxUserId)
                                .eq(ProxyPayRecord::getStatus, ProxyPayRecord.STATUS_COMPLETED)
                                .isNull(ProxyPayRecord::getDeletedAt)
                                .orderByDesc(ProxyPayRecord::getCreatedAt));
                for (ProxyPayRecord proxy : proxyRecords) {
                    MiniParkingRecordVO proxyVo = new MiniParkingRecordVO();
                    proxyVo.setId(proxy.getRecordId());
                    proxyVo.setRecordId(proxy.getRecordId());
                    proxyVo.setPlateNumber(proxy.getPlateNumber());
                    proxyVo.setPayStatus("PAID");
                    proxyVo.setStatus("OUT");
                    proxyVo.setProxyPay(true); // 标记为代缴
                    proxyVo.setFeeCents(proxy.getAmountCents());
                    if (proxy.getAmountCents() != null) {
                        proxyVo.setFeeAmount(new BigDecimal(proxy.getAmountCents())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
                    }
                    // 查停车场名称
                    if (proxy.getParkingLotId() != null) {
                        ParkingLot lot = parkingLotMapper.selectById(proxy.getParkingLotId());
                        proxyVo.setParkingLotName(lot != null ? lot.getName() : "");
                    }
                    // 查入场时间（从 record）
                    ParkingRecord proxyRecord = parkingRecordMapper.selectById(proxy.getRecordId());
                    if (proxyRecord != null) {
                        proxyVo.setEntryTime(proxyRecord.getEntryTime());
                        proxyVo.setExitTime(proxyRecord.getExitTime());
                        if (proxyRecord.getEntryTime() != null) {
                            LocalDateTime endTime = proxyRecord.getExitTime() != null
                                    ? proxyRecord.getExitTime() : LocalDateTime.now();
                            proxyVo.setDurationMinutes(ChronoUnit.MINUTES.between(
                                    proxyRecord.getEntryTime(), endTime));
                        }
                    }
                    results.add(proxyVo);
                }
            }

            // 按入场时间倒序排序
            results.sort((a, b) -> {
                LocalDateTime ta = a.getEntryTime();
                LocalDateTime tb = b.getEntryTime();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });

            // 分页截取
            int start = (int) ((current - 1) * size);
            int end = Math.min(start + (int) size, results.size());
            if (start >= results.size()) {
                return new Page<>(current, size, 0);
            }
            List<MiniParkingRecordVO> paged = results.subList(start, end);
            Page<MiniParkingRecordVO> page = new Page<>(current, size, results.size());
            page.setRecords(paged);
            return page;
        }

        // 默认：返回全部
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
     * 静默获取当前 wx 用户 ID（不抛异常）。
     */
    private Long getCurrentWxUserIdQuietly() {
        try {
            return wxUserService.getCurrentWxUserId();
        } catch (Exception e) {
            log.debug("获取当前 wx 用户 ID 失败: {}", e.getMessage());
            return null;
        }
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

            // 设置订单原始状态，前端用于区分欠费标识
            vo.setOrderStatus(order.getStatus());

            if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                    || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
                vo.setPayStatus("PAID");
            } else if (ParkingOrder.STATUS_ARREARS.equals(order.getStatus())) {
                vo.setPayStatus("ARREARS");
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
