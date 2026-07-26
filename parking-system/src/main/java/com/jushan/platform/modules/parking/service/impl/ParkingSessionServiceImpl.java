package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.parking.vo.ParkingSessionVO;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingOrderMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 在场车辆管理服务实现。
 * <p>
 * 支持车辆入场/出场记录，重复入场策略处理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class ParkingSessionServiceImpl extends ServiceImpl<ParkingSessionMapper, ParkingSession>
        implements ParkingSessionService {

    private final ParkingRecordMapper parkingRecordMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper parkingLaneMapper;
    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String lastSequenceDate = "";

    public ParkingSessionServiceImpl(ParkingRecordMapper parkingRecordMapper,
                                     ParkingOrderMapper parkingOrderMapper,
                                     ParkingLotMapper parkingLotMapper,
                                     ParkingLaneMapper parkingLaneMapper) {
        this.parkingRecordMapper = parkingRecordMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.parkingLaneMapper = parkingLaneMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingSessionVO entry(ParkingSessionEntryCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        // MQ 消费者等无租户上下文场景，从命令中读取
        if (tenantId == null && cmd.getTenantId() != null) {
            tenantId = cmd.getTenantId();
        }
        // 平台管理员等无租户上下文场景，从停车场推导
        if (tenantId == null && cmd.getParkingLotId() != null) {
            ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(cmd.getParkingLotId());
            if (lot != null) {
                tenantId = lot.getTenantId();
            }
        }
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "租户ID不能为空");
        }
        String standardizedPlate = cmd.getPlateNumber() != null ? cmd.getPlateNumber().toUpperCase() : null;

        // 检查是否已有 ParkingRecord 的 PARKING 记录（由 EntryService 创建）
        List<ParkingRecord> activeRecords = parkingRecordMapper.selectActiveByPlate(
                cmd.getParkingLotId(), standardizedPlate);
        if (!activeRecords.isEmpty()) {
            // 已有 ParkingRecord 在场记录，说明 EntryService 已处理，检查 ParkingSession 是否存在
            ParkingSession existing = baseMapper.selectInByPlateNumber(standardizedPlate, tenantId);
            if (existing != null) {
                // ParkingSession 已存在，直接返回（幂等）
                log.info("ParkingSession 已存在（EntryService 已同步创建）: sessionId={}, plate={}",
                        existing.getId(), standardizedPlate);
                return toVO(existing);
            }
            // ParkingRecord 存在但 ParkingSession 不存在，继续创建 ParkingSession
            log.info("ParkingRecord 存在但 ParkingSession 不存在，补创建: plate={}", standardizedPlate);
        }

        // 检查是否已有 ParkingSession 在场记录（重复入场幂等处理）
        ParkingSession existingSession = baseMapper.selectInByPlateNumber(standardizedPlate, tenantId);
        if (existingSession != null) {
            // 幂等：以最新识别为准，更新原在场记录的入场信息，不新建记录、不标记异常
            // 适用场景：车牌在相机前停留导致的重复识别、系统重启/设备误触发后的补识别
            existingSession.setParkingLotId(cmd.getParkingLotId());
            existingSession.setLaneId(cmd.getLaneId());
            existingSession.setEntryTime(cmd.getEntryTime() != null ? cmd.getEntryTime() : LocalDateTime.now());
            if (cmd.getEntryImage() != null) {
                existingSession.setEntryImage(cmd.getEntryImage());
            }
            if (cmd.getVehicleType() != null) {
                existingSession.setVehicleType(cmd.getVehicleType());
            }
            if (cmd.getPlateColor() != null) {
                existingSession.setPlateColor(cmd.getPlateColor());
            }
            if (cmd.getEntryTrigger() != null) {
                existingSession.setEntryTrigger(cmd.getEntryTrigger());
            }
            existingSession.setUpdatedAt(LocalDateTime.now());
            baseMapper.updateById(existingSession);
            log.info("重复入场幂等处理（以最新识别为准）: plate={}, sessionId={}",
                    standardizedPlate, existingSession.getId());
            return toVO(existingSession);
        }

        ParkingSession entity = new ParkingSession();
        entity.setParkingLotId(cmd.getParkingLotId());
        entity.setLaneId(cmd.getLaneId());
        entity.setPlateNumber(standardizedPlate);
        entity.setPlateColor(cmd.getPlateColor());
        entity.setVehicleType(cmd.getVehicleType());
        entity.setEntryTime(cmd.getEntryTime() != null ? cmd.getEntryTime() : LocalDateTime.now());
        entity.setEntryImage(cmd.getEntryImage());
        entity.setEntryOperator(cmd.getEntryOperator());
        entity.setEntryTrigger(cmd.getEntryTrigger());
        entity.setFeeAmount(cmd.getFeeAmount());
        entity.setPaidAmount(cmd.getPaidAmount());
        entity.setStatus(ParkingSession.STATUS_IN);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("车辆入场记录: sessionId={}, plate={}, lotId={}",
                entity.getId(), standardizedPlate, cmd.getParkingLotId());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingSessionVO exit(ParkingSessionExitCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        ParkingSession entity = baseMapper.selectById(cmd.getSessionId());
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "在场记录不存在");
        }
        // MQ 消费者等无租户上下文场景，从实体中读取 tenantId
        if (tenantId == null) {
            tenantId = entity.getTenantId();
        }
        if (!tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "在场记录不存在");
        }

        if (!ParkingSession.STATUS_IN.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车辆不在场，当前状态: " + entity.getStatus());
        }

        LocalDateTime exitTime = LocalDateTime.now();

        // 更新出场信息（含 ParkingRecordId 关联）
        int affected = baseMapper.updateExitWithRecord(
                entity.getId(),
                exitTime,
                cmd.getExitLaneId(),
                cmd.getExitImage(),
                cmd.getExitOperator(),
                cmd.getFeeAmount(),
                cmd.getPaidAmount(),
                cmd.getParkingRecordId()
        );

        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "出场记录更新失败");
        }

        // 刷新实体
        entity = baseMapper.selectById(entity.getId());

        // 不再创建 ParkingRecord 和 ParkingOrder，由 ExitService 统一处理
        // 如果 ExitService 未处理（岗亭直接调用场景），则保留原有逻辑
        // 但当前联调以 MQ 链路为主，ParkingSession 只负责在场状态管理

        log.info("车辆出场记录: sessionId={}, plate={}, fee={}",
                entity.getId(), entity.getPlateNumber(), cmd.getFeeAmount());

        return toVO(entity);
    }

    /**
     * 创建停车记录（ParkingRecord）。
     * <p>
     * 【已废弃】ParkingRecord 创建已统一收敛到 EntryService/ExitService，
     * 本方法保留仅供历史兼容，不再被 exit() 调用。
     */
    @Deprecated
    private ParkingRecord createParkingRecord(ParkingSession session, LocalDateTime exitTime) {
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(session.getTenantId());
        record.setParkingLotId(session.getParkingLotId());
        record.setLaneId(session.getLaneId());
        record.setStandardizedPlate(session.getPlateNumber());
        record.setStatus(ParkingRecord.STATUS_COMPLETED);
        record.setEntryTime(session.getEntryTime());
        record.setExitTime(exitTime);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        parkingRecordMapper.insert(record);
        log.info("停车记录创建成功: recordId={}, plate={}", record.getId(), record.getStandardizedPlate());
        return record;
    }

    /**
     * 创建停车订单（ParkingOrder）。
     * <p>
     * 【已废弃】ParkingOrder 创建已统一收敛到 ParkingOrderService，
     * 本方法保留仅供历史兼容，不再被 exit() 调用。
     */
    @Deprecated
    private ParkingOrder createParkingOrder(ParkingRecord record, int feeCents) {
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(record.getTenantId());
        order.setParkingLotId(record.getParkingLotId());
        order.setParkingRecordId(record.getId());
        order.setOrderNo(generateOrderNo(record.getParkingLotId()));
        order.setOrderType(ParkingOrder.ORDER_TYPE_PARKING);
        order.setPlateNumber(record.getStandardizedPlate());
        order.setAmountCents(Math.max(0, feeCents));
        order.setDiscountAmount(0);
        order.setPointsDiscount(0);
        order.setPayableAmount(Math.max(0, feeCents));
        order.setPaidAmount(0);

        if (feeCents <= 0) {
            order.setStatus(ParkingOrder.STATUS_COMPLETED);
            order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
            order.setPayTime(LocalDateTime.now());
        } else {
            order.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        }

        order.setExpiredAt(LocalDateTime.now().plusMinutes(15));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        parkingOrderMapper.insert(order);
        log.info("停车订单创建成功: orderId={}, orderNo={}, feeCents={}, status={}",
                order.getId(), order.getOrderNo(), feeCents, order.getStatus());
        return order;
    }

    /**
     * 生成订单号：O{lotId}{yyyyMMdd}{6位序号}。
     * 从数据库查询当前最大序号避免重复。
     */
    private String generateOrderNo(Long parkingLotId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = String.format("O%d%s", parkingLotId, date);

        // 查询当天该停车场的最大订单号
        String maxOrderNo = parkingOrderMapper.selectMaxOrderNoByPrefix(prefix);
        int seq = 1;
        if (maxOrderNo != null && maxOrderNo.startsWith(prefix)) {
            try {
                String seqStr = maxOrderNo.substring(prefix.length());
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }

        return String.format("%s%06d", prefix, seq);
    }

    @Override
    public ParkingSessionVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        ParkingSession entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "在场记录不存在");
        }
        if (tenantId != null && !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "在场记录不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<ParkingSessionVO> pageList(IPage<ParkingSession> page, Long parkingLotId, String plateNumber, String status) {
        Long tenantId = TenantContext.getTenantId();

        String normalizedPlate = (plateNumber != null && !plateNumber.isBlank()) ? plateNumber.toUpperCase() : null;

        LambdaQueryWrapper<ParkingSession> wrapper = new LambdaQueryWrapper<ParkingSession>()
                .eq(tenantId != null, ParkingSession::getTenantId, tenantId)
                .eq(parkingLotId != null, ParkingSession::getParkingLotId, parkingLotId)
                .like(normalizedPlate != null, ParkingSession::getPlateNumber, normalizedPlate)
                .eq(status != null && !status.isEmpty(), ParkingSession::getStatus, status)
                .isNull(ParkingSession::getDeletedAt)
                .orderByDesc(ParkingSession::getEntryTime);

        IPage<ParkingSession> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<ParkingSessionVO> listInByParkingLotId(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        List<ParkingSession> list = baseMapper.selectInByParkingLotId(parkingLotId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ParkingSessionVO getInByPlateNumber(String plateNumber) {
        Long tenantId = TenantContext.getTenantId();
        if (plateNumber == null || plateNumber.isBlank()) {
            return null;
        }
        ParkingSession entity = baseMapper.selectInByPlateNumber(plateNumber.toUpperCase(), tenantId);
        return entity != null ? toVO(entity) : null;
    }

    @Override
    public ParkingSessionVO getInByPlateAndLot(String plateNumber, Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        if (plateNumber == null || plateNumber.isBlank()) {
            return null;
        }
        if (tenantId != null) {
            ParkingSession entity = baseMapper.selectInByPlateAndLot(plateNumber.toUpperCase(), parkingLotId, tenantId);
            return entity != null ? toVO(entity) : null;
        }
        // MQ 消费者等无租户上下文场景，使用 LambdaQueryWrapper 查询
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingSession> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingSession>()
                .eq(ParkingSession::getPlateNumber, plateNumber.toUpperCase())
                .eq(ParkingSession::getParkingLotId, parkingLotId)
                .eq(ParkingSession::getStatus, ParkingSession.STATUS_IN)
                .isNull(ParkingSession::getDeletedAt)
                .orderByDesc(ParkingSession::getEntryTime)
                .last("LIMIT 1");
        ParkingSession entity = baseMapper.selectOne(wrapper);
        return entity != null ? toVO(entity) : null;
    }

    @Override
    public long countInByParkingLotId(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        return baseMapper.countInByParkingLotId(parkingLotId, tenantId);
    }

    @Override
    public long countInByParkingLotIdIgnoreTenant(Long parkingLotId) {
        return baseMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingSession>()
                        .eq(ParkingSession::getParkingLotId, parkingLotId)
                        .eq(ParkingSession::getStatus, ParkingSession.STATUS_IN)
                        .isNull(ParkingSession::getDeletedAt));
    }

    @Override
    public ParkingSessionVO getRecentOutByPlateAndLot(String plateNumber, Long parkingLotId, int withinSeconds) {
        if (plateNumber == null || plateNumber.isBlank() || parkingLotId == null) {
            return null;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(withinSeconds);
        ParkingSession entity = baseMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingSession>()
                        .eq(ParkingSession::getPlateNumber, plateNumber.toUpperCase())
                        .eq(ParkingSession::getParkingLotId, parkingLotId)
                        .eq(ParkingSession::getStatus, ParkingSession.STATUS_OUT)
                        .isNull(ParkingSession::getDeletedAt)
                        .ge(ParkingSession::getExitTime, threshold)
                        .orderByDesc(ParkingSession::getExitTime)
                        .last("LIMIT 1"));
        return entity != null ? toVO(entity) : null;
    }

    private ParkingSessionVO toVO(ParkingSession entity) {
        ParkingSessionVO vo = new ParkingSessionVO();
        BeanUtils.copyProperties(entity, vo);

        // 计算在场时长
        if (entity.getEntryTime() != null) {
            LocalDateTime endTime = entity.getExitTime() != null ? entity.getExitTime() : LocalDateTime.now();
            vo.setDurationMinutes(ChronoUnit.MINUTES.between(entity.getEntryTime(), endTime));
        }

        // 费用精度安全：feeAmount（元）→ feeCents（分）
        if (entity.getFeeAmount() != null) {
            vo.setFeeCents(entity.getFeeAmount().movePointRight(2).intValue());
        } else {
            vo.setFeeCents(0);
        }

        // 车道名称
        if (entity.getLaneId() != null) {
            try {
                ParkingLane lane = parkingLaneMapper.selectById(entity.getLaneId());
                if (lane != null) vo.setEntryLaneName(lane.getName());
            } catch (Exception ignored) {}
        }
        if (entity.getExitLaneId() != null) {
            try {
                ParkingLane lane = parkingLaneMapper.selectById(entity.getExitLaneId());
                if (lane != null) vo.setExitLaneName(lane.getName());
            } catch (Exception ignored) {}
        }

        return vo;
    }
}
