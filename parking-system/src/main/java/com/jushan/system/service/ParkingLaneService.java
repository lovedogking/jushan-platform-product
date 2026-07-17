package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;

import com.jushan.system.dto.CreateLaneRequest;
import com.jushan.system.dto.UpdateLaneRequest;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.DeviceModel;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceModelMapper;
import com.jushan.system.mapper.DeviceVendorMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.vo.DeviceVO;
import com.jushan.system.vo.ParkingLaneVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 车道服务。
 * <p>
 * 负责车道 CRUD、状态管理和自动放行策略配置。
 * 所有操作从当前登录会话推导租户范围，并通过停车场归属验证数据隔离。
 * <p>
 * 前端 API 使用 String 枚举方向（ENTRY/EXIT/MIXED）和状态（ENABLED/DISABLED），
 * Service 层负责与 DB Integer 字段（type/status）间的双向转换。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ParkingLaneService {

    private static final Logger log = LoggerFactory.getLogger(ParkingLaneService.class);

    /** 车道方向枚举（前端合约） */
    private static final String DIR_ENTRY = "ENTRY";
    private static final String DIR_EXIT = "EXIT";
    private static final String DIR_MIXED = "MIXED";

    /** DB type 值：1=入口, 2=出口, 3=双向 */
    private static final int TYPE_ENTRY = 1;
    private static final int TYPE_EXIT = 2;
    private static final int TYPE_MIXED = 3;

    /** 车道状态枚举（前端合约） */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** DB status 值：1=启用, 2=禁用, 3=维护中 */
    private static final int DB_STATUS_ENABLED = 1;
    private static final int DB_STATUS_DISABLED = 2;

    private final ParkingLaneMapper laneMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceVendorMapper vendorMapper;
    private final DeviceModelMapper modelMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public ParkingLaneService(ParkingLaneMapper laneMapper, ParkingLotMapper parkingLotMapper,
                               DeviceMapper deviceMapper, DeviceVendorMapper vendorMapper,
                               DeviceModelMapper modelMapper,
                               ParkingLotScopeResolver scopeResolver) {
        this.laneMapper = laneMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.deviceMapper = deviceMapper;
        this.vendorMapper = vendorMapper;
        this.modelMapper = modelMapper;
        this.scopeResolver = scopeResolver;
    }

    // ==================== 方向/状态转换工具 ====================

    /**
     * 前端方向 String → DB type Integer。
     *
     * @throws BusinessException 无效方向时抛出
     */
    public static Integer directionToInt(String direction) {
        if (direction == null || direction.isBlank()) return null;
        return switch (direction.toUpperCase()) {
            case DIR_ENTRY -> TYPE_ENTRY;
            case DIR_EXIT -> TYPE_EXIT;
            case DIR_MIXED -> TYPE_MIXED;
            default -> throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的车道方向: " + direction + "，仅支持 ENTRY / EXIT / MIXED");
        };
    }

    /**
     * DB type Integer → 前端方向 String。
     */
    public static String intToDirectionStr(Integer type) {
        if (type == null) return null;
        return switch (type) {
            case TYPE_ENTRY -> DIR_ENTRY;
            case TYPE_EXIT -> DIR_EXIT;
            case TYPE_MIXED -> DIR_MIXED;
            default -> null;
        };
    }

    /**
     * 前端状态 String → DB status Integer。
     *
     * @throws BusinessException 无效状态时抛出
     */
    public static Integer statusToInt(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status.toUpperCase()) {
            case STATUS_ENABLED -> DB_STATUS_ENABLED;
            case STATUS_DISABLED -> DB_STATUS_DISABLED;
            default -> throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的车道状态: " + status + "，仅支持 ENABLED / DISABLED");
        };
    }

    /**
     * DB status Integer → 前端状态 String。
     */
    public static String intToStatusStr(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case DB_STATUS_ENABLED -> STATUS_ENABLED;
            case DB_STATUS_DISABLED -> STATUS_DISABLED;
            default -> null;
        };
    }

    // ==================== 创建车道 ====================

    /**
     * 创建车道。
     * <p>
     * 校验停车场归属、车道编码停车场内唯一、方向合法性。
     *
     * @param request 创建请求
     * @return 车道视图
     */
    @Transactional
    public ParkingLaneVO create(CreateLaneRequest request) {
        // 1. 校验停车场归属
        getParkingLotWithAuth(request.getParkingLotId());

        // 2. 校验方向
        Integer type = directionToInt(request.getDirection());

        // 3. 校验编码在停车场内唯一
        String laneNo = request.getCode().trim();
        Long existingCount = laneMapper.selectCount(
                new LambdaQueryWrapper<ParkingLane>()
                        .eq(ParkingLane::getLotId, request.getParkingLotId())
                        .eq(ParkingLane::getLaneNo, laneNo));
        if (existingCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "车道编码 '" + laneNo + "' 在本停车场内已存在");
        }

        ParkingLane lane = new ParkingLane();
        lane.setLotId(request.getParkingLotId());
        lane.setName(request.getName().trim());
        lane.setLaneNo(laneNo);
        lane.setType(type);
        lane.setStatus(DB_STATUS_ENABLED);
        lane.setCreatedAt(LocalDateTime.now());
        lane.setUpdatedAt(LocalDateTime.now());
        laneMapper.insert(lane);

        log.info("创建车道成功: lotId={}, laneId={}, name={}, laneNo={}, type={}",
                request.getParkingLotId(), lane.getId(), lane.getName(), laneNo, type);

        return toVO(lane);
    }

    // ==================== 更新车道 ====================

    /**
     * 更新车道基础信息（部分更新）。
     *
     * @param laneId  车道 ID
     * @param request 更新请求（仅非 null 字段被更新）
     * @return 车道视图
     */
    @Transactional
    public ParkingLaneVO update(Long laneId, UpdateLaneRequest request) {
        ParkingLane lane = getLaneWithAuth(laneId);

        LambdaUpdateWrapper<ParkingLane> wrapper = new LambdaUpdateWrapper<ParkingLane>()
                .eq(ParkingLane::getId, laneId);

        boolean hasUpdate = false;

        if (request.getName() != null) {
            wrapper.set(ParkingLane::getName, request.getName().trim());
            hasUpdate = true;
        }
        if (request.getCode() != null) {
            String newLaneNo = request.getCode().trim();
            // 编码变更时检查唯一性（排除自身）
            Long existingCount = laneMapper.selectCount(
                    new LambdaQueryWrapper<ParkingLane>()
                            .eq(ParkingLane::getLotId, lane.getLotId())
                            .eq(ParkingLane::getLaneNo, newLaneNo)
                            .ne(ParkingLane::getId, laneId));
            if (existingCount > 0) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "车道编码 '" + newLaneNo + "' 在本停车场内已存在");
            }
            wrapper.set(ParkingLane::getLaneNo, newLaneNo);
            hasUpdate = true;
        }
        if (request.getDirection() != null) {
            Integer newType = directionToInt(request.getDirection());
            wrapper.set(ParkingLane::getType, newType);
            hasUpdate = true;
        }

        if (!hasUpdate) {
            return toVO(lane);
        }

        wrapper.set(ParkingLane::getUpdatedAt, LocalDateTime.now());
        laneMapper.update(null, wrapper);

        ParkingLane updated = laneMapper.selectByIdIgnoreTenant(laneId);
        log.info("更新车道成功: laneId={}", laneId);
        return toVO(updated);
    }

    // ==================== 车道查询 ====================

    /**
     * 分页查询车道列表。
     * <p>
     * 按停车场筛选，租户内数据隔离。
     * 平台用户可跨租户查看。
     *
     * @param page         页码
     * @param size         每页大小
     * @param parkingLotId 停车场 ID（必填，用于限定范围）
     * @param status       状态筛选（可选：ENABLED / DISABLED）
     * @param direction    方向筛选（可选：ENTRY / EXIT / MIXED）
     * @return 分页结果
     */
    public IPage<ParkingLaneVO> list(int page, int size, Long parkingLotId, String status, String direction) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }

        // 校验停车场归属（租户隔离）
        getParkingLotWithAuth(parkingLotId);

        Integer typeFilter = directionToInt(direction);
        Integer statusFilter = statusToInt(status);

        LambdaQueryWrapper<ParkingLane> wrapper = new LambdaQueryWrapper<ParkingLane>()
                .eq(ParkingLane::getLotId, parkingLotId)
                .eq(statusFilter != null, ParkingLane::getStatus, statusFilter)
                .eq(typeFilter != null, ParkingLane::getType, typeFilter)
                .orderByDesc(ParkingLane::getCreatedAt);

        IPage<ParkingLane> lanePage = laneMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查询绑定设备
        List<Long> laneIds = lanePage.getRecords().stream()
                .map(ParkingLane::getId).collect(Collectors.toList());
        Map<Long, List<DeviceVO>> devicesByLane = getBoundDevicesByLaneIds(laneIds);

        return lanePage.convert(lane -> {
            ParkingLaneVO vo = toVO(lane);
            vo.setDevices(devicesByLane.getOrDefault(lane.getId(), Collections.emptyList()));
            return vo;
        });
    }

    /**
     * 查询单个车道详情（含绑定设备）。
     *
     * @param laneId 车道 ID
     * @return 车道视图
     */
    public ParkingLaneVO get(Long laneId) {
        ParkingLane lane = getLaneWithAuth(laneId);
        ParkingLaneVO vo = toVO(lane);
        vo.setDevices(getBoundDevices(laneId));
        return vo;
    }

    // ==================== 启用/停用 ====================

    /**
     * 启用或停用车道。
     * <p>
     * 使用条件更新防止并发覆盖。
     *
     * @param laneId 车道 ID
     * @param action ENABLED 或 DISABLED（前端合约）
     */
    @Transactional
    public void updateStatus(Long laneId, String action) {
        String actionUpper = action != null ? action.toUpperCase() : "";
        if (!STATUS_ENABLED.equals(actionUpper) && !STATUS_DISABLED.equals(actionUpper)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的操作类型: " + action + "，仅支持 ENABLED / DISABLED");
        }

        ParkingLane lane = getLaneWithAuth(laneId);
        Integer beforeStatus = lane.getStatus();

        // 状态流转校验
        int newStatus = STATUS_ENABLED.equals(actionUpper) ? DB_STATUS_ENABLED : DB_STATUS_DISABLED;
        if (Integer.valueOf(newStatus).equals(beforeStatus)) {
            String statusLabel = STATUS_ENABLED.equals(actionUpper) ? "启用" : "停用";
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "车道已是" + statusLabel + "状态");
        }

        LambdaUpdateWrapper<ParkingLane> wrapper = new LambdaUpdateWrapper<ParkingLane>()
                .set(ParkingLane::getStatus, newStatus)
                .set(ParkingLane::getUpdatedAt, LocalDateTime.now())
                .eq(ParkingLane::getId, laneId)
                .eq(ParkingLane::getStatus, beforeStatus);

        boolean updated = laneMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "车道状态已变更，请刷新后重试");
        }

        log.info("车道状态变更成功: laneId={}, {} -> {}, lotId={}",
                laneId, beforeStatus, actionUpper, lane.getLotId());
    }

    // ==================== 私有方法 ====================

    /**
     * 查询车道并校验停车场归属（通过停车场 → 租户链）。
     */
    private ParkingLane getLaneWithAuth(Long laneId) {
        ParkingLane lane = laneMapper.selectByIdIgnoreTenant(laneId);
        if (lane == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车道不存在");
        }
        // 通过停车场校验租户归属
        getParkingLotWithAuth(lane.getLotId());
        return lane;
    }

    /**
     * 查询停车场并校验租户归属 + 停车场级授权。
     * <p>
     * <strong>P0 停车场级数据隔离</strong>：在租户校验通过后，额外校验当前用户
     * 是否有权访问该停车场。
     */
    private ParkingLot getParkingLotWithAuth(Long lotId) {
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        DataScope.validateTenantMatch(lot.getTenantId(), "停车场");
        // P0：停车场级数据范围校验
        scopeResolver.validateAccess(lotId);
        return lot;
    }

    /**
     * 实体转视图（DB Integer → 前端 String）。
     */
    private ParkingLaneVO toVO(ParkingLane lane) {
        ParkingLaneVO vo = new ParkingLaneVO();
        vo.setId(lane.getId());
        vo.setParkingLotId(lane.getLotId());
        vo.setName(lane.getName());
        vo.setCode(lane.getLaneNo());
        vo.setDirection(intToDirectionStr(lane.getType()));
        vo.setStatus(intToStatusStr(lane.getStatus()));
        vo.setIsKeyLane(null);        // 预留：DB 不含此字段，后续迁移补齐
        vo.setAutoReleasePolicy(null); // 预留：DB 不含此字段
        vo.setDescription(null);       // 预留：DB 不含此字段
        vo.setCreatedAt(lane.getCreatedAt());
        vo.setUpdatedAt(lane.getUpdatedAt());
        return vo;
    }

    /**
     * 如果值为 null 或空则返回默认值。
     */
    private static String defaultString(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    // ==================== 设备绑定查询（T21） ====================

    /**
     * 查询单个车道的绑定设备列表。
     */
    private List<DeviceVO> getBoundDevices(Long laneId) {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getLaneId, laneId));
        return devices.stream().map(this::toDeviceVO).collect(Collectors.toList());
    }

    /**
     * 批量查询多车道的绑定设备（按 laneId 分组）。
     */
    private Map<Long, List<DeviceVO>> getBoundDevicesByLaneIds(List<Long> laneIds) {
        if (laneIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().in(Device::getLaneId, laneIds));
        return devices.stream()
                .map(this::toDeviceVO)
                .collect(Collectors.groupingBy(DeviceVO::getLaneId));
    }

    /**
     * Device 实体转 DeviceVO。
     */
    private DeviceVO toDeviceVO(Device device) {
        DeviceVendor vendor = vendorMapper.selectById(device.getVendorId());
        DeviceModel model = modelMapper.selectById(device.getModelId());
        DeviceVO vo = new DeviceVO();
        vo.setId(device.getId());
        vo.setParkingLotId(device.getParkingLotId());
        vo.setLaneId(device.getLaneId());
        vo.setExecutorDeviceId(device.getExecutorDeviceId());
        vo.setVendorId(device.getVendorId());
        vo.setVendorName(vendor != null ? vendor.getName() : null);
        vo.setModelId(device.getModelId());
        vo.setModelName(model != null ? model.getName() : null);
        vo.setName(device.getName());
        vo.setCode(device.getCode());
        vo.setDeviceSn(device.getDeviceSn());
        vo.setDeviceType(device.getDeviceType());
        vo.setRecognitionDirection(device.getRecognitionDirection());
        vo.setCameraRole(device.getCameraRole());
        vo.setStatus(device.getStatus());
        vo.setCapabilities(device.getCapabilities());
        vo.setDescription(device.getDescription());
        vo.setCreatedAt(device.getCreatedAt());
        vo.setUpdatedAt(device.getUpdatedAt());
        return vo;
    }
}
