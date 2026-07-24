package com.jushan.platform.modules.vehicle.service;
import com.jushan.platform.modules.common.service.ParamResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.common.cache.VehicleListCacheStore;
import com.jushan.platform.modules.vehicle.dto.VehicleListCreateCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleListUpdateCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleListPageQuery;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.vehicle.entity.VehicleList;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.vehicle.mapper.VehicleListMapper;
import com.jushan.platform.modules.vehicle.vo.VehicleListDecisionVO;
import com.jushan.platform.modules.vehicle.vo.VehicleListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 车辆黑白名单业务服务。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Slf4j
@Service
public class VehicleListService extends ServiceImpl<VehicleListMapper, VehicleList> {

    private final VehicleListMapper vehicleListMapper;
    private final VehicleListCacheStore cacheStore;
    private final ParkingLotMapper parkingLotMapper;

    static final Map<String, String> TRIGGER_TYPE_LABEL_MAP = new LinkedHashMap<>();
    static final Map<String, String> LIST_TYPE_LABEL_MAP = new LinkedHashMap<>();
    static final Map<String, String> STATUS_LABEL_MAP = new LinkedHashMap<>();

    static {
        TRIGGER_TYPE_LABEL_MAP.put(VehicleList.TRIGGER_ARREARS, "欠费类");
        TRIGGER_TYPE_LABEL_MAP.put(VehicleList.TRIGGER_MANAGEMENT, "管理类");
        TRIGGER_TYPE_LABEL_MAP.put(VehicleList.TRIGGER_OTHER, "其他类");

        LIST_TYPE_LABEL_MAP.put(VehicleList.TYPE_BLACK, "黑名单");
        LIST_TYPE_LABEL_MAP.put(VehicleList.TYPE_WHITE, "白名单");

        STATUS_LABEL_MAP.put(VehicleList.STATUS_ACTIVE, "生效中");
        STATUS_LABEL_MAP.put(VehicleList.STATUS_EXPIRED, "已过期");
        STATUS_LABEL_MAP.put(VehicleList.STATUS_DISABLED, "已禁用");
    }

    public VehicleListService(VehicleListMapper vehicleListMapper, VehicleListCacheStore cacheStore, ParkingLotMapper parkingLotMapper) {
        this.vehicleListMapper = vehicleListMapper;
        this.cacheStore = cacheStore;
        this.parkingLotMapper = parkingLotMapper;
        // 确保 baseMapper 指向同一实例，使 MyBatis-Plus 的 insert/update/delete 方法可被 Mockito 拦截
        this.baseMapper = vehicleListMapper;
    }

    @Transactional
    public VehicleList create(VehicleListCreateCmd cmd) {
        String plate = cmd.getPlateNumber().toUpperCase();
        Long lotId = cmd.getParkingLotId();
        String listType = cmd.getListType();

        // 互斥校验
        String oppositeType = VehicleList.TYPE_BLACK.equals(listType)
                ? VehicleList.TYPE_WHITE : VehicleList.TYPE_BLACK;
        VehicleList conflict = vehicleListMapper.selectByLotAndPlateAndType(lotId, plate, oppositeType);
        if (conflict != null) {
            String oppositeLabel = LIST_TYPE_LABEL_MAP.getOrDefault(oppositeType, oppositeType);
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "该车牌已存在于" + oppositeLabel + "中，无法同时添加为" + getListTypeLabel(listType));
        }

        // 黑名单必填 triggerType
        if (VehicleList.TYPE_BLACK.equals(listType)
                && (cmd.getTriggerType() == null || cmd.getTriggerType().isBlank())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "黑名单触发类型不能为空");
        }

        // 有效期校验
        if (cmd.getStartDate() != null && cmd.getEndDate() != null
                && cmd.getStartDate().isAfter(cmd.getEndDate())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "有效期开始时间不能晚于结束时间");
        }

        VehicleList entity = new VehicleList();
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        Long tenantId = lot != null ? lot.getTenantId() : TenantContext.getTenantId();
        entity.setTenantId(tenantId);
        entity.setPlateNumber(plate);
        entity.setListType(listType);
        entity.setParkingLotId(lotId);
        entity.setStartDate(cmd.getStartDate());
        entity.setEndDate(cmd.getEndDate());
        entity.setTriggerType(VehicleList.TYPE_WHITE.equals(listType) ? null : cmd.getTriggerType());
        entity.setStatus(VehicleList.STATUS_ACTIVE);
        entity.setRemark(cmd.getRemark());

        baseMapper.insert(entity);
        cacheStore.evict(lotId, plate);
        log.info("名单已创建: id={} plate={} type={} lotId={}", entity.getId(), plate, listType, lotId);
        return entity;
    }

    @Transactional
    public VehicleList update(Long id, VehicleListUpdateCmd cmd) {
        VehicleList entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "名单记录不存在");
        }

        String plate = cmd.getPlateNumber().toUpperCase();
        Long lotId = cmd.getParkingLotId() != null ? cmd.getParkingLotId() : entity.getParkingLotId();
        String listType = cmd.getListType() != null ? cmd.getListType() : entity.getListType();

        // 互斥校验
        String oppositeType = VehicleList.TYPE_BLACK.equals(listType)
                ? VehicleList.TYPE_WHITE : VehicleList.TYPE_BLACK;
        VehicleList conflict = vehicleListMapper.selectByLotAndPlateAndType(lotId, plate, oppositeType);
        if (conflict != null && !conflict.getId().equals(id)) {
            String oppositeLabel = LIST_TYPE_LABEL_MAP.getOrDefault(oppositeType, oppositeType);
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "该车牌已存在于" + oppositeLabel + "中，无法同时设置为" + getListTypeLabel(listType));
        }

        if (cmd.getStartDate() != null && cmd.getEndDate() != null
                && cmd.getStartDate().isAfter(cmd.getEndDate())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "有效期开始时间不能晚于结束时间");
        }

        entity.setPlateNumber(plate);
        entity.setListType(listType);
        if (cmd.getParkingLotId() != null) {
            entity.setParkingLotId(cmd.getParkingLotId());
        }
        if (cmd.getStartDate() != null) entity.setStartDate(cmd.getStartDate());
        if (cmd.getEndDate() != null) entity.setEndDate(cmd.getEndDate());
        if (VehicleList.TYPE_WHITE.equals(listType)) {
            entity.setTriggerType(null);
        } else if (cmd.getTriggerType() != null) {
            entity.setTriggerType(cmd.getTriggerType());
        }
        if (cmd.getRemark() != null) entity.setRemark(cmd.getRemark());

        baseMapper.updateById(entity);
        cacheStore.evict(entity.getParkingLotId(), entity.getPlateNumber());
        if (!plate.equals(entity.getPlateNumber())) {
            cacheStore.evict(entity.getParkingLotId(), plate);
        }
        log.info("名单已更新: id={} plate={} type={}", id, plate, listType);
        return entity;
    }

    @Transactional
    public void delete(Long id) {
        VehicleList entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "名单记录不存在");
        }
        baseMapper.deleteById(id);
        cacheStore.evict(entity.getParkingLotId(), entity.getPlateNumber());
        log.info("名单已删除: id={} plate={}", id, entity.getPlateNumber());
    }

    public IPage<VehicleListVO> pageList(VehicleListPageQuery query) {
        Page<VehicleList> page = new Page<>(query.getPage(), query.getSize());
        String plate = query.getPlateNumber();
        LambdaQueryWrapper<VehicleList> wrapper = new LambdaQueryWrapper<VehicleList>()
                .eq(query.getParkingLotId() != null, VehicleList::getParkingLotId, query.getParkingLotId())
                .eq(query.getListType() != null && !query.getListType().isEmpty(),
                        VehicleList::getListType, query.getListType())
                .eq(plate != null && !plate.isEmpty(),
                        VehicleList::getPlateNumber, plate == null ? null : plate.toUpperCase())
                .orderByDesc(VehicleList::getCreatedAt);

        IPage<VehicleList> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    public VehicleListVO detail(Long id) {
        VehicleList entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "名单记录不存在");
        }
        return toVO(entity);
    }

    // ==================== 判定方法 ====================

    public boolean isWhitelisted(Long parkingLotId, String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return false;
        }
        String cached = cacheStore.get(parkingLotId, plateNumber);
        if (cached != null) {
            if (VehicleListCacheStore.NULL_MARKER.equals(cached)) return false;
            return cached.startsWith("WHITE");
        }
        VehicleList entity = vehicleListMapper.selectActiveWhite(parkingLotId, plateNumber.toUpperCase());
        if (entity != null && isInDateRange(entity)) {
            cacheStore.put(parkingLotId, plateNumber, "WHITE");
            return true;
        }
        cacheStore.put(parkingLotId, plateNumber, VehicleListCacheStore.NULL_MARKER);
        return false;
    }

    public boolean isBlacklisted(Long parkingLotId, String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return false;
        }
        return resolveBlacklist(parkingLotId, plateNumber) != null;
    }

    public VehicleList resolveBlacklist(Long parkingLotId, String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return null;
        }
        String cached = cacheStore.get(parkingLotId, plateNumber);
        if (cached != null) {
            if (VehicleListCacheStore.NULL_MARKER.equals(cached)) return null;
            if (cached.startsWith("BLACK")) {
                VehicleList entity = vehicleListMapper.selectActiveBlack(parkingLotId, plateNumber.toUpperCase());
                if (entity != null && isInDateRange(entity)) return entity;
            }
            return null;
        }
        VehicleList entity = vehicleListMapper.selectActiveBlack(parkingLotId, plateNumber.toUpperCase());
        if (entity != null && isInDateRange(entity)) {
            cacheStore.put(parkingLotId, plateNumber, "BLACK:" + entity.getTriggerType());
            return entity;
        }
        cacheStore.put(parkingLotId, plateNumber, VehicleListCacheStore.NULL_MARKER);
        return null;
    }

    public VehicleListDecisionVO checkEntry(Long parkingLotId, String plateNumber) {
        VehicleListDecisionVO decision = new VehicleListDecisionVO();
        decision.setDenyEntry(false);
        decision.setAlert(false);

        if (isWhitelisted(parkingLotId, plateNumber)) {
            decision.setListType(VehicleList.TYPE_WHITE);
            decision.setReason("白名单车辆，自动放行");
            return decision;
        }

        VehicleList black = resolveBlacklist(parkingLotId, plateNumber);
        if (black == null) {
            return decision;
        }

        decision.setListType(VehicleList.TYPE_BLACK);
        decision.setTriggerType(black.getTriggerType());

        // triggerMode: default DENY_ENTRY (will be connected to ParamResolver in later integration)
        String triggerMode = "DENY_ENTRY";

        switch (triggerMode) {
            case "DENY_ENTRY":
                decision.setDenyEntry(true);
                decision.setReason("黑名单车辆，禁止入场");
                break;
            case "ALLOW_WITH_ALERT":
                decision.setAlert(true);
                decision.setReason("黑名单车辆，允许入场但已触发告警");
                break;
            case "BY_TYPE":
                if (VehicleList.TRIGGER_ARREARS.equals(black.getTriggerType())) {
                    decision.setDenyEntry(true);
                    decision.setReason("欠费类黑名单车辆，禁止入场");
                } else if (VehicleList.TRIGGER_MANAGEMENT.equals(black.getTriggerType())) {
                    decision.setAlert(true);
                    decision.setReason("管理类黑名单车辆，允许入场但已触发告警");
                } else {
                    decision.setDenyEntry(true);
                    decision.setReason("其他类黑名单车辆，禁止入场");
                }
                break;
            default:
                decision.setDenyEntry(true);
                decision.setReason("黑名单车辆，禁止入场（默认策略）");
        }

        return decision;
    }

    private boolean isInDateRange(VehicleList entity) {
        LocalDate now = LocalDate.now();
        if (entity.getStartDate() != null && now.isBefore(entity.getStartDate())) return false;
        if (entity.getEndDate() != null && now.isAfter(entity.getEndDate())) return false;
        return true;
    }

    public VehicleListVO toVO(VehicleList entity) {
        VehicleListVO vo = new VehicleListVO();
        vo.setId(entity.getId());
        vo.setPlateNumber(entity.getPlateNumber());
        vo.setListType(entity.getListType());
        vo.setListTypeLabel(LIST_TYPE_LABEL_MAP.getOrDefault(entity.getListType(), entity.getListType()));
        vo.setParkingLotId(entity.getParkingLotId());
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setTriggerType(entity.getTriggerType());
        vo.setTriggerTypeLabel(TRIGGER_TYPE_LABEL_MAP.getOrDefault(entity.getTriggerType(), ""));
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(STATUS_LABEL_MAP.getOrDefault(entity.getStatus(), entity.getStatus()));
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String getListTypeLabel(String listType) {
        return LIST_TYPE_LABEL_MAP.getOrDefault(listType, listType);
    }
}
