package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.client.dto.DisplayConfigRequest;
import com.jushan.system.client.dto.DisplayResultDTO;
import com.jushan.system.client.dto.DisplayTextRequest;
import com.jushan.system.client.dto.TimeSyncResultDTO;
import com.jushan.system.client.dto.VoiceControlRequest;
import com.jushan.system.client.dto.VoiceResultDTO;
import com.jushan.system.dto.CreateDeviceRequest;
import com.jushan.system.dto.UpdateDeviceRequest;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.DeviceCommandAudit;
import com.jushan.system.entity.DeviceModel;
import com.jushan.system.entity.DeviceStatusSnapshot;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.SysAuditLog;
import com.jushan.system.mapper.DeviceCommandAuditMapper;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceModelMapper;
import com.jushan.system.mapper.DeviceStatusSnapshotMapper;
import com.jushan.system.mapper.DeviceVendorMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.SysAuditLogMapper;
import com.jushan.system.vo.DeviceStatusVO;
import com.jushan.system.vo.DeviceVO;
import com.jushan.system.service.CameraFailoverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 平台设备台账服务（T20）。
 * <p>
 * 负责设备 CRUD、状态管理、厂商/型号关联和租户-停车场数据隔离。
 * 所有操作从当前登录会话推导租户范围，并通过停车场归属验证数据隔离。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>前端只能提交平台设备引用（parkingLotId / vendorId / modelId），禁止直接传入 device_sn</li>
 *   <li>device_sn 由后端从可信记录读取</li>
 *   <li>SN 在同厂商内唯一（UNIQUE(vendor_id, device_sn)）</li>
 *   <li>禁止跨租户/停车场复用设备</li>
 * </ul>
 * <p>
 * <strong>设备类型</strong>：
 * <ul>
 *   <li>{@code CAMERA} — 相机（车牌识别、抓拍）</li>
 *   <li>{@code GATE} — 道闸（开闸执行器）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    /** 设备状态 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 设备命令类型（P001） */
    public static final String COMMAND_TYPE_OPEN_GATE = "OPEN_GATE";

    /** 命令来源（P001） */
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_AUTO_EXIT = "AUTO_EXIT";

    /** 命令审计状态（P001） */
    public static final String AUDIT_STATUS_PENDING = "PENDING";
    public static final String AUDIT_STATUS_SUCCESS = "SUCCESS";
    public static final String AUDIT_STATUS_FAILED = "FAILED";
    public static final String AUDIT_STATUS_UNCERTAIN = "UNCERTAIN";
    public static final String AUDIT_STATUS_REJECTED = "REJECTED";
    public static final String AUDIT_STATUS_NOT_IMPLEMENTED = "NOT_IMPLEMENTED";

    /** 设备类型 */
    private static final List<String> VALID_DEVICE_TYPES = List.of("CAMERA", "GATE");

    /** 默认值 */
    private static final String DEFAULT_DEVICE_TYPE = "CAMERA";
    private static final String DEFAULT_CAPABILITIES = "";

    /** 识别方向 */
    public static final int DIRECTION_ENTRY = 1;
    public static final int DIRECTION_EXIT = 2;

    /** 主备角色 */
    public static final int CAMERA_ROLE_PRIMARY = 1;
    public static final int CAMERA_ROLE_BACKUP = 2;

    /** 快照过期阈值（秒），超过此时间的快照标记为 stale */
    static final long STALE_THRESHOLD_SECONDS = 60;

    private final DeviceMapper deviceMapper;
    private final DeviceVendorMapper vendorMapper;
    private final DeviceModelMapper modelMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final DeviceAccessClient deviceAccessClient;
    private final DeviceStatusSnapshotMapper snapshotMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final DeviceCommandAuditMapper commandAuditMapper;
    private final ParkingLotScopeResolver scopeResolver;
    private final CameraFailoverService cameraFailoverService;

    public DeviceService(DeviceMapper deviceMapper,
                         DeviceVendorMapper vendorMapper,
                         DeviceModelMapper modelMapper,
                         ParkingLotMapper parkingLotMapper,
                         ParkingLaneMapper laneMapper,
                         DeviceAccessClient deviceAccessClient,
                         DeviceStatusSnapshotMapper snapshotMapper,
                         SysAuditLogMapper auditLogMapper,
                         DeviceCommandAuditMapper commandAuditMapper,
                         ParkingLotScopeResolver scopeResolver,
                         CameraFailoverService cameraFailoverService) {
        this.deviceMapper = deviceMapper;
        this.vendorMapper = vendorMapper;
        this.modelMapper = modelMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.deviceAccessClient = deviceAccessClient;
        this.snapshotMapper = snapshotMapper;
        this.auditLogMapper = auditLogMapper;
        this.commandAuditMapper = commandAuditMapper;
        this.scopeResolver = scopeResolver;
        this.cameraFailoverService = cameraFailoverService;
    }

    // ==================== 创建设备 ====================

    /**
     * 创建设备。
     * <p>
     * 校验停车场归属、厂商/型号有效性、编码停车场内唯一、SN 同厂商唯一。
     *
     * @param request 创建请求
     * @return 设备视图
     */
    @Transactional
    public DeviceVO create(CreateDeviceRequest request) {
        // 1. 校验停车场归属（租户隔离）
        getParkingLotWithAuth(request.getParkingLotId());

        // 2. 校验厂商存在且启用
        DeviceVendor vendor = getVendorEnabled(request.getVendorId());

        // 3. 校验型号存在、属于该厂商且启用
        DeviceModel model = getModelEnabled(request.getModelId(), request.getVendorId());

        // 4. 校验设备类型
        String deviceType = defaultString(request.getDeviceType(), DEFAULT_DEVICE_TYPE).toUpperCase();
        if (!VALID_DEVICE_TYPES.contains(deviceType)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的设备类型: " + deviceType + "，仅支持 " + String.join(", ", VALID_DEVICE_TYPES));
        }

        // 5. 校验编码在停车场内唯一
        String code = request.getCode().trim();
        Long existingCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getParkingLotId, request.getParkingLotId())
                        .eq(Device::getCode, code));
        if (existingCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备编码 '" + code + "' 在本停车场内已存在");
        }

        // 6. 校验 device_sn 在同厂商内唯一
        String deviceSn = request.getDeviceSn().trim();
        if (deviceSn.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "设备序列号不能为空");
        }
        Long snCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getVendorId, request.getVendorId())
                        .eq(Device::getDeviceSn, deviceSn));
        if (snCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备序列号 '" + deviceSn + "' 在厂商 '" + vendor.getName() + "' 中已存在");
        }

        Device device = new Device();
        device.setParkingLotId(request.getParkingLotId());
        device.setVendorId(request.getVendorId());
        device.setModelId(request.getModelId());
        device.setName(request.getName().trim());
        device.setCode(code);
        device.setDeviceSn(deviceSn);
        device.setDeviceType(deviceType);
        device.setRecognitionDirection(validateDirection(request.getRecognitionDirection(), deviceType));
        device.setCameraRole(validateCameraRole(request.getCameraRole(), deviceType));
        device.setStatus(STATUS_ENABLED);
        device.setCapabilities(defaultString(request.getCapabilities(), DEFAULT_CAPABILITIES));
        device.setDescription(defaultString(request.getDescription(), ""));
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(device);

        log.info("创建设备成功: parkingLotId={}, deviceId={}, name={}, code={}, vendor={}, model={}, deviceSn={}",
                request.getParkingLotId(), device.getId(), device.getName(), code,
                vendor.getName(), model.getName(), deviceSn);

        return toVO(device, vendor, model);
    }

    // ==================== 更新设备 ====================

    /**
     * 更新设备基础信息（部分更新）。
     * <p>
     * device_sn 不可通过此接口修改（SN 变更需走专门的审计流程）。
     *
     * @param deviceId 设备 ID
     * @param request  更新请求（仅非 null 字段被更新）
     * @return 设备视图
     */
    @Transactional
    public DeviceVO update(Long deviceId, UpdateDeviceRequest request) {
        Device device = getDeviceWithAuth(deviceId);

        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<Device>()
                .eq(Device::getId, deviceId);

        boolean hasUpdate = false;

        if (request.getName() != null) {
            wrapper.set(Device::getName, request.getName().trim());
            hasUpdate = true;
        }
        if (request.getCode() != null) {
            String newCode = request.getCode().trim();
            // 编码变更时检查唯一性（排除自身）
            Long existingCount = deviceMapper.selectCount(
                    new LambdaQueryWrapper<Device>()
                            .eq(Device::getParkingLotId, device.getParkingLotId())
                            .eq(Device::getCode, newCode)
                            .ne(Device::getId, deviceId));
            if (existingCount > 0) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "设备编码 '" + newCode + "' 在本停车场内已存在");
            }
            wrapper.set(Device::getCode, newCode);
            hasUpdate = true;
        }
        if (request.getDeviceType() != null) {
            String deviceType = request.getDeviceType().toUpperCase();
            if (!VALID_DEVICE_TYPES.contains(deviceType)) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "无效的设备类型: " + deviceType + "，仅支持 " + String.join(", ", VALID_DEVICE_TYPES));
            }
            wrapper.set(Device::getDeviceType, deviceType);
            hasUpdate = true;
        }
        if (request.getRecognitionDirection() != null) {
            wrapper.set(Device::getRecognitionDirection, validateDirection(request.getRecognitionDirection(), device.getDeviceType()));
            hasUpdate = true;
        }
        if (request.getCameraRole() != null) {
            wrapper.set(Device::getCameraRole, validateCameraRole(request.getCameraRole(), device.getDeviceType()));
            hasUpdate = true;
        }
        if (request.getCapabilities() != null) {
            wrapper.set(Device::getCapabilities, request.getCapabilities());
            hasUpdate = true;
        }
        if (request.getDescription() != null) {
            wrapper.set(Device::getDescription, request.getDescription().trim());
            hasUpdate = true;
        }

        if (!hasUpdate) {
            return toVO(device, vendorMapper.selectById(device.getVendorId()),
                    modelMapper.selectById(device.getModelId()));
        }

        wrapper.set(Device::getUpdatedAt, LocalDateTime.now());
        deviceMapper.update(null, wrapper);

        Device updated = deviceMapper.selectById(deviceId);
        DeviceVendor vendor = vendorMapper.selectById(updated.getVendorId());
        DeviceModel model = modelMapper.selectById(updated.getModelId());
        log.info("更新设备成功: deviceId={}", deviceId);
        return toVO(updated, vendor, model);
    }

    // ==================== 设备查询 ====================

    /**
     * 分页查询设备列表。
     * <p>
     * 按停车场筛选，租户内数据隔离。平台用户可跨租户查看。
     *
     * @param page         页码
     * @param size         每页大小
     * @param parkingLotId 停车场 ID（必填）
     * @param status       状态筛选（可选）
     * @param deviceType   设备类型筛选（可选）
     * @return 分页结果
     */
    public IPage<DeviceVO> list(int page, int size, Long parkingLotId, String status, String deviceType) {
        String typeUpper = (deviceType != null && !deviceType.isBlank())
                ? deviceType.toUpperCase() : null;
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
                .eq(parkingLotId != null, Device::getParkingLotId, parkingLotId)
                .eq(status != null && !status.isBlank(), Device::getStatus, status)
                .eq(typeUpper != null, Device::getDeviceType, typeUpper)
                .orderByDesc(Device::getCreatedAt);

        // 租户数据隔离：非平台用户按 scopeResolver 限制可访问的车场
        if (parkingLotId == null) {
            Set<Long> authorizedIds = scopeResolver.resolveAuthorizedIds();
            if (authorizedIds != null) {
                if (authorizedIds.isEmpty()) {
                    return new Page<DeviceVO>(page, size);
                }
                wrapper.in(Device::getParkingLotId, authorizedIds);
            }
        } else {
            getParkingLotWithAuth(parkingLotId);
        }

        IPage<Device> devicePage = deviceMapper.selectPage(new Page<>(page, size), wrapper);
        return devicePage.convert(d -> {
            DeviceVendor vendor = vendorMapper.selectById(d.getVendorId());
            DeviceModel model = modelMapper.selectById(d.getModelId());
            return toVO(d, vendor, model);
        });
    }

    /**
     * 查询单个设备详情。
     *
     * @param deviceId 设备 ID
     * @return 设备视图
     */
    public DeviceVO get(Long deviceId) {
        Device device = getDeviceWithAuth(deviceId);
        DeviceVendor vendor = vendorMapper.selectById(device.getVendorId());
        DeviceModel model = modelMapper.selectById(device.getModelId());
        return toVO(device, vendor, model);
    }

    // ==================== 厂商/型号查询 ====================

    /**
     * 查询所有启用厂商列表。
     *
     * @return 厂商列表
     */
    public List<DeviceVendor> listVendors() {
        return vendorMapper.selectList(
                new LambdaQueryWrapper<DeviceVendor>()
                        .eq(DeviceVendor::getStatus, STATUS_ENABLED)
                        .orderByAsc(DeviceVendor::getId));
    }

    /**
     * 查询某厂商下所有启用型号列表。
     *
     * @param vendorId 厂商 ID（可选，不传则查全部）
     * @return 型号列表
     */
    public List<DeviceModel> listModels(Long vendorId) {
        LambdaQueryWrapper<DeviceModel> wrapper = new LambdaQueryWrapper<DeviceModel>()
                .eq(DeviceModel::getStatus, STATUS_ENABLED);
        if (vendorId != null) {
            wrapper.eq(DeviceModel::getVendorId, vendorId);
        }
        return modelMapper.selectList(wrapper.orderByAsc(DeviceModel::getId));
    }

    // ==================== 启用/停用 ====================

    /**
     * 启用或停用设备。
     * <p>
     * 使用条件更新（乐观锁）防止并发覆盖。
     *
     * @param deviceId 设备 ID
     * @param action   ENABLED 或 DISABLED
     */
    @Transactional
    public void updateStatus(Long deviceId, String action) {
        String actionUpper = action != null ? action.toUpperCase() : "";
        if (!STATUS_ENABLED.equals(actionUpper) && !STATUS_DISABLED.equals(actionUpper)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的操作类型: " + action + "，仅支持 ENABLED / DISABLED");
        }

        Device device = getDeviceWithAuth(deviceId);
        String beforeStatus = device.getStatus();

        if (actionUpper.equals(beforeStatus)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备已是" + (STATUS_ENABLED.equals(beforeStatus) ? "启用" : "停用") + "状态");
        }

        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<Device>()
                .set(Device::getStatus, actionUpper)
                .set(Device::getUpdatedAt, LocalDateTime.now())
                .eq(Device::getId, deviceId)
                .eq(Device::getStatus, beforeStatus);

        boolean updated = deviceMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "设备状态已变更，请刷新后重试");
        }

        log.info("设备状态变更成功: deviceId={}, {} -> {}, parkingLotId={}",
                deviceId, beforeStatus, actionUpper, device.getParkingLotId());
    }

    // ==================== 车道绑定（T21） ====================

    /**
     * 将设备绑定到车道。
     * <p>
     * 校验设备已启用、车道存在且同停车场、同车道同类型无其他设备。
     * 对于 GATE 设备，绑定后还需调用 setExecutor 指定执行相机。
     *
     * @param deviceId 设备 ID
     * @param laneId   车道 ID
     * @return 绑定后的设备视图
     */
    @Transactional
    public DeviceVO bindLane(Long deviceId, Long laneId) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不能绑定车道");
        }

        ParkingLane lane = laneMapper.selectById(laneId);
        if (lane == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车道不存在");
        }
        // 校验车道归属（租户隔离）
        getParkingLotWithAuth(lane.getLotId());

        if (lane.getStatus() == null || lane.getStatus() != 1) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的车道不能绑定设备");
        }

        // 设备和车道必须在同一停车场
        if (!device.getParkingLotId().equals(lane.getLotId())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备与车道不属于同一停车场，不允许绑定");
        }

        // CAMERA 设备绑定校验（识别方向 + 主备关系 + 车道类型约束）
        if ("CAMERA".equals(device.getDeviceType())) {
            validateCameraLaneBinding(device, lane);
        }

        // 更新 laneId
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<Device>()
                .set(Device::getLaneId, laneId)
                .set(Device::getUpdatedAt, LocalDateTime.now())
                .eq(Device::getId, deviceId);
        deviceMapper.update(null, wrapper);
        device.setLaneId(laneId);

        // GATE 绑定后自动清空旧的 executor（需要重新指定）
        if ("GATE".equals(device.getDeviceType()) && device.getExecutorDeviceId() != null) {
            LambdaUpdateWrapper<Device> clearWrapper = new LambdaUpdateWrapper<Device>()
                    .set(Device::getExecutorDeviceId, null)
                    .set(Device::getUpdatedAt, LocalDateTime.now())
                    .eq(Device::getId, deviceId);
            deviceMapper.update(null, clearWrapper);
            device.setExecutorDeviceId(null);
        }

        log.info("设备绑定车道成功: deviceId={}, laneId={}, deviceType={}, parkingLotId={}",
                deviceId, laneId, device.getDeviceType(), device.getParkingLotId());

        DeviceVendor vendor = vendorMapper.selectById(device.getVendorId());
        DeviceModel model = modelMapper.selectById(device.getModelId());
        return toVO(device, vendor, model);
    }

    /**
     * 将设备与车道解绑。
     *
     * @param deviceId 设备 ID
     * @return 解绑后的设备视图
     */
    @Transactional
    public DeviceVO unbindLane(Long deviceId) {
        Device device = getDeviceWithAuth(deviceId);

        if (device.getLaneId() == null) {
            return toVO(device, vendorMapper.selectById(device.getVendorId()),
                    modelMapper.selectById(device.getModelId()));
        }

        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<Device>()
                .set(Device::getLaneId, null)
                .set(Device::getExecutorDeviceId, null)
                .set(Device::getUpdatedAt, LocalDateTime.now())
                .eq(Device::getId, deviceId);
        deviceMapper.update(null, wrapper);
        device.setLaneId(null);
        device.setExecutorDeviceId(null);

        log.info("设备解绑车道成功: deviceId={}, parkingLotId={}", deviceId, device.getParkingLotId());

        DeviceVendor vendor = vendorMapper.selectById(device.getVendorId());
        DeviceModel model = modelMapper.selectById(device.getModelId());
        return toVO(device, vendor, model);
    }

    /**
     * 为 GATE 设备设置执行相机。
     * <p>
     * 只有 GATE 类型设备需要设置 executor；相机自身即为执行者。
     * 执行相机必须与 GATE 绑定到同一车道、同一停车场、已启用且类型为 CAMERA。
     *
     * @param gateDeviceId     GATE 设备 ID
     * @param executorDeviceId 执行开闸的 CAMERA 设备 ID
     * @return 更新后的设备视图
     */
    @Transactional
    public DeviceVO setExecutor(Long gateDeviceId, Long executorDeviceId) {
        Device gate = getDeviceWithAuth(gateDeviceId);

        if (!"GATE".equals(gate.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅逻辑道闸（GATE）设备需要设置执行相机");
        }

        Device camera = getDeviceWithAuth(executorDeviceId);

        if (!"CAMERA".equals(camera.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "执行设备必须是相机（CAMERA）类型");
        }
        if (!STATUS_ENABLED.equals(camera.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "执行相机 '" + camera.getName() + "' 已停用");
        }

        // 必须绑定到同一车道
        if (gate.getLaneId() == null || !gate.getLaneId().equals(camera.getLaneId())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "GATE 设备与执行相机必须绑定到同一车道");
        }

        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<Device>()
                .set(Device::getExecutorDeviceId, executorDeviceId)
                .set(Device::getUpdatedAt, LocalDateTime.now())
                .eq(Device::getId, gateDeviceId);
        deviceMapper.update(null, wrapper);
        gate.setExecutorDeviceId(executorDeviceId);

        log.info("设置 GATE 执行相机成功: gateDeviceId={}, executorDeviceId={}, executorSn={}",
                gateDeviceId, executorDeviceId, camera.getDeviceSn());

        DeviceVendor vendor = vendorMapper.selectById(gate.getVendorId());
        DeviceModel model = modelMapper.selectById(gate.getModelId());
        return toVO(gate, vendor, model);
    }

    /**
     * 查询指定停车场下可绑定到车道的相机列表。
     * <p>
     * 仅返回已启用 + CAMERA 类型的设备，包含识别方向信息。
     *
     * @param parkingLotId 停车场 ID
     * @return 相机列表，每项含 deviceId、deviceName、recognitionDirection
     */
    public List<Map<String, Object>> listAvailableForLane(Long parkingLotId) {
        // 校验停车场归属（租户隔离 + 停车场级授权）
        getParkingLotWithAuth(parkingLotId);

        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getParkingLotId, parkingLotId)
                        .eq(Device::getDeviceType, "CAMERA")
                        .eq(Device::getStatus, STATUS_ENABLED));

        return devices.stream().map(d -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("deviceId", d.getId());
            item.put("deviceName", d.getName());
            item.put("recognitionDirection", d.getRecognitionDirection());
            return item;
        }).collect(Collectors.toList());
    }

    // ==================== 识别方向/主备角色校验 ====================

    /**
     * 校验识别方向值合法性（仅 CAMERA 可设置）。
     */
    private Integer validateDirection(Integer direction, String deviceType) {
        if (direction == null) return null;
        if (!"CAMERA".equals(deviceType)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "仅相机（CAMERA）设备可以设置识别方向");
        }
        if (direction != DIRECTION_ENTRY && direction != DIRECTION_EXIT) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的识别方向: " + direction + "，仅支持 1=入场 / 2=出场");
        }
        return direction;
    }

    /**
     * 校验主备角色值合法性（仅 CAMERA 可设置）。
     */
    private Integer validateCameraRole(Integer role, String deviceType) {
        if (role == null) return null;
        if (!"CAMERA".equals(deviceType)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "仅相机（CAMERA）设备可以设置主备角色");
        }
        if (role != CAMERA_ROLE_PRIMARY && role != CAMERA_ROLE_BACKUP) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的主备角色: " + role + "，仅支持 1=主相机 / 2=备相机");
        }
        return role;
    }

    /**
     * CAMERA 设备绑定车道校验（解除一车道一相机限制后的新规则）。
     * <p>
     * 单向通道（type=1/2）：允许 1 台相机（单相机模式）或 2 台（主备双相机）。
     *   同车道同方向最多一主一备。
     * <p>
     * 双向通道（type=3）：必须覆盖入场+出场两个识别方向的相机。
     *   每个方向允许 1 台（单相机）或 2 台（主备）。
     *
     * @param device 待绑定的设备
     * @param lane   目标车道
     */
    private void validateCameraLaneBinding(Device device, ParkingLane lane) {
        Integer laneType = lane.getType();
        if (laneType == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "车道类型未设置，无法绑定相机");
        }

        // 收集已绑定到此车道的所有 CAMERA 设备（排除自身）
        List<Device> existingCameras = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getLaneId, lane.getId())
                        .eq(Device::getDeviceType, "CAMERA")
                        .ne(Device::getId, device.getId()));

        // 校验当前设备的识别方向必填（创建时未填则拦截）
        Integer direction = device.getRecognitionDirection();
        if (laneType == 3) {
            // 双向通道：识别方向必填
            if (direction == null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "双向通道绑定的相机必须指定识别方向（1=入场 / 2=出场），请先在设备信息中设置识别方向");
            }
        }

        if (direction != null) {
            // 校验同车道同方向同角色不重复
            for (Device existing : existingCameras) {
                if (direction.equals(existing.getRecognitionDirection())
                        && device.getCameraRole() != null
                        && device.getCameraRole().equals(existing.getCameraRole())) {
                    String roleLabel = device.getCameraRole() == CAMERA_ROLE_PRIMARY ? "主相机" : "备相机";
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                            "该车道已存在相同识别方向和角色的相机（方向=" + directionLabel(direction) + "，" + roleLabel + "），同方向同角色不允许重复");
                }
            }

            // 校验同车道同方向最多一主一备
            long sameDirectionCount = existingCameras.stream()
                    .filter(c -> direction.equals(c.getRecognitionDirection()))
                    .count();
            if (sameDirectionCount >= 2) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "该车道 " + directionLabel(direction) + " 方向已绑定 " + sameDirectionCount + " 台相机，最多允许一主一备共 2 台");
            }
        }

        if (laneType == 3) {
            // 双向通道：校验绑定后两个方向都需覆盖
            boolean hasEntry = direction != null && direction == DIRECTION_ENTRY
                    || existingCameras.stream().anyMatch(c -> Integer.valueOf(DIRECTION_ENTRY).equals(c.getRecognitionDirection()));
            boolean hasExit = direction != null && direction == DIRECTION_EXIT
                    || existingCameras.stream().anyMatch(c -> Integer.valueOf(DIRECTION_EXIT).equals(c.getRecognitionDirection()));
            if (!hasEntry) {
                log.warn("双向通道缺少入场方向相机: laneId={}, deviceId={}", lane.getId(), device.getId());
            }
            if (!hasExit) {
                log.warn("双向通道缺少出场方向相机: laneId={}, deviceId={}", lane.getId(), device.getId());
            }
        }
    }

    private static String directionLabel(Integer direction) {
        if (direction == null) return "未知";
        return direction == DIRECTION_ENTRY ? "入场" : "出场";
    }

    // ==================== 设备校时（T25） ====================

    /**
     * 设备校时（调用 DA）。
     * <p>
     * 校验设备权限/状态后，通过 Device Access Client 向设备发送校时命令，
     * 并将结果持久化到审计日志。
     * <b>不包含自动重试</b>；网络错误或命令超时标记为 UNCERTAIN。
     *
     * @param deviceId 平台设备 ID
     * @param reason   操作原因（由操作人填写）
     * @return 校时结果
     */
    @Transactional
    public TimeSyncResultDTO syncTime(Long deviceId, String reason) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不支持校时");
        }

        String deviceSn = device.getDeviceSn();
        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());
        Long tenantId = lot.getTenantId();
        String result = "SUCCESS";
        String failReason = "";
        TimeSyncResultDTO dto;

        try {
            dto = deviceAccessClient.syncTime(deviceSn);
            log.info("设备校时成功: deviceId={}, deviceSn={}, success={}, deviceCode={}",
                    deviceId, deviceSn, dto.getSuccess(), dto.getDeviceCode());
        } catch (BusinessException e) {
            // DA 返回错误或网络异常 → 记录失败但不抛出，让审计日志先持久化
            if (e.getMessage() != null && e.getMessage().contains("UNCERTAIN")) {
                result = "UNCERTAIN";
            } else {
                result = "FAILED";
            }
            failReason = e.getMessage();
            log.warn("设备校时失败: deviceId={}, deviceSn={}, result={}, error={}",
                    deviceId, deviceSn, result, e.getMessage());

            // 构造失败结果
            dto = new TimeSyncResultDTO();
            dto.setSuccess(false);
            dto.setMessage(failReason);
        }

        // 持久化审计日志
        writeAuditLog(device, lot, tenantId, result, failReason, reason);

        return dto;
    }

    /**
     * 写入校时操作审计记录。
     */
    private void writeAuditLog(Device device, ParkingLot lot, Long tenantId,
                               String result, String failReason, String reason) {
        SysAuditLog audit = new SysAuditLog();
        audit.setTenantId(tenantId);
        audit.setTargetType("device");
        audit.setTargetId(String.valueOf(device.getId()));
        audit.setAction("time_sync");

        // 操作人信息
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx != null) {
            audit.setOperatorId(ctx.userId() != null ? ctx.userId() : 0L);
            audit.setOperatorName("userId=" + (ctx.userId() != null ? ctx.userId() : "unknown"));
            audit.setIsProxy(0);
        } else {
            audit.setOperatorId(0L);
            audit.setOperatorName("system");
            audit.setIsProxy(0);
        }

        // 操作前后信息
        audit.setAfterValue("{\"deviceSn\":\"" + device.getDeviceSn()
                + "\",\"deviceName\":\"" + device.getName()
                + "\",\"parkingLotId\":" + lot.getId() + "}");

        audit.setResult(result);
        audit.setFailReason(failReason != null ? failReason : "");
        audit.setReason(reason != null ? reason : "");

        auditLogMapper.insert(audit);
    }

    // ==================== 开闸（P001 → v0.4） ====================

    /**
     * 开闸方法（已废弃）。
     * <p>
     * Device Access v0.4 已实现 {@code POST /api/v1/devices/{deviceSn}/gate/open}，
     * 请使用 {@link DeviceAccessClient#openGate(String)} 直接调用。
     * 本方法保留仅作为审计记录参考，将在后续版本中移除。
     * <p>
     * <strong>安全约束</strong>：
     * <ul>
     *   <li>禁止自动重试开闸</li>
     *   <li>UNCERTAIN 状态必须人工确认</li>
     *   <li>前端不得直接传入 device_sn</li>
     * </ul>
     *
     * @param deviceId 平台设备 ID
     * @param reason   操作原因
     * @param source   操作来源（MANUAL / SYSTEM / AUTO_EXIT 等）
     * @param previousCommandId 前次命令 ID（人工再次开闸时填写，可选）
     * @deprecated 使用 {@link DeviceAccessClient#openGate(String)} 替代（v0.4 已实现真实开闸）
     */
    @Deprecated
    public void openGatePlaceholder(Long deviceId, String reason, String source, String previousCommandId) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不能开闸");
        }

        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());

        // 当前仅 GATE 类型设备支持开闸；CAMERA 开闸由 executor mapping 在后续版本中明确
        if (!"GATE".equals(device.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅逻辑道闸（GATE）设备支持开闸");
        }

        String deviceSn = device.getDeviceSn();
        LocalDateTime now = LocalDateTime.now();

        // 构造审计记录
        DeviceCommandAudit audit = new DeviceCommandAudit();
        audit.setTenantId(lot.getTenantId());
        audit.setParkingLotId(device.getParkingLotId());
        audit.setLaneId(device.getLaneId());
        audit.setDeviceId(deviceId);
        audit.setDeviceSn(deviceSn);
        audit.setCommandType(COMMAND_TYPE_OPEN_GATE);
        audit.setSource(defaultString(source, SOURCE_MANUAL));
        audit.setReason(reason != null ? reason : "");
        audit.setPreviousCommandId(previousCommandId);
        audit.setRequestPayload(buildOpenGateRequestPayload(device, lot));
        audit.setIssuedAt(now);
        audit.setCreatedAt(now);
        audit.setUpdatedAt(now);

        // 操作人信息
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx != null) {
            audit.setOperatorId(ctx.userId());
            audit.setOperatorName("userId=" + (ctx.userId() != null ? ctx.userId() : "unknown"));
        } else {
            audit.setOperatorId(0L);
            audit.setOperatorName("system");
        }

        commandAuditMapper.insert(audit);

        // 调用 DeviceAccessClient v0.4 真实开闸
        try {
            com.jushan.system.client.dto.CommandResultDTO result = deviceAccessClient.openGate(deviceSn);
            audit.setStatus(result.isSuccessful() ? AUDIT_STATUS_SUCCESS : AUDIT_STATUS_FAILED);
            audit.setUncertain(false);
            audit.setResponsePayload("{\"success\":" + result.getSuccess()
                    + ",\"deviceCode\":" + result.getDeviceCode()
                    + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
            audit.setCompletedAt(LocalDateTime.now());
            audit.setUpdatedAt(LocalDateTime.now());
            commandAuditMapper.updateById(audit);

            if (!result.isSuccessful()) {
                log.warn("开闸失败: deviceId={}, auditId={}, deviceCode={}, message={}",
                        deviceId, audit.getId(), result.getDeviceCode(), result.getMessage());
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "开闸失败: " + (result.getMessage() != null ? result.getMessage() : "设备返回异常"));
            }

            log.info("开闸成功: deviceId={}, auditId={}", deviceId, audit.getId());

        } catch (BusinessException e) {
            // 网络异常或 DA 错误（UNCERTAIN）
            audit.setStatus(AUDIT_STATUS_UNCERTAIN);
            audit.setUncertain(true);
            audit.setErrorCode(String.valueOf(e.getCode()));
            audit.setErrorMessage(e.getMessage());
            audit.setUpdatedAt(LocalDateTime.now());
            commandAuditMapper.updateById(audit);

            log.error("开闸异常（UNCERTAIN）: deviceId={}, auditId={}, error={}",
                    deviceId, audit.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 构造开闸请求上下文 JSON（仅用于审计）。
     */
    private String buildOpenGateRequestPayload(Device device, ParkingLot lot) {
        return "{\"deviceId\":" + device.getId()
                + ",\"deviceSn\":\"" + escapeJson(device.getDeviceSn())
                + "\",\"deviceName\":\"" + escapeJson(device.getName())
                + "\",\"parkingLotId\":" + lot.getId()
                + ",\"laneId\":" + (device.getLaneId() != null ? device.getLaneId() : "null")
                + ",\"executorDeviceId\":" + (device.getExecutorDeviceId() != null ? device.getExecutorDeviceId() : "null")
                + ",\"commandType\":\"" + COMMAND_TYPE_OPEN_GATE + "\"}";
    }

    /**
     * 简单 JSON 字符串转义。
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== 设备控制（T5: v0.4 开闸/关闸/显示屏/语音） ====================

    /**
     * 按车道远程开闸（Phase 1 B2 运营端远程开闸）。
     * <p>
     * 根据车道 ID 查找绑定的 GATE 设备，再委托 {@link #openGate(Long, String)} 执行开闸。
     * <b>禁止自动重试</b>；网络错误或命令超时标记为 UNCERTAIN。
     *
     * @param laneId 车道 ID
     * @param reason 操作原因
     * @return 命令执行结果
     */
    @Transactional
    public CommandResultDTO openGateByLane(Long laneId, String reason) {
        // 1. 查找车道
        ParkingLane lane = laneMapper.selectByIdIgnoreTenant(laneId);
        if (lane == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车道不存在: laneId=" + laneId);
        }
        if (lane.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车道已被删除: laneId=" + laneId);
        }

        // 2. 校验停车场归属
        DataScope.validateTenantMatch(lane.getTenantId(), "车道");
        scopeResolver.validateAccess(lane.getLotId());

        // 3. 查找绑定的 GATE 设备
        Device gateDevice = deviceMapper.selectByLaneIdAndTypeIgnoreTenant(laneId, "GATE");
        if (gateDevice == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车道未绑定道闸设备: laneId=" + laneId + " laneName=" + lane.getName());
        }

        log.info("按车道远程开闸: laneId={}, laneName={}, gateDeviceId={}, reason={}",
                laneId, lane.getName(), gateDevice.getId(), reason);

        // 4. 委托给设备开闸
        return openGate(gateDevice.getId(), reason);
    }

    /**
     * 开闸（调用 DA v0.4）。
     * <p>
     * 支持 GATE 类型设备和具备开闸能力的 CAMERA 设备。
     * <b>禁止自动重试</b>；网络错误或命令超时标记为 UNCERTAIN。
     *
     * @param deviceId 平台设备 ID
     * @param reason   操作原因
     * @return 命令执行结果
     */
    @Transactional
    public CommandResultDTO openGate(Long deviceId, String reason) {
        return openGate(deviceId, reason, null, null);
    }

    /**
     * 开闸（调用 DA v0.4），支持传入车牌号和费用用于审计记录。
     * <p>
     * 与 {@link #openGate(Long, String)} 逻辑一致，额外允许在审计记录中写入
     * {@code plateNumber} 和 {@code feeCents}，供岗亭端手工计费/免费放行场景使用。
     *
     * @param deviceId    平台设备 ID
     * @param reason      操作原因
     * @param plateNumber 车牌号（可选）
     * @param feeCents    计费金额（分，可选）
     * @return 命令执行结果
     */
    @Transactional
    public CommandResultDTO openGate(Long deviceId, String reason, String plateNumber, Integer feeCents) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不能开闸");
        }

        String deviceType = device.getDeviceType();
        if (!"GATE".equals(deviceType) && !"CAMERA".equals(deviceType)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅道闸（GATE）或相机（CAMERA）设备支持开闸");
        }

        String deviceSn = device.getDeviceSn();
        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());
        Long tenantId = lot.getTenantId();
        LocalDateTime now = LocalDateTime.now();

        // 生成幂等 commandId
        String commandId = UUID.randomUUID().toString();

        // 构造命令审计记录
        DeviceCommandAudit audit = buildCommandAudit(device, lot, tenantId,
                COMMAND_TYPE_OPEN_GATE, reason, null, now);
        audit.setCommandId(commandId);
        audit.setPlateNumber(plateNumber);
        audit.setFeeCents(feeCents);

        try {
            // 统一通过 DeviceAccessClient 开闸（内部含重试）
            CommandResultDTO result = deviceAccessClient.openGate(deviceSn, commandId);

            audit.setStatus(result.isSuccessful() ? AUDIT_STATUS_SUCCESS : AUDIT_STATUS_FAILED);
            audit.setUncertain(false);
            audit.setResponsePayload(buildCommandResponseJson(result));
            audit.setCompletedAt(LocalDateTime.now());
            audit.setUpdatedAt(LocalDateTime.now());
            commandAuditMapper.updateById(audit);

            log.info("开闸完成: deviceId={}, auditId={}, commandId={}, success={}, deviceCode={}",
                    deviceId, audit.getId(), commandId, result.getSuccess(), result.getDeviceCode());

            if (!result.isSuccessful()) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "开闸失败: " + (result.getMessage() != null ? result.getMessage() : "设备返回异常"));
            }
            return result;

        } catch (BusinessException e) {
            audit.setStatus(AUDIT_STATUS_UNCERTAIN);
            audit.setUncertain(true);
            audit.setErrorCode(String.valueOf(e.getCode()));
            audit.setErrorMessage(e.getMessage());
            audit.setUpdatedAt(LocalDateTime.now());
            commandAuditMapper.updateById(audit);

            log.error("开闸异常（UNCERTAIN）: deviceId={}, auditId={}, commandId={}, error={}",
                    deviceId, audit.getId(), commandId, e.getMessage());
            throw e;
        }
    }

    /**
     * 检查设备是否具备 OPEN_GATE 能力。
     * <p>
     * 任务包 7-1：CAMERA GPIO 控制已收敛至 DeviceAccessClient → Adapter，
     * Adapter 侧根据设备类型自动选择 gate_direct_open 或 gpio_out 协议。
     * 本方法仅保留用于日志提示，不再执行 GPIO 旁路调用。
     */
    private boolean hasOpenGateCapability(Device device) {
        String capabilities = device.getCapabilities();
        boolean hasCap = capabilities != null && capabilities.contains("OPEN_GATE");
        if (hasCap && "CAMERA".equals(device.getDeviceType())) {
            log.info("CAMERA 设备具备 OPEN_GATE 能力，将通过 Adapter 下发: deviceSn={}", device.getDeviceSn());
        }
        return hasCap;
    }

    /**
     * 构造 CommandResultDTO。
     */
    private CommandResultDTO buildCommandResult(boolean success, int deviceCode, String message) {
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(success);
        dto.setDeviceCode(deviceCode);
        dto.setMessage(message);
        return dto;
    }

    /**
     * 关闸（调用 DA v0.4）。
     * <p>
     * 支持 GATE 类型设备和具备关闸能力的 CAMERA 设备。
     * <b>禁止自动重试</b>；网络错误或命令超时标记为 UNCERTAIN。
     *
     * @param deviceId 平台设备 ID
     * @param reason   操作原因
     * @return 命令执行结果
     */
    @Transactional
    public CommandResultDTO closeGate(Long deviceId, String reason) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不能关闸");
        }

        String deviceType = device.getDeviceType();
        if (!"GATE".equals(deviceType) && !"CAMERA".equals(deviceType)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅道闸（GATE）或相机（CAMERA）设备支持关闸");
        }

        String deviceSn = device.getDeviceSn();
        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());
        Long tenantId = lot.getTenantId();
        LocalDateTime now = LocalDateTime.now();

        String commandId = UUID.randomUUID().toString();

        DeviceCommandAudit audit = buildCommandAudit(device, lot, tenantId,
                "CLOSE_GATE", reason, null, now);
        audit.setCommandId(commandId);

        try {
            CommandResultDTO result = deviceAccessClient.closeGate(deviceSn, commandId);

            audit.setStatus(result.isSuccessful() ? AUDIT_STATUS_SUCCESS : AUDIT_STATUS_FAILED);
            audit.setUncertain(false);
            audit.setResponsePayload(buildCommandResponseJson(result));
            audit.setCompletedAt(LocalDateTime.now());
            audit.setUpdatedAt(LocalDateTime.now());
            commandAuditMapper.updateById(audit);

            log.info("关闸完成: deviceId={}, auditId={}, commandId={}, success={}, deviceCode={}",
                    deviceId, audit.getId(), commandId, result.getSuccess(), result.getDeviceCode());

            if (!result.isSuccessful()) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "关闸失败: " + (result.getMessage() != null ? result.getMessage() : "设备返回异常"));
            }
            return result;

        } catch (BusinessException e) {
            audit.setStatus(AUDIT_STATUS_UNCERTAIN);
            audit.setUncertain(true);
            audit.setErrorCode(String.valueOf(e.getCode()));
            audit.setErrorMessage(e.getMessage());
            audit.setUpdatedAt(LocalDateTime.now());
            commandAuditMapper.updateById(audit);

            log.error("关闸异常（UNCERTAIN）: deviceId={}, auditId={}, commandId={}, error={}",
                    deviceId, audit.getId(), commandId, e.getMessage());
            throw e;
        }
    }

    /**
     * 显示屏实时文字（调用 DA v0.4）。
     *
     * @param deviceId  平台设备 ID
     * @param content   显示内容
     * @param direction 显示方向（HORIZONTAL / VERTICAL）
     * @param fontSize  字体大小
     * @param color     文字颜色
     * @return 显示结果
     */
    @Transactional
    public DisplayResultDTO displayText(Long deviceId, String content, String direction,
                                         Integer fontSize, String color) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不支持显示屏控制");
        }

        if (!"CAMERA".equals(device.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅相机（CAMERA）设备支持显示屏控制");
        }

        String deviceSn = device.getDeviceSn();
        DisplayTextRequest request = new DisplayTextRequest(content, direction, fontSize, color);

        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());
        Long tenantId = lot.getTenantId();
        String resultStatus = "SUCCESS";
        String failReason = "";

        log.info("发送显示屏文字: deviceId={}, deviceSn={}, content={}, direction={}",
                deviceId, deviceSn, content, direction);

        try {
            DisplayResultDTO result = deviceAccessClient.displayText(deviceSn, request);
            log.info("显示屏文字发送成功: deviceId={}, success={}", deviceId, result.getSuccess());
            if (!result.isSuccessful()) {
                resultStatus = "FAILED";
                failReason = result.getMessage();
            }
            writeControlAuditLog(device, lot, tenantId, "display_text", resultStatus, failReason, content);
            return result;
        } catch (BusinessException e) {
            resultStatus = "FAILED";
            failReason = e.getMessage();
            writeControlAuditLog(device, lot, tenantId, "display_text", resultStatus, failReason, content);
            log.error("显示屏文字发送失败: deviceId={}, error={}", deviceId, e.getMessage());
            throw e;
        }
    }

    /**
     * 显示屏配置（音量/亮度/时间同步）（调用 DA v0.4）。
     *
     * @param deviceId   平台设备 ID
     * @param configType 配置类型（VOLUME / BRIGHTNESS / TIME_SYNC）
     * @param intValue   整数型配置值
     * @param stringValue 字符串型配置值
     * @return 配置结果
     */
    @Transactional
    public DisplayResultDTO displayConfig(Long deviceId, String configType,
                                           Integer intValue, String stringValue) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不支持显示屏配置");
        }

        if (!"CAMERA".equals(device.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅相机（CAMERA）设备支持显示屏配置");
        }

        String deviceSn = device.getDeviceSn();
        DisplayConfigRequest request = new DisplayConfigRequest(configType, intValue, stringValue);

        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());
        Long tenantId = lot.getTenantId();
        String resultStatus = "SUCCESS";
        String failReason = "";

        log.info("发送显示屏配置: deviceId={}, deviceSn={}, configType={}, intValue={}",
                deviceId, deviceSn, configType, intValue);

        try {
            DisplayResultDTO result = deviceAccessClient.displayConfig(deviceSn, request);
            log.info("显示屏配置成功: deviceId={}, success={}", deviceId, result.getSuccess());
            if (!result.isSuccessful()) {
                resultStatus = "FAILED";
                failReason = result.getMessage();
            }
            writeControlAuditLog(device, lot, tenantId, "display_config", resultStatus, failReason,
                    configType + "=" + intValue);
            return result;
        } catch (BusinessException e) {
            resultStatus = "FAILED";
            failReason = e.getMessage();
            writeControlAuditLog(device, lot, tenantId, "display_config", resultStatus, failReason,
                    configType + "=" + intValue);
            log.error("显示屏配置失败: deviceId={}, error={}", deviceId, e.getMessage());
            throw e;
        }
    }

    /**
     * 语音播报（调用 DA v0.4）。
     *
     * @param deviceId 平台设备 ID
     * @param action   操作（PLAY / STOP）
     * @param voiceId  语音模板 ID
     * @param variable 变量参数（如车牌号）
     * @return 播报结果
     */
    @Transactional
    public VoiceResultDTO voiceControl(Long deviceId, String action, Integer voiceId, String variable) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不支持语音播报");
        }

        if (!"CAMERA".equals(device.getDeviceType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅相机（CAMERA）设备支持语音播报");
        }

        String deviceSn = device.getDeviceSn();
        VoiceControlRequest request = new VoiceControlRequest(action, voiceId, variable);

        ParkingLot lot = getParkingLotWithAuth(device.getParkingLotId());
        Long tenantId = lot.getTenantId();
        String resultStatus = "SUCCESS";
        String failReason = "";

        log.info("发送语音播报: deviceId={}, deviceSn={}, action={}, voiceId={}, variable={}",
                deviceId, deviceSn, action, voiceId, variable);

        try {
            VoiceResultDTO result = deviceAccessClient.voiceControl(deviceSn, request);
            log.info("语音播报成功: deviceId={}, success={}", deviceId, result.getSuccess());
            if (!result.isSuccessful()) {
                resultStatus = "FAILED";
                failReason = result.getMessage();
            }
            writeControlAuditLog(device, lot, tenantId, "voice_control", resultStatus, failReason,
                    "action=" + action + ",voiceId=" + voiceId);
            return result;
        } catch (BusinessException e) {
            resultStatus = "FAILED";
            failReason = e.getMessage();
            writeControlAuditLog(device, lot, tenantId, "voice_control", resultStatus, failReason,
                    "action=" + action + ",voiceId=" + voiceId);
            log.error("语音播报失败: deviceId={}, error={}", deviceId, e.getMessage());
            throw e;
        }
    }

    /**
     * 构造命令审计记录（通用）。
     */
    private DeviceCommandAudit buildCommandAudit(Device device, ParkingLot lot, Long tenantId,
                                                  String commandType, String reason,
                                                  String previousCommandId, LocalDateTime now) {
        DeviceCommandAudit audit = new DeviceCommandAudit();
        audit.setTenantId(tenantId);
        audit.setParkingLotId(device.getParkingLotId());
        audit.setLaneId(device.getLaneId());
        audit.setDeviceId(device.getId());
        audit.setDeviceSn(device.getDeviceSn());
        audit.setCommandType(commandType);
        audit.setSource(SOURCE_MANUAL);
        audit.setReason(reason != null ? reason : "");
        audit.setPreviousCommandId(previousCommandId);
        audit.setRequestPayload(buildCommandRequestPayload(device, lot, commandType));
        audit.setIssuedAt(now);
        audit.setCreatedAt(now);
        audit.setUpdatedAt(now);

        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx != null) {
            audit.setOperatorId(ctx.userId());
            audit.setOperatorName("userId=" + (ctx.userId() != null ? ctx.userId() : "unknown"));
        } else {
            audit.setOperatorId(0L);
            audit.setOperatorName("system");
        }

        commandAuditMapper.insert(audit);
        return audit;
    }

    /**
     * 构造命令请求上下文 JSON（通用，用于审计）。
     */
    private String buildCommandRequestPayload(Device device, ParkingLot lot, String commandType) {
        return "{\"deviceId\":" + device.getId()
                + ",\"deviceSn\":\"" + escapeJson(device.getDeviceSn())
                + "\",\"deviceName\":\"" + escapeJson(device.getName())
                + "\",\"parkingLotId\":" + lot.getId()
                + ",\"laneId\":" + (device.getLaneId() != null ? device.getLaneId() : "null")
                + ",\"executorDeviceId\":" + (device.getExecutorDeviceId() != null ? device.getExecutorDeviceId() : "null")
                + ",\"commandType\":\"" + escapeJson(commandType) + "\"}";
    }

    /**
     * 构造命令响应 JSON（通用，用于审计）。
     */
    private String buildCommandResponseJson(CommandResultDTO result) {
        return "{\"success\":" + result.getSuccess()
                + ",\"deviceCode\":" + result.getDeviceCode()
                + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}";
    }

    /**
     * 写入设备控制操作审计日志（通用，用于显示屏/语音等非开闸操作）。
     */
    private void writeControlAuditLog(Device device, ParkingLot lot, Long tenantId,
                                       String action, String result, String failReason, String detail) {
        SysAuditLog audit = new SysAuditLog();
        audit.setTenantId(tenantId);
        audit.setTargetType("device");
        audit.setTargetId(String.valueOf(device.getId()));
        audit.setAction(action);

        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx != null) {
            audit.setOperatorId(ctx.userId() != null ? ctx.userId() : 0L);
            audit.setOperatorName("userId=" + (ctx.userId() != null ? ctx.userId() : "unknown"));
            audit.setIsProxy(0);
        } else {
            audit.setOperatorId(0L);
            audit.setOperatorName("system");
            audit.setIsProxy(0);
        }

        audit.setAfterValue("{\"deviceSn\":\"" + escapeJson(device.getDeviceSn())
                + "\",\"deviceName\":\"" + escapeJson(device.getName())
                + "\",\"parkingLotId\":" + lot.getId()
                + ",\"detail\":\"" + escapeJson(detail != null ? detail : "") + "\"}");

        audit.setResult(result);
        audit.setFailReason(failReason != null ? failReason : "");
        audit.setReason("");

        auditLogMapper.insert(audit);
    }

    // ==================== 设备状态查询与快照（T24） ====================

    /**
     * 查询单个设备实时状态（调用 DA）。
     * <p>
     * 每次调用都会向 Device Access 发起 HTTP 请求并持久化快照。
     * 此操作有网络开销，应仅在用户主动点击"刷新状态"时调用，
     * 列表轮询应使用 {@link #getLatestSnapshot(Long)}。
     *
     * @param deviceId 平台设备 ID
     * @return 最新状态（含采集时间）
     */
    public DeviceStatusVO queryStatus(Long deviceId) {
        Device device = getDeviceWithAuth(deviceId);

        if (!STATUS_ENABLED.equals(device.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已停用的设备不支持状态查询");
        }

        String deviceSn = device.getDeviceSn();
        LocalDateTime collectedAt = LocalDateTime.now();

        DeviceStatusSnapshot snapshot = new DeviceStatusSnapshot();
        snapshot.setDeviceId(deviceId);
        snapshot.setDeviceSn(deviceSn);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setCreatedAt(LocalDateTime.now());

        try {
            DeviceStatusDTO dto = deviceAccessClient.getStatus(deviceSn);

            // 成功：填充 DA 返回值
            snapshot.setQuerySuccess(true);
            snapshot.setOnline(dto.getOnline());
            snapshot.setGateStatus(dto.getGateStatus());
            snapshot.setGateConnectStatus(dto.getGateConnectStatus());
            snapshot.setStatusDescription(dto.getStatus());

            // 解析 lastOnlineTime 字符串为 LocalDateTime
            if (dto.getLastOnlineTime() != null && !dto.getLastOnlineTime().isEmpty()) {
                try {
                    snapshot.setLastOnlineTime(LocalDateTime.parse(
                            dto.getLastOnlineTime(), FORMATTER));
                } catch (DateTimeParseException e) {
                    log.warn("解析 lastOnlineTime 失败: deviceSn={}, value={}", deviceSn, dto.getLastOnlineTime());
                }
            }

            log.info("设备状态查询成功: deviceId={}, deviceSn={}, online={}, gateStatus={}",
                    deviceId, deviceSn, dto.getOnline(), dto.getGateStatus());

            // 主备切换：检测到离线时触发故障切换
            if (Boolean.FALSE.equals(dto.getOnline()) && "CAMERA".equals(device.getDeviceType())) {
                cameraFailoverService.onDeviceOffline(deviceId, deviceSn);
            } else if (Boolean.TRUE.equals(dto.getOnline()) && "CAMERA".equals(device.getDeviceType())) {
                cameraFailoverService.onDeviceOnline(deviceId);
            }

        } catch (BusinessException e) {
            // DA 返回错误或网络异常：持久化失败快照
            snapshot.setQuerySuccess(false);
            snapshot.setErrorMessage(e.getMessage());
            // 根据异常消息提取错误码分类
            if (e.getMessage() != null) {
                if (e.getMessage().contains("UNCERTAIN")) {
                    snapshot.setErrorCode("UNCERTAIN");
                } else if (e.getMessage().contains("404")) {
                    snapshot.setErrorCode("404");
                } else if (e.getMessage().contains("503")) {
                    snapshot.setErrorCode("503");
                } else if (e.getMessage().contains("500")) {
                    snapshot.setErrorCode("500");
                } else {
                    snapshot.setErrorCode("UNKNOWN");
                }
            }
            log.warn("设备状态查询失败: deviceId={}, deviceSn={}, error={}",
                    deviceId, deviceSn, e.getMessage());
        }

        snapshotMapper.insert(snapshot);
        return toStatusVO(device, snapshot);
    }

    /** 批量查询最大设备数（FIX-13：防止单次请求占用线程过久） */
    private static final int MAX_BATCH_QUERY_SIZE = 50;

    /** 批量查询并发度（FIX-13：限制同时调用的 DA 请求数） */
    private static final int BATCH_QUERY_CONCURRENCY = 5;

    /**
     * 批量查询设备实时状态（受控并发执行，FIX-13）。
     * <p>
     * 使用信号量控制并发度（限制同时调用的 DA 状态查询请求数），
     * 单次请求最多查询 {@value #MAX_BATCH_QUERY_SIZE} 台设备，
     * 单个设备失败不影响其他设备。
     *
     * @param deviceIds 设备 ID 列表
     * @return 各设备的状态视图列表（按查询完成顺序）
     */
    public List<DeviceStatusVO> queryStatusBatch(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }

        // FIX-13：单次请求上限校验
        if (deviceIds.size() > MAX_BATCH_QUERY_SIZE) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "单次批量状态查询最多支持 " + MAX_BATCH_QUERY_SIZE + " 台设备，当前请求 " + deviceIds.size() + " 台");
        }

        List<DeviceStatusVO> results = new ArrayList<>();
        java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(BATCH_QUERY_CONCURRENCY);
        List<Thread> workers = new ArrayList<>();
        List<Exception> workerErrors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (Long deviceId : deviceIds) {
            Thread worker = Thread.ofVirtual().start(() -> {
                try {
                    semaphore.acquire();
                    try {
                        DeviceStatusVO vo = queryStatus(deviceId);
                        synchronized (results) {
                            results.add(vo);
                        }
                    } finally {
                        semaphore.release();
                    }
                } catch (BusinessException e) {
                    log.warn("批量状态查询跳过设备: deviceId={}, reason={}", deviceId, e.getMessage());
                    synchronized (results) {
                        results.add(createErrorStatusVO(deviceId, "SKIPPED", e.getMessage()));
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.error("批量状态查询异常: deviceId={}", deviceId, e);
                    workerErrors.add(e);
                    synchronized (results) {
                        results.add(createErrorStatusVO(deviceId, "ERROR",
                                e.getMessage() != null ? e.getMessage() : "unknown error"));
                    }
                }
            });
            workers.add(worker);
        }

        // 等待所有 worker 完成
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("批量状态查询被中断");
                break;
            }
        }

        log.info("批量状态查询完成: 请求 {} 台设备，成功获取 {} 条结果",
                deviceIds.size(), results.size());
        return results;
    }

    /**
     * 获取设备最新状态快照（不触发 DA 调用）。
     * <p>
     * 返回最近的持久化快照，用于列表轮询和详情展示。
     * 调用方应通过 {@link DeviceStatusVO#getStale()} 判断快照新鲜度。
     *
     * @param deviceId 平台设备 ID
     * @return 最新状态视图（可能为从未查询过的空快照）
     */
    public DeviceStatusVO getLatestSnapshot(Long deviceId) {
        Device device = getDeviceWithAuth(deviceId);

        DeviceStatusSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<DeviceStatusSnapshot>()
                        .eq(DeviceStatusSnapshot::getDeviceId, deviceId)
                        .orderByDesc(DeviceStatusSnapshot::getCollectedAt)
                        .last("LIMIT 1"));

        return toStatusVO(device, snapshot);
    }

    /**
     * 批量获取设备最新状态快照。
     *
     * @param deviceIds 设备 ID 列表
     * @return 各设备的状态视图列表
     */
    public List<DeviceStatusVO> getLatestSnapshots(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DeviceStatusVO> results = new ArrayList<>();
        for (Long deviceId : deviceIds) {
            try {
                results.add(getLatestSnapshot(deviceId));
            } catch (BusinessException e) {
                log.warn("获取设备快照跳过: deviceId={}, reason={}", deviceId, e.getMessage());
            }
        }
        return results;
    }

    // ==================== 状态 VO 构造 ====================

    /**
     * 构造 DeviceStatusVO。
     *
     * @param device   设备实体（非 null）
     * @param snapshot 最新快照（可能为 null，表示从未查询过）
     */
    private DeviceStatusVO toStatusVO(Device device, DeviceStatusSnapshot snapshot) {
        DeviceStatusVO vo = new DeviceStatusVO();
        vo.setDeviceId(device.getId());
        vo.setDeviceName(device.getName());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceType(device.getDeviceType());
        vo.setDeviceStatus(device.getStatus());

        if (snapshot != null) {
            vo.setOnline(snapshot.getOnline());
            vo.setLastOnlineTime(snapshot.getLastOnlineTime());
            vo.setGateStatus(snapshot.getGateStatus());
            vo.setGateConnectStatus(snapshot.getGateConnectStatus());
            vo.setStatusDescription(snapshot.getStatusDescription());
            vo.setCollectedAt(snapshot.getCollectedAt());
            vo.setLastQuerySuccess(snapshot.getQuerySuccess());
            vo.setLastErrorCode(snapshot.getErrorCode());
            vo.setLastErrorMessage(snapshot.getErrorMessage());

            // 计算快照新鲜度
            if (snapshot.getCollectedAt() != null) {
                long ageSeconds = Duration.between(snapshot.getCollectedAt(), LocalDateTime.now()).getSeconds();
                vo.setSnapshotAgeSeconds(ageSeconds);
                vo.setStale(ageSeconds > STALE_THRESHOLD_SECONDS);
            }
        }
        // snapshot 为 null 时所有状态字段保持 null，前端识别为"从未查询"

        return vo;
    }

    /**
     * 为批量查询中不可达的设备创建错误 VO。
     */
    private DeviceStatusVO createErrorStatusVO(Long deviceId, String errorCode, String errorMessage) {
        DeviceStatusVO vo = new DeviceStatusVO();
        vo.setDeviceId(deviceId);
        vo.setLastQuerySuccess(false);
        vo.setLastErrorCode(errorCode);
        vo.setLastErrorMessage(errorMessage);

        // 尝试读取设备基础信息
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) {
            vo.setDeviceName(device.getName());
            vo.setDeviceCode(device.getCode());
            vo.setDeviceType(device.getDeviceType());
            vo.setDeviceStatus(device.getStatus());
        }

        return vo;
    }

    /** 解析 DA 返回的时间字符串格式（yyyy-MM-dd HH:mm:ss） */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 私有方法 ====================

    /**
     * 查询设备并校验停车场归属（通过停车场 → 租户链）。
     */
    private Device getDeviceWithAuth(Long deviceId) {
        Device device = deviceMapper.selectByIdIgnoreTenant(deviceId);
        if (device == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "设备不存在");
        }
        // 通过停车场校验租户归属
        getParkingLotWithAuth(device.getParkingLotId());
        return device;
    }

    /**
     * 查询停车场并校验租户归属 + 停车场级授权（fail-close）。
     * <p>
     * <strong>P0 停车场级数据隔离</strong>：在租户校验通过后，额外校验当前用户
     * 是否有权访问该停车场。设备运维等受限角色仅可访问授权停车场的设备。
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
     * 查询厂商并确认启用。
     */
    private DeviceVendor getVendorEnabled(Long vendorId) {
        DeviceVendor vendor = vendorMapper.selectById(vendorId);
        if (vendor == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "设备厂商不存在");
        }
        if (!STATUS_ENABLED.equals(vendor.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备厂商 '" + vendor.getName() + "' 已停用");
        }
        return vendor;
    }

    /**
     * 查询型号并确认属于指定厂商且启用。
     */
    private DeviceModel getModelEnabled(Long modelId, Long vendorId) {
        DeviceModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "设备型号不存在");
        }
        if (!vendorId.equals(model.getVendorId())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备型号不属于所选厂商");
        }
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "设备型号 '" + model.getName() + "' 已停用");
        }
        return model;
    }

    /**
     * 实体转视图。
     */
    private DeviceVO toVO(Device device, DeviceVendor vendor, DeviceModel model) {
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

    private static String defaultString(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
