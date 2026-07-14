package com.jushan.platform.modules.miniapp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.miniapp.service.MiniUserService;
import com.jushan.platform.modules.miniapp.vo.MiniParkingRecordVO;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.parking.service.ParkingSpacePolicyService;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序车主服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class MiniUserServiceImpl implements MiniUserService {

    private final ParkingSessionMapper parkingSessionMapper;
    private final ParkingSpacePolicyService parkingSpacePolicyService;

    public MiniUserServiceImpl(ParkingSessionMapper parkingSessionMapper,
                               ParkingSpacePolicyService parkingSpacePolicyService) {
        this.parkingSessionMapper = parkingSessionMapper;
        this.parkingSpacePolicyService = parkingSpacePolicyService;
    }

    @Override
    public IPage<MiniParkingRecordVO> listParkingRecords(long current, long size) {
        // TODO: 通过 plate_binding 关联查询当前用户的所有车牌，再查询停车记录
        // 当前简化实现：查询所有记录
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<ParkingSession> wrapper = new LambdaQueryWrapper<ParkingSession>()
                .eq(ParkingSession::getTenantId, tenantId)
                .isNull(ParkingSession::getDeletedAt)
                .orderByDesc(ParkingSession::getEntryTime);

        IPage<ParkingSession> entityPage = parkingSessionMapper.selectPage(new Page<>(current, size), wrapper);
        return entityPage.convert(this::toMiniVO);
    }

    @Override
    public IPage<MiniParkingRecordVO> listParkingRecordsByPlate(String plateNumber, long current, long size) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<ParkingSession> wrapper = new LambdaQueryWrapper<ParkingSession>()
                .eq(ParkingSession::getTenantId, tenantId)
                .eq(ParkingSession::getPlateNumber, plateNumber.toUpperCase())
                .isNull(ParkingSession::getDeletedAt)
                .orderByDesc(ParkingSession::getEntryTime);

        IPage<ParkingSession> entityPage = parkingSessionMapper.selectPage(new Page<>(current, size), wrapper);
        return entityPage.convert(this::toMiniVO);
    }

    @Override
    public List<MiniParkingRecordVO> listCurrentSessions() {
        Long tenantId = TenantContext.getTenantId();
        // TODO: 通过 plate_binding 关联查询当前用户的所有车牌，再查询在场记录
        List<ParkingSession> list = parkingSessionMapper.selectInByParkingLotId(null, tenantId);
        return list.stream().map(this::toMiniVO).collect(Collectors.toList());
    }

    @Override
    public ParkingSpaceRemainVO getParkingLotRemain(Long parkingLotId) {
        return parkingSpacePolicyService.calculateRemain(parkingLotId);
    }

    @Override
    public List<String> listBoundPlates() {
        // TODO: 从 wx_user / plate_binding 查询当前用户绑定的车牌
        // 当前返回空列表，待后续实现
        return List.of();
    }

    private MiniParkingRecordVO toMiniVO(ParkingSession entity) {
        MiniParkingRecordVO vo = new MiniParkingRecordVO();
        BeanUtils.copyProperties(entity, vo);

        // 计算在场时长
        if (entity.getEntryTime() != null) {
            java.time.LocalDateTime endTime = entity.getExitTime() != null ? entity.getExitTime() : java.time.LocalDateTime.now();
            vo.setDurationMinutes(ChronoUnit.MINUTES.between(entity.getEntryTime(), endTime));
        }

        // 支付状态
        if (ParkingSession.STATUS_IN.equals(entity.getStatus())) {
            vo.setPayStatus("IN_PROGRESS");
        } else if (entity.getFeeAmount() != null && entity.getFeeAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            vo.setPayStatus(entity.getPaidAmount() != null && entity.getPaidAmount().compareTo(entity.getFeeAmount()) >= 0 ? "PAID" : "UNPAID");
        } else {
            vo.setPayStatus("FREE");
        }

        // 停车场名称（TODO: 查询停车场名称）
        vo.setParkingLotName("");

        return vo;
    }
}
