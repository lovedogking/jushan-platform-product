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
 * <strong>车道方向枚举</strong>（可逆，待确认混合车道语义）：
 * <ul>
 *   <li>{@code ENTRY} — 入口车道（入场方向）</li>
 *   <li>{@code EXIT} — 出口车道（出场方向）</li>
 *   <li>{@code MIXED} — 混合车道（可切换出入方向，语义待确认，当前以可逆枚举标记）</li>
 * </ul>
 * <p>
 * <strong>自动放行策略</strong>：
 * <ul>
 *   <li>{@code AUTO} — 自动放行</li>
 *   <li>{@code MANUAL} — 人工确认</li>
 *   <li>{@code AFTER_PAY} — 缴费后自动放行</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ParkingLaneService {

    private static final Logger log = LoggerFactory.getLogger(ParkingLaneService.class);

    /** 车道状态常量 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 车道方向枚举 */
    private static final List<String> VALID_DIRECTIONS = List.of("ENTRY", "EXIT", "MIXED");

    /** 自动放行策略枚举 */
    private static final List<String> VALID_AUTO_RELEASE_POLICIES = List.of("AUTO", "MANUAL", "AFTER_PAY");

    /** 默认值 */
    private static final String DEFAULT_DIRECTION = "ENTRY";
    private static final String DEFAULT_AUTO_RELEASE_POLICY = "MANUAL";
    private static final int DEFAULT_IS_KEY_LANE = 0;

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
        String direction = defaultString(request.getDirection(), DEFAULT_DIRECTION).toUpperCase();
        if (!VALID_DIRECTIONS.contains(direction)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的车道方向: " + direction + "，仅支持 " + String.join(", ", VALID_DIRECTIONS));
        }

        // 3. 校验编码在停车场内唯一
        String code = request.getCode().trim();
        Long existingCount = laneMapper.selectCount(
                new LambdaQueryWrapper<ParkingLane>()
                        .eq(ParkingLane::getParkingLotId, request.getParkingLotId())
                        .eq(ParkingLane::getCode, code));
        if (existingCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "车道编码 '" + code + "' 在本停车场内已存在");
        }

        // 4. 校验自动放行策略
        String autoReleasePolicy = defaultString(request.getAutoReleasePolicy(), DEFAULT_AUTO_RELEASE_POLICY).toUpperCase();
        if (!VALID_AUTO_RELEASE_POLICIES.contains(autoReleasePolicy)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的自动放行策略: " + autoReleasePolicy + "，仅支持 " + String.join(", ", VALID_AUTO_RELEASE_POLICIES));
        }

        ParkingLane lane = new ParkingLane();
        lane.setParkingLotId(request.getParkingLotId());
        lane.setName(request.getName().trim());
        lane.setCode(code);
        lane.setDirection(direction);
        lane.setStatus(STATUS_ENABLED);
        lane.setIsKeyLane(request.getIsKeyLane() != null ? request.getIsKeyLane() : DEFAULT_IS_KEY_LANE);
        lane.setAutoReleasePolicy(autoReleasePolicy);
        lane.setDescription(defaultString(request.getDescription(), ""));
        lane.setCreatedAt(LocalDateTime.now());
        lane.setUpdatedAt(LocalDateTime.now());
        laneMapper.insert(lane);

        log.info("创建车道成功: parkingLotId={}, laneId={}, name={}, code={}, direction={}",
                request.getParkingLotId(), lane.getId(), lane.getName(), code, direction);

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
            String newCode = request.getCode().trim();
            // 编码变更时检查唯一性（排除自身）
            Long existingCount = laneMapper.selectCount(
                    new LambdaQueryWrapper<ParkingLane>()
                            .eq(ParkingLane::getParkingLotId, lane.getParkingLotId())
                            .eq(ParkingLane::getCode, newCode)
                            .ne(ParkingLane::getId, laneId));
            if (existingCount > 0) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "车道编码 '" + newCode + "' 在本停车场内已存在");
            }
            wrapper.set(ParkingLane::getCode, newCode);
            hasUpdate = true;
        }
        if (request.getDirection() != null) {
            String direction = request.getDirection().toUpperCase();
            if (!VALID_DIRECTIONS.contains(direction)) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "无效的车道方向: " + direction + "，仅支持 " + String.join(", ", VALID_DIRECTIONS));
            }
            wrapper.set(ParkingLane::getDirection, direction);
            hasUpdate = true;
        }
        if (request.getIsKeyLane() != null) {
            wrapper.set(ParkingLane::getIsKeyLane, request.getIsKeyLane());
            hasUpdate = true;
        }
        if (request.getAutoReleasePolicy() != null) {
            String policy = request.getAutoReleasePolicy().toUpperCase();
            if (!VALID_AUTO_RELEASE_POLICIES.contains(policy)) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "无效的自动放行策略: " + policy + "，仅支持 " + String.join(", ", VALID_AUTO_RELEASE_POLICIES));
            }
            wrapper.set(ParkingLane::getAutoReleasePolicy, policy);
            hasUpdate = true;
        }
        if (request.getDescription() != null) {
            wrapper.set(ParkingLane::getDescription, request.getDescription().trim());
            hasUpdate = true;
        }

        if (!hasUpdate) {
            return toVO(lane);
        }

        wrapper.set(ParkingLane::getUpdatedAt, LocalDateTime.now());
        laneMapper.update(null, wrapper);

        ParkingLane updated = laneMapper.selectById(laneId);
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
     * @param status       状态筛选（可选）
     * @param direction    方向筛选（可选）
     * @return 分页结果
     */
    public IPage<ParkingLaneVO> list(int page, int size, Long parkingLotId, String status, String direction) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }

        // 校验停车场归属（租户隔离）
        getParkingLotWithAuth(parkingLotId);

        String dirFilter = direction != null && !direction.isBlank() ? direction.toUpperCase() : null;
        LambdaQueryWrapper<ParkingLane> wrapper = new LambdaQueryWrapper<ParkingLane>()
                .eq(ParkingLane::getParkingLotId, parkingLotId)
                .eq(status != null && !status.isBlank(), ParkingLane::getStatus, status)
                .eq(dirFilter != null, ParkingLane::getDirection, dirFilter)
                .orderByAsc(ParkingLane::getDirection)
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
     * @param action ENABLED 或 DISABLED
     */
    @Transactional
    public void updateStatus(Long laneId, String action) {
        String actionUpper = action != null ? action.toUpperCase() : "";
        if (!STATUS_ENABLED.equals(actionUpper) && !STATUS_DISABLED.equals(actionUpper)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的操作类型: " + action + "，仅支持 ENABLED / DISABLED");
        }

        ParkingLane lane = getLaneWithAuth(laneId);
        String beforeStatus = lane.getStatus();

        // 状态流转校验
        if (actionUpper.equals(beforeStatus)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "车道已是" + (STATUS_ENABLED.equals(beforeStatus) ? "启用" : "停用") + "状态");
        }

        LambdaUpdateWrapper<ParkingLane> wrapper = new LambdaUpdateWrapper<ParkingLane>()
                .set(ParkingLane::getStatus, actionUpper)
                .set(ParkingLane::getUpdatedAt, LocalDateTime.now())
                .eq(ParkingLane::getId, laneId)
                .eq(ParkingLane::getStatus, beforeStatus);

        boolean updated = laneMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "车道状态已变更，请刷新后重试");
        }

        log.info("车道状态变更成功: laneId={}, {} -> {}, parkingLotId={}",
                laneId, beforeStatus, actionUpper, lane.getParkingLotId());
    }

    // ==================== 私有方法 ====================

    /**
     * 查询车道并校验停车场归属（通过停车场 → 租户链）。
     */
    private ParkingLane getLaneWithAuth(Long laneId) {
        ParkingLane lane = laneMapper.selectById(laneId);
        if (lane == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车道不存在");
        }
        // 通过停车场校验租户归属
        getParkingLotWithAuth(lane.getParkingLotId());
        return lane;
    }

    /**
     * 查询停车场并校验租户归属 + 停车场级授权。
     * <p>
     * <strong>P0 停车场级数据隔离</strong>：在租户校验通过后，额外校验当前用户
     * 是否有权访问该停车场。
     */
    private ParkingLot getParkingLotWithAuth(Long lotId) {
        ParkingLot lot = parkingLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        DataScope.validateTenantMatch(lot.getTenantId(), "停车场");
        // P0：停车场级数据范围校验
        scopeResolver.validateAccess(lotId);
        return lot;
    }

    /**
     * 实体转视图。
     */
    private ParkingLaneVO toVO(ParkingLane lane) {
        ParkingLaneVO vo = new ParkingLaneVO();
        vo.setId(lane.getId());
        vo.setParkingLotId(lane.getParkingLotId());
        vo.setName(lane.getName());
        vo.setCode(lane.getCode());
        vo.setDirection(lane.getDirection());
        vo.setStatus(lane.getStatus());
        vo.setIsKeyLane(lane.getIsKeyLane());
        vo.setAutoReleasePolicy(lane.getAutoReleasePolicy());
        vo.setDescription(lane.getDescription());
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
        vo.setStatus(device.getStatus());
        vo.setCapabilities(device.getCapabilities());
        vo.setDescription(device.getDescription());
        vo.setCreatedAt(device.getCreatedAt());
        vo.setUpdatedAt(device.getUpdatedAt());
        return vo;
    }
}
