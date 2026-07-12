package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.framework.auth.TenantContext;
import com.jushan.system.dto.CreateParkingLotRequest;
import com.jushan.system.dto.ParkingLotCapacityRequest;
import com.jushan.system.dto.ParkingLotStatusRequest;
import com.jushan.system.dto.UpdateParkingLotRequest;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingLotCapacityLog;
import com.jushan.system.entity.ParkingLotStatusLog;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.ParkingLotCapacityLogMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingLotStatusLogMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.vo.ParkingLotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 停车场服务。
 * <p>
 * 负责停车场 CRUD、状态启用/停用、容量管理和变更审计。
 * 所有操作从当前登录会话推导租户范围，不信任前端传入的 tenantId。
 * <p>
 * <strong>权限差异（T18 停止条件）</strong>：
 * <ul>
 *   <li>停用停车场：仅超级管理员（总后台）和客户管理员，停车场管理员无权</li>
 *   <li>修改总车位：客户管理员和停车场管理员均可</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ParkingLotService {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotService.class);

    /** 停车场状态常量 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    /** 新停车场初始状态（FIX-08：不再默认启用，需通过就绪检查后才能启用） */
    public static final String INITIAL_STATUS = STATUS_DISABLED;

    /** 容量变更字段名 */
    public static final String FIELD_TOTAL_SPACES = "total_spaces";
    public static final String FIELD_REMAINING_SPACES = "remaining_spaces";

    /** 状态操作类型 */
    private static final String ACTION_ENABLED = "ENABLED";
    private static final String ACTION_DISABLED = "DISABLED";

    /** 合法的容量变更字段 */
    private static final List<String> VALID_CAPACITY_FIELDS = List.of(FIELD_TOTAL_SPACES, FIELD_REMAINING_SPACES);

    /** 支付模式默认值 */
    private static final String DEFAULT_PAYMENT_MODE = "PLATFORM";
    /** 图片保存天数默认值 */
    private static final int DEFAULT_IMAGE_RETENTION_DAYS = 30;
    /** 业务数据保存天数默认值 */
    private static final int DEFAULT_DATA_RETENTION_DAYS = 365;
    /** 免费离场分钟默认值 */
    private static final int DEFAULT_FREE_EXIT_MINUTES = 15;
    /** 人工放行策略默认值 */
    private static final String DEFAULT_MANUAL_RELEASE_POLICY = "ADMIN_ONLY";
    /** 离线策略默认值 */
    private static final String DEFAULT_OFFLINE_POLICY = "ALLOW_ENTRY_EXIT";

    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLotCapacityLogMapper capacityLogMapper;
    private final ParkingLotStatusLogMapper statusLogMapper;
    private final TenantMapper tenantMapper;
    private final ParkingLotScopeResolver scopeResolver;
    private final ReadinessCheckService readinessCheckService;

    public ParkingLotService(ParkingLotMapper parkingLotMapper,
                             ParkingLotCapacityLogMapper capacityLogMapper,
                             ParkingLotStatusLogMapper statusLogMapper,
                             TenantMapper tenantMapper,
                             ParkingLotScopeResolver scopeResolver,
                             ReadinessCheckService readinessCheckService) {
        this.parkingLotMapper = parkingLotMapper;
        this.capacityLogMapper = capacityLogMapper;
        this.statusLogMapper = statusLogMapper;
        this.tenantMapper = tenantMapper;
        this.scopeResolver = scopeResolver;
        this.readinessCheckService = readinessCheckService;
    }

    // ==================== 创建停车场 ====================

    /**
     * 创建停车场。
     * <p>
     * 仅客户管理员可操作。新停车场的租户从当前会话推导。
     * 创建时自动计算剩余车位（= totalSpaces - currentVehicles）。
     *
     * @param request 创建请求
     * @return 停车场视图
     */
    @Transactional
    public ParkingLotVO create(CreateParkingLotRequest request) {
        Long tenantId = DataScope.requireTenantUser();
        DataScope.requireCustomerAdmin();

        // 校验租户状态
        Tenant tenant = tenantMapper.selectById(tenantId);
        DataScope.validateTenantEnabled(tenant != null ? tenant.getStatus() : null);

        ParkingLot lot = new ParkingLot();
        lot.setTenantId(tenantId);
        lot.setName(request.getName().trim());
        lot.setAddress(defaultString(request.getAddress(), ""));
        lot.setContactPhone(defaultString(request.getContactPhone(), ""));
        lot.setLongitude(request.getLongitude());
        lot.setLatitude(request.getLatitude());

        int totalSpaces = request.getTotalSpaces();
        lot.setTotalSpaces(totalSpaces);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(totalSpaces); // 默认 = 总车位 - 0

        // FIX-08：新停车场默认停用，必须通过就绪检查后才能启用
        lot.setStatus(INITIAL_STATUS);
        lot.setPaymentMode(defaultString(request.getPaymentMode(), DEFAULT_PAYMENT_MODE));
        lot.setImageRetentionDays(defaultInt(request.getImageRetentionDays(), DEFAULT_IMAGE_RETENTION_DAYS));
        lot.setDataRetentionDays(defaultInt(request.getDataRetentionDays(), DEFAULT_DATA_RETENTION_DAYS));
        lot.setFreeExitMinutes(defaultInt(request.getFreeExitMinutes(), DEFAULT_FREE_EXIT_MINUTES));
        lot.setManualReleasePolicy(defaultString(request.getManualReleasePolicy(), DEFAULT_MANUAL_RELEASE_POLICY));
        lot.setOfflinePolicy(defaultString(request.getOfflinePolicy(), DEFAULT_OFFLINE_POLICY));

        // 停用默认为开启状态（所有能力保留）
        lot.setDisableNewEntries(1);
        lot.setDisablePayment(1);
        lot.setDisableExit(1);
        lot.setDisableAutoGate(0);
        lot.setDisableOnlyConfig(1);

        lot.setCreatedAt(LocalDateTime.now());
        lot.setUpdatedAt(LocalDateTime.now());
        parkingLotMapper.insert(lot);

        log.info("创建停车场成功: tenantId={}, parkingLotId={}, name={}, totalSpaces={}",
                tenantId, lot.getId(), lot.getName(), totalSpaces);

        return toVO(lot);
    }

    // ==================== 更新停车场 ====================

    /**
     * 更新停车场基础信息（不含容量字段和状态）。
     * <p>
     * 仅客户管理员和停车场管理员可操作。停车场管理员只能更新已授权停车场。
     *
     * @param lotId   停车场 ID
     * @param request 更新请求（仅非 null 字段被更新）
     * @return 停车场视图
     */
    @Transactional
    public ParkingLotVO update(Long lotId, UpdateParkingLotRequest request) {
        ParkingLot lot = getParkingLotWithAuth(lotId);

        LambdaUpdateWrapper<ParkingLot> wrapper = new LambdaUpdateWrapper<ParkingLot>()
                .eq(ParkingLot::getId, lotId);

        boolean hasUpdate = false;
        if (request.getName() != null) {
            wrapper.set(ParkingLot::getName, request.getName().trim());
            hasUpdate = true;
        }
        if (request.getAddress() != null) {
            wrapper.set(ParkingLot::getAddress, request.getAddress().trim());
            hasUpdate = true;
        }
        if (request.getContactPhone() != null) {
            wrapper.set(ParkingLot::getContactPhone, request.getContactPhone().trim());
            hasUpdate = true;
        }
        if (request.getLongitude() != null) {
            wrapper.set(ParkingLot::getLongitude, request.getLongitude());
            hasUpdate = true;
        }
        if (request.getLatitude() != null) {
            wrapper.set(ParkingLot::getLatitude, request.getLatitude());
            hasUpdate = true;
        }
        if (request.getPaymentMode() != null) {
            wrapper.set(ParkingLot::getPaymentMode, request.getPaymentMode().trim());
            hasUpdate = true;
        }
        if (request.getImageRetentionDays() != null) {
            wrapper.set(ParkingLot::getImageRetentionDays, request.getImageRetentionDays());
            hasUpdate = true;
        }
        if (request.getDataRetentionDays() != null) {
            wrapper.set(ParkingLot::getDataRetentionDays, request.getDataRetentionDays());
            hasUpdate = true;
        }
        if (request.getFreeExitMinutes() != null) {
            wrapper.set(ParkingLot::getFreeExitMinutes, request.getFreeExitMinutes());
            hasUpdate = true;
        }
        if (request.getManualReleasePolicy() != null) {
            wrapper.set(ParkingLot::getManualReleasePolicy, request.getManualReleasePolicy().trim());
            hasUpdate = true;
        }
        if (request.getOfflinePolicy() != null) {
            wrapper.set(ParkingLot::getOfflinePolicy, request.getOfflinePolicy().trim());
            hasUpdate = true;
        }

        if (!hasUpdate) {
            return toVO(lot);
        }

        wrapper.set(ParkingLot::getUpdatedAt, LocalDateTime.now());
        parkingLotMapper.update(null, wrapper);

        ParkingLot updated = parkingLotMapper.selectById(lotId);
        log.info("更新停车场成功: parkingLotId={}", lotId);
        return toVO(updated);
    }

    // ==================== 停车场查询 ====================

    /**
     * 分页查询停车场列表。
     * <p>
     * 租户用户只能查看本租户的停车场；平台用户只能通过总后台接口查看（本接口拒绝平台用户）。
     * <p>
     * <strong>P0 停车场级数据隔离</strong>：
     * 客户管理员（customer_admin）可查看本租户全部停车场；
     * 受限角色（parking_manager / device_maintenance / finance / booth_operator）
     * 仅可查看 employee_parking_lot 明确授权的停车场。
     *
     * @param page   页码
     * @param size   每页大小
     * @param status 状态筛选（可选）
     * @return 分页结果
     */
    public IPage<ParkingLotVO> list(int page, int size, String status) {
        Long tenantId = TenantContext.requireTenantId();

        LambdaQueryWrapper<ParkingLot> wrapper = new LambdaQueryWrapper<ParkingLot>()
                .eq(ParkingLot::getTenantId, tenantId)
                .eq(status != null && !status.isBlank(), ParkingLot::getStatus, status)
                .orderByDesc(ParkingLot::getCreatedAt);

        // P0：停车场级数据范围
        Set<Long> authorizedIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedIds != null) {
            if (authorizedIds.isEmpty()) {
                // 无授权停车场 → 返回空分页
                IPage<ParkingLotVO> emptyPage = new Page<>(page, size);
                emptyPage.setTotal(0);
                return emptyPage;
            }
            wrapper.in(ParkingLot::getId, authorizedIds);
        }

        IPage<ParkingLot> lotPage = parkingLotMapper.selectPage(new Page<>(page, size), wrapper);
        return lotPage.convert(this::toVO);
    }

    /**
     * 查询单个停车场详情。
     *
     * @param lotId 停车场 ID
     * @return 停车场视图
     */
    public ParkingLotVO get(Long lotId) {
        ParkingLot lot = getParkingLotWithAuth(lotId);
        return toVO(lot);
    }

    // ==================== 状态管理（启用/停用） ====================

    /**
     * 启用或停用停车场。
     * <p>
     * <strong>T18 停止条件</strong>：只有总后台（super_admin/platform_operator）和客户管理员可以停用。
     * 停车场管理员<b>无权</b>停用停车场。启用同理。
     * <p>
     * 使用条件更新防止并发覆盖。
     *
     * @param lotId   停车场 ID
     * @param request 状态变更请求
     */
    @Transactional
    public void updateStatus(Long lotId, ParkingLotStatusRequest request) {
        String action = request.getAction().trim();

        // 1. 校验操作类型
        if (!ACTION_ENABLED.equals(action) && !ACTION_DISABLED.equals(action)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的操作类型: " + action + "，仅支持 ENABLED / DISABLED");
        }

        // 2. 权限校验：只有总后台和客户管理员可以停用/启用
        //    停车场管理员和岗亭无权
        if (TenantContext.isPlatformUser()) {
            // 平台用户（含 super_admin 和 platform_operator）：允许
        } else if (TenantContext.isTenantUser()) {
            // 租户用户：必须是 customer_admin（此角色有 parking:disable 权限）
            // 不需要额外代码检查，由 Controller 层的 @SaCheckPermission("parking:disable") 保证
        } else {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }

        // 3. 查询停车场
        ParkingLot lot = getParkingLotWithAuth(lotId);
        String beforeStatus = lot.getStatus();

        // 4. 停用必须填写原因
        if (ACTION_DISABLED.equals(action) && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停用停车场必须填写原因");
        }

        // 5. 状态流转校验
        if (ACTION_ENABLED.equals(action)) {
            if (STATUS_ENABLED.equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场已是启用状态");
            }
            if (!STATUS_DISABLED.equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "只能启用在停用状态的停车场，当前状态: " + beforeStatus);
            }
        } else { // DISABLED
            if (STATUS_DISABLED.equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场已是停用状态");
            }
            if (!STATUS_ENABLED.equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "只能停用启用状态的停车场，当前状态: " + beforeStatus);
            }
        }

        // 5.1 FIX-08：启用前就绪检查（所有进入 ENABLED 的路径统一调用）
        if (ACTION_ENABLED.equals(action)) {
            var readinessResult = readinessCheckService.check(lotId);
            if (!readinessResult.isReady()) {
                List<String> blockerMessages = readinessResult.getItems().stream()
                        .filter(i -> "BLOCKER".equals(i.getLevel()))
                        .map(i -> "[" + i.getCode() + "] " + i.getMessage())
                        .collect(Collectors.toList());
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "停车场未就绪，无法启用。阻塞项: " + String.join("; ", blockerMessages));
            }
            log.info("停车场就绪检查通过: parkingLotId={}, warnings={}",
                    lotId, readinessResult.getWarningCount());
        }

        // 6. 条件更新（防并发）
        LambdaUpdateWrapper<ParkingLot> wrapper = new LambdaUpdateWrapper<ParkingLot>()
                .set(ParkingLot::getStatus, action)
                .set(ParkingLot::getUpdatedAt, LocalDateTime.now())
                .eq(ParkingLot::getId, lotId)
                .eq(ParkingLot::getStatus, beforeStatus);

        // 停用时更新保留范围
        if (ACTION_DISABLED.equals(action)) {
            if (request.getDisableNewEntries() != null) {
                wrapper.set(ParkingLot::getDisableNewEntries, request.getDisableNewEntries());
            }
            if (request.getDisablePayment() != null) {
                wrapper.set(ParkingLot::getDisablePayment, request.getDisablePayment());
            }
            if (request.getDisableExit() != null) {
                wrapper.set(ParkingLot::getDisableExit, request.getDisableExit());
            }
            if (request.getDisableAutoGate() != null) {
                wrapper.set(ParkingLot::getDisableAutoGate, request.getDisableAutoGate());
            }
            if (request.getDisableOnlyConfig() != null) {
                wrapper.set(ParkingLot::getDisableOnlyConfig, request.getDisableOnlyConfig());
            }
        }

        boolean updated = parkingLotMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场状态已变更，请刷新后重试");
        }

        // 7. 写入状态审计日志
        writeStatusLog(lotId, beforeStatus, action, request.getReason());

        log.info("停车场状态变更成功: parkingLotId={}, {} -> {}, reason={}",
                lotId, beforeStatus, action, request.getReason());
    }

    // ==================== 容量管理 ====================

    /**
     * 修改总车位数或人工修正剩余车位数。
     * <p>
     * 客户管理员和停车场管理员均可操作。
     * 操作必须填写原因，修改前后值记录到审计表。
     *
     * @param lotId   停车场 ID
     * @param request 容量变更请求
     */
    @Transactional
    public void updateCapacity(Long lotId, ParkingLotCapacityRequest request) {
        String fieldName = request.getFieldName().trim();
        int newValue = request.getValue();
        String reason = request.getReason().trim();

        // 1. 校验字段名
        if (!VALID_CAPACITY_FIELDS.contains(fieldName)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的容量字段: " + fieldName + "，仅支持 " + String.join(", ", VALID_CAPACITY_FIELDS));
        }

        // 2. 校验停车场归属
        ParkingLot lot = getParkingLotWithAuth(lotId);

        // 3. 获取修改前数值
        int beforeValue;
        if (FIELD_TOTAL_SPACES.equals(fieldName)) {
            beforeValue = lot.getTotalSpaces();
        } else {
            beforeValue = lot.getRemainingSpaces();
        }

        // 4. 没有变化则跳过
        if (beforeValue == newValue) {
            log.info("容量未变化，跳过更新: parkingLotId={}, field={}, value={}", lotId, fieldName, newValue);
            return;
        }

        // 5. 条件更新（使用 beforeValue 乐观锁防止并发覆盖）
        LambdaUpdateWrapper<ParkingLot> wrapper = new LambdaUpdateWrapper<ParkingLot>()
                .set(ParkingLot::getUpdatedAt, LocalDateTime.now())
                .eq(ParkingLot::getId, lotId);

        if (FIELD_TOTAL_SPACES.equals(fieldName)) {
            wrapper.set(ParkingLot::getTotalSpaces, newValue)
                   .eq(ParkingLot::getTotalSpaces, beforeValue);
        } else {
            wrapper.set(ParkingLot::getRemainingSpaces, newValue)
                   .eq(ParkingLot::getRemainingSpaces, beforeValue);
        }

        boolean updated = parkingLotMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "容量已变更，请刷新后重试");
        }

        // 6. 写入容量审计日志
        writeCapacityLog(lotId, fieldName, beforeValue, newValue, reason);

        log.info("停车场容量变更成功: parkingLotId={}, field={}, {} -> {}, reason={}",
                lotId, fieldName, beforeValue, newValue, reason);
    }

    // ==================== 私有方法 ====================

    /**
     * 查询停车场并校验租户归属 + 停车场级授权。
     * <p>
     * 平台用户允许跨租户访问；租户用户必须属于本租户。
     * <p>
     * <strong>P0 停车场级数据隔离</strong>：
     * 在租户校验通过后，额外校验当前用户是否有权访问该停车场。
     * 客户管理员通过全量范围；受限角色需在 employee_parking_lot 中有授权记录。
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
     * 写入容量变更审计日志。
     */
    private void writeCapacityLog(Long parkingLotId, String fieldName,
                                   int beforeValue, int afterValue, String reason) {
        Long operatorId;
        try {
            operatorId = TenantContext.requireUserId();
        } catch (Exception e) {
            log.warn("无法获取操作人 ID，使用 0 占位", e);
            operatorId = 0L;
        }

        ParkingLotCapacityLog logEntry = new ParkingLotCapacityLog();
        logEntry.setParkingLotId(parkingLotId);
        logEntry.setFieldName(fieldName);
        logEntry.setBeforeValue(beforeValue);
        logEntry.setAfterValue(afterValue);
        logEntry.setOperatorId(operatorId);
        logEntry.setReason(reason != null ? reason : "");
        logEntry.setCreatedAt(LocalDateTime.now());
        capacityLogMapper.insert(logEntry);
    }

    /**
     * 写入状态变更审计日志。
     */
    private void writeStatusLog(Long parkingLotId, String beforeStatus,
                                 String afterStatus, String reason) {
        Long operatorId;
        try {
            operatorId = TenantContext.requireUserId();
        } catch (Exception e) {
            log.warn("无法获取操作人 ID，使用 0 占位", e);
            operatorId = 0L;
        }

        ParkingLotStatusLog logEntry = new ParkingLotStatusLog();
        logEntry.setParkingLotId(parkingLotId);
        logEntry.setBeforeStatus(beforeStatus);
        logEntry.setAfterStatus(afterStatus);
        logEntry.setOperatorId(operatorId);
        logEntry.setReason(reason != null ? reason : "");
        logEntry.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(logEntry);
    }

    /**
     * ParkingLot → ParkingLotVO 转换。
     */
    private ParkingLotVO toVO(ParkingLot lot) {
        ParkingLotVO vo = new ParkingLotVO();
        vo.setId(lot.getId());
        vo.setTenantId(lot.getTenantId());
        vo.setName(lot.getName());
        vo.setAddress(lot.getAddress());
        vo.setContactPhone(lot.getContactPhone());
        vo.setLongitude(lot.getLongitude());
        vo.setLatitude(lot.getLatitude());
        vo.setTotalSpaces(lot.getTotalSpaces());
        vo.setCurrentVehicles(lot.getCurrentVehicles());
        vo.setRemainingSpaces(lot.getRemainingSpaces());
        vo.setStatus(lot.getStatus());
        vo.setPaymentMode(lot.getPaymentMode());
        vo.setImageRetentionDays(lot.getImageRetentionDays());
        vo.setDataRetentionDays(lot.getDataRetentionDays());
        vo.setFreeExitMinutes(lot.getFreeExitMinutes());
        vo.setManualReleasePolicy(lot.getManualReleasePolicy());
        vo.setOfflinePolicy(lot.getOfflinePolicy());
        vo.setDisableNewEntries(lot.getDisableNewEntries());
        vo.setDisablePayment(lot.getDisablePayment());
        vo.setDisableExit(lot.getDisableExit());
        vo.setDisableAutoGate(lot.getDisableAutoGate());
        vo.setDisableOnlyConfig(lot.getDisableOnlyConfig());
        vo.setCreatedAt(lot.getCreatedAt());
        vo.setUpdatedAt(lot.getUpdatedAt());
        return vo;
    }

    private static String defaultString(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
}
