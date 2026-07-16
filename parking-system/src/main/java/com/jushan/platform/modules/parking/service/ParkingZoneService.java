package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.modules.parking.dto.ParkingZoneCreateCmd;
import com.jushan.platform.modules.parking.dto.ParkingZoneUpdateCmd;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.entity.ParkingZone;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.parking.mapper.ParkingZoneMapper;
import com.jushan.platform.modules.parking.vo.ParkingZoneVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 区域管理服务实现。
 * <p>
 * 实现区域 CRUD、状态管理、临停车位自动计算。
 * 禁用区域后，关联通道自动暂停。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class ParkingZoneService extends ServiceImpl<ParkingZoneMapper, ParkingZone> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 2;

    private final ParkingLaneMapper parkingLaneMapper;

    public ParkingZoneService(ParkingLaneMapper parkingLaneMapper) {
        this.parkingLaneMapper = parkingLaneMapper;
    }

    /**
     * 解析当前租户ID，正确处理平台用户。
     * <p>
     * 平台用户（super_admin、platform_operator）无租户绑定，返回 null。
     * 租户用户返回其 tenantId。
     */
    private Long resolveTenantId() {
        if (TenantContext.isPlatformUser()) {
            return null;
        }
        return TenantContext.requireTenantId();
    }

    /**
     * 创建区域（DTO 入口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingZoneVO create(ParkingZoneCreateCmd cmd) {
        ParkingZone zone = new ParkingZone();
        zone.setLotId(cmd.getLotId());
        zone.setName(cmd.getName());
        zone.setTag(cmd.getTag());
        zone.setLevel(cmd.getLevel());
        zone.setFeeRuleId(cmd.getFeeRuleId());
        zone.setTotalSpaces(cmd.getTotalSpaces());
        zone.setFixedSpaces(cmd.getFixedSpaces());
        zone.setStatus(cmd.getStatus());
        zone.setManagerId(cmd.getManagerId());
        zone.setRemark(cmd.getRemark());
        save(zone);
        return detail(zone.getId());
    }

    /**
     * 更新区域（DTO 入口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ParkingZoneUpdateCmd cmd) {
        ParkingZone zone = new ParkingZone();
        zone.setId(id);
        zone.setLotId(cmd.getLotId());
        zone.setName(cmd.getName());
        zone.setTag(cmd.getTag());
        zone.setLevel(cmd.getLevel());
        zone.setFeeRuleId(cmd.getFeeRuleId());
        zone.setTotalSpaces(cmd.getTotalSpaces());
        zone.setFixedSpaces(cmd.getFixedSpaces());
        zone.setStatus(cmd.getStatus());
        zone.setManagerId(cmd.getManagerId());
        zone.setRemark(cmd.getRemark());
        updateById(zone);
    }

    // ==================== 创建区域 ====================

    /**
     * 创建区域。
     * <p>
     * 自动计算 tempSpaces = totalSpaces - fixedSpaces。
     * 同步更新停车场总车位数。
     *
     * @param zone 区域实体
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "区域管理", value = "创建区域")
    public boolean save(ParkingZone zone) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后创建区域");
        }

        // 校验车位数
        validateSpaces(zone);

        zone.setTenantId(tenantId);
        zone.setTempSpaces(zone.getTotalSpaces() - zone.getFixedSpaces());
        zone.setStatus(STATUS_ENABLED);
        zone.setVersion(0);
        zone.setCreatedAt(LocalDateTime.now());
        zone.setUpdatedAt(LocalDateTime.now());

        return super.save(zone);
    }

    // ==================== 更新区域 ====================

    /**
     * 更新区域。
     * <p>
     * 更新时重新计算 tempSpaces，同步更新停车场总车位数。
     *
     * @param zone 区域实体
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "区域管理", value = "更新区域")
    public boolean updateById(ParkingZone zone) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后更新区域");
        }
        ParkingZone existing = getAndCheck(zone.getId(), tenantId);

        // 校验车位数
        int totalSpaces = zone.getTotalSpaces() != null ? zone.getTotalSpaces() : existing.getTotalSpaces();
        int fixedSpaces = zone.getFixedSpaces() != null ? zone.getFixedSpaces() : existing.getFixedSpaces();
        validateSpaces(totalSpaces, fixedSpaces);

        zone.setTempSpaces(totalSpaces - fixedSpaces);
        zone.setUpdatedAt(LocalDateTime.now());

        return super.updateById(zone);
    }

    // ==================== 删除区域 ====================

    /**
     * 软删除区域。
     * <p>
     * 校验无关联通道后才允许删除。
     *
     * @param id 区域 ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "区域管理", value = "删除区域")
    public boolean removeById(Long id) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后删除区域");
        }
        ParkingZone zone = getAndCheck(id, tenantId);

        // 校验是否存在关联通道
        long laneCount = parkingLaneMapper.selectCount(
                new LambdaQueryWrapper<ParkingLane>()
                        .eq(ParkingLane::getZoneId, id)
                        .eq(ParkingLane::getDeletedAt, 0));
        if (laneCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该区域下存在关联通道，请先删除通道");
        }

        return super.removeById(id);
    }

    // ==================== 查询 ====================

    /**
     * 查询区域详情。
     *
     * @param id 区域 ID
     * @return 区域实体
     */
    public ParkingZoneVO detail(Long id) {
        Long tenantId = resolveTenantId();
        return toVO(getAndCheck(id, tenantId));
    }

    /**
     * 按车场查询区域列表。
     *
     * @param lotId 车场 ID
     * @return 区域列表
     */
    public List<ParkingZoneVO> listByLotId(Long lotId) {
        Long tenantId = resolveTenantId();
        return baseMapper.selectList(
                new LambdaQueryWrapper<ParkingZone>()
                        .eq(tenantId != null, ParkingZone::getTenantId, tenantId)
                        .eq(ParkingZone::getLotId, lotId)
                        .orderByAsc(ParkingZone::getLevel))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 分页查询区域列表。
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param lotId   车场 ID（可选）
     * @param status  状态筛选（可选）
     * @return 分页结果
     */
    public IPage<ParkingZoneVO> pageList(long current, long size, Long lotId, Integer status) {
        Long tenantId = resolveTenantId();
        LambdaQueryWrapper<ParkingZone> wrapper = new LambdaQueryWrapper<ParkingZone>()
                .eq(tenantId != null, ParkingZone::getTenantId, tenantId)
                .orderByDesc(ParkingZone::getCreatedAt);

        if (lotId != null) {
            wrapper.eq(ParkingZone::getLotId, lotId);
        }
        if (status != null) {
            wrapper.eq(ParkingZone::getStatus, status);
        }

        IPage<ParkingZone> entityPage = baseMapper.selectPage(new Page<>(current, size), wrapper);
        List<ParkingZoneVO> records = entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        Page<ParkingZoneVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    // ==================== 状态管理 ====================

    /**
     * 更新区域状态。
     * <p>
     * 禁用时，关联通道自动更新为维护中状态。
     *
     * @param id     区域 ID
     * @param status 新状态（1启用 2禁用）
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "区域管理", value = "更新区域状态")
    public void updateStatus(Long id, Integer status) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后变更区域状态");
        }
        ParkingZone zone = getAndCheck(id, tenantId);

        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的状态值: " + status);
        }

        if (Objects.equals(zone.getStatus(), status)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "区域已是该状态");
        }

        LambdaUpdateWrapper<ParkingZone> wrapper = new LambdaUpdateWrapper<ParkingZone>()
                .set(ParkingZone::getStatus, status)
                .set(ParkingZone::getUpdatedAt, LocalDateTime.now())
                .eq(ParkingZone::getId, id)
                .eq(ParkingZone::getVersion, zone.getVersion());

        boolean updated = baseMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "区域状态已变更，请刷新后重试");
        }

        // 禁用时，关联通道更新为维护中（状态 3）
        if (status == STATUS_DISABLED) {
            LambdaUpdateWrapper<ParkingLane> laneWrapper = new LambdaUpdateWrapper<ParkingLane>()
                    .set(ParkingLane::getStatus, 3) // 维护中
                    .set(ParkingLane::getUpdatedAt, LocalDateTime.now())
                    .eq(ParkingLane::getZoneId, id)
                    .eq(ParkingLane::getStatus, 1); // 仅更新原启用状态的通道
            int affectedLanes = parkingLaneMapper.update(null, laneWrapper);
            log.info("禁用区域后联动通道: zoneId={}, 受影响通道数={}", id, affectedLanes);
        }

        log.info("更新区域状态成功: zoneId={}, {} -> {}", id, zone.getStatus(), status);
    }

    // ==================== 内部方法 ====================

    /**
     * 根据ID查询并校验租户归属。
     */
    private ParkingZone getAndCheck(Long id, Long tenantId) {
        ParkingZone zone = baseMapper.selectById(id);
        if (zone == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "区域不存在");
        }
        // 平台用户可访问所有租户数据
        if (tenantId == null) {
            return zone;
        }
        if (!Objects.equals(tenantId, zone.getTenantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问其他租户数据");
        }
        return zone;
    }

    /**
     * 校验车位数合法性。
     */
    private void validateSpaces(ParkingZone zone) {
        validateSpaces(zone.getTotalSpaces(), zone.getFixedSpaces());
    }

    private void validateSpaces(int totalSpaces, int fixedSpaces) {
        if (totalSpaces < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车位总数不能为负数");
        }
        if (fixedSpaces < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "固定车位数不能为负数");
        }
        if (fixedSpaces > totalSpaces) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "固定车位数不能大于车位总数");
        }
    }

    private ParkingZoneVO toVO(ParkingZone zone) {
        ParkingZoneVO vo = new ParkingZoneVO();
        vo.setId(zone.getId());
        vo.setTenantId(zone.getTenantId());
        vo.setLotId(zone.getLotId());
        vo.setName(zone.getName());
        vo.setTag(zone.getTag());
        vo.setLevel(zone.getLevel());
        vo.setFeeRuleId(zone.getFeeRuleId());
        vo.setTotalSpaces(zone.getTotalSpaces());
        vo.setFixedSpaces(zone.getFixedSpaces());
        vo.setTempSpaces(zone.getTempSpaces());
        vo.setStatus(zone.getStatus());
        vo.setManagerId(zone.getManagerId());
        vo.setRemark(zone.getRemark());
        vo.setVersion(zone.getVersion());
        vo.setCreatedAt(zone.getCreatedAt());
        vo.setUpdatedAt(zone.getUpdatedAt());
        return vo;
    }
}
