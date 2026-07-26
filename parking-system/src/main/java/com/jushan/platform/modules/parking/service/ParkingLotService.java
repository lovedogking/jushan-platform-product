package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.dto.CreateParkingLotRequest;
import com.jushan.platform.modules.parking.dto.ParkingLotCapacityRequest;
import com.jushan.platform.modules.parking.dto.ParkingLotStatusRequest;
import com.jushan.platform.modules.parking.dto.UpdateParkingLotRequest;
import com.jushan.system.entity.Company;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingLotCapacityLog;
import com.jushan.platform.modules.tenant.entity.SysTenant;
import com.jushan.platform.modules.tenant.mapper.SysTenantMapper;
import com.jushan.platform.modules.parking.entity.ParkingLotStatusLog;
import com.jushan.platform.modules.company.mapper.CompanyMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotCapacityLogMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotStatusLogMapper;
import com.jushan.platform.modules.parking.vo.ParkingLotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    /** 重复入场策略默认值 */
    private static final String DEFAULT_DUPLICATE_ENTRY_POLICY = "REJECT";

    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLotCapacityLogMapper capacityLogMapper;
    private final ParkingLotStatusLogMapper statusLogMapper;
    private final SysTenantMapper sysTenantMapper;
    private final CompanyMapper companyMapper;
    private final ParkingLotScopeResolver scopeResolver;
    private final ReadinessCheckService readinessCheckService;

    /**
     * 解析当前租户ID，正确处理平台用户。
     * <p>
     * 平台用户（super_admin、platform_operator）无租户绑定，返回 null。
     * 租户用户返回其 tenantId。
     */
    private static final Long DEFAULT_TENANT_ID = 1L;

    private Long resolveTenantId() {
        if (TenantContext.isPlatformUser()) {
            return null;
        }
        return TenantContext.requireTenantId();
    }

    public ParkingLotService(ParkingLotMapper parkingLotMapper,
                             ParkingLotCapacityLogMapper capacityLogMapper,
                             ParkingLotStatusLogMapper statusLogMapper,
                             SysTenantMapper sysTenantMapper,
                             CompanyMapper companyMapper,
                             ParkingLotScopeResolver scopeResolver,
                             ReadinessCheckService readinessCheckService) {
        this.parkingLotMapper = parkingLotMapper;
        this.capacityLogMapper = capacityLogMapper;
        this.statusLogMapper = statusLogMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.companyMapper = companyMapper;
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
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            // 平台用户：直接使用上下文中的租户（超管无需绑定公司）
            if (TenantContext.isPlatformUser()) {
                tenantId = TenantContext.getTenantId();
            }
            // 平台用户可显式指定目标租户（超管创建车场时选择归属租户）
            if (tenantId == null && TenantContext.isPlatformUser() && request.getTenantId() != null) {
                tenantId = request.getTenantId();
            }
            // 如果仍无租户，使用公司推导（兼容旧逻辑）
            if (tenantId == null && request.getCompanyId() != null) {
                Company preCompany = companyMapper.selectById(request.getCompanyId());
                if (preCompany != null && preCompany.getDeletedAt() == null) {
                    tenantId = preCompany.getTenantId();
                }
            }
            if (tenantId == null) {
                if (TenantContext.isPlatformUser()) {
                    // 超管未指定归属租户时默认挂到默认租户，支持先建车场后建租户管理员再绑定
                    tenantId = DEFAULT_TENANT_ID;
                } else {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                            "无法确定租户，请检查登录状态");
                }
            }
        } else {
            DataScope.requireCustomerAdmin();
        }

        // 校验租户状态（sys_tenant：1正常 0禁用）
        SysTenant tenant = sysTenantMapper.selectById(tenantId);
        DataScope.validateTenantEnabled(tenant != null ? tenant.getStatus() : null);

        ParkingLot lot = new ParkingLot();
        lot.setTenantId(tenantId);
        // 公司/集团模型扁平化：companyId 为空时置 0
        if (request.getCompanyId() != null && request.getCompanyId() > 0) {
            Company company = companyMapper.selectById(request.getCompanyId());
            if (company != null && company.getDeletedAt() == null) {
                lot.setCompanyId(company.getId());
                lot.setGroupId(company.getLevel() == 1 ? company.getId() : company.getParentId());
            }
        }
        if (lot.getCompanyId() == null) {
            // parking_lot.company_id 为 NOT NULL 列，未绑定公司时置 0
            lot.setCompanyId(0L);
        }
        lot.setName(request.getName().trim());
        lot.setAddress(defaultString(request.getAddress(), ""));
        lot.setContactPhone(defaultString(request.getContactPhone(), ""));
        lot.setContactName(defaultString(request.getContactName(), ""));
        lot.setLongitude(parseBigDecimal(request.getLongitude()));
        lot.setLatitude(parseBigDecimal(request.getLatitude()));

        int totalSpaces = request.getTotalSpaces() != null ? request.getTotalSpaces() : 0;
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
        lot.setDuplicateEntryPolicy(defaultString(request.getDuplicateEntryPolicy(), DEFAULT_DUPLICATE_ENTRY_POLICY));

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
        Long tenantId = resolveTenantId();
        // 平台用户使用停车场自身的 tenantId 进行数据校验
        Long effectiveTenantId = tenantId != null ? tenantId : lot.getTenantId();

        LambdaUpdateWrapper<ParkingLot> wrapper = new LambdaUpdateWrapper<ParkingLot>()
                .eq(ParkingLot::getId, lotId);

        boolean hasUpdate = false;
        if (request.getCompanyId() != null) {
            Company company = resolveCompany(request.getCompanyId(), effectiveTenantId);
            wrapper.set(ParkingLot::getCompanyId, company.getId());
            wrapper.set(ParkingLot::getGroupId,
                    company.getLevel() == 1 ? company.getId() : company.getParentId());
            hasUpdate = true;
        }
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
        if (request.getContactName() != null) {
            wrapper.set(ParkingLot::getContactName, request.getContactName().trim());
            hasUpdate = true;
        }
        if (request.getLongitude() != null) {
            wrapper.set(ParkingLot::getLongitude, parseBigDecimal(request.getLongitude()));
            hasUpdate = true;
        }
        if (request.getLatitude() != null) {
            wrapper.set(ParkingLot::getLatitude, parseBigDecimal(request.getLatitude()));
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
        if (request.getDuplicateEntryPolicy() != null) {
            wrapper.set(ParkingLot::getDuplicateEntryPolicy, request.getDuplicateEntryPolicy().trim());
            hasUpdate = true;
        }
        if (request.getFeeRuleId() != null) {
            wrapper.set(ParkingLot::getFeeRuleId, request.getFeeRuleId());
            hasUpdate = true;
        }

        if (!hasUpdate) {
            return toVO(lot);
        }

        // 更新前校验：不允许通过部分更新把 company_id 改为空或跨租户
        if (request.getCompanyId() != null) {
            wrapper.ne(ParkingLot::getCompanyId, 0);
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
        // 解析租户范围：平台用户可查看所有租户的停车场
        Long tenantId = resolveTenantId();

        LambdaQueryWrapper<ParkingLot> wrapper = new LambdaQueryWrapper<ParkingLot>()
                .eq(tenantId != null, ParkingLot::getTenantId, tenantId)
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
            // 不需要额外代码检查，由 Controller 层的 @RequirePermission("parking:disable") 保证
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

        boolean updated = parkingLotMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场状态已变更，请刷新后重试");
        }

        // 7. 写入状态审计日志
        writeStatusLog(lotId, lot.getTenantId(), beforeStatus, action, request.getReason());

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

        // 4b. 剩余车位不能超总车位
        if (FIELD_REMAINING_SPACES.equals(fieldName)) {
            int totalSpaces = lot.getTotalSpaces() != null ? lot.getTotalSpaces() : 0;
            if (newValue > totalSpaces) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "剩余车位数（" + newValue + "）不能超过总车位数（" + totalSpaces + "）");
            }
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
        writeCapacityLog(lotId, lot.getTenantId(), fieldName, beforeValue, newValue, reason);

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
     * 写入容量变更审计日志。
     */
    private void writeCapacityLog(Long parkingLotId, Long tenantId, String fieldName,
                                   int beforeValue, int afterValue, String reason) {
        Long operatorId;
        try {
            operatorId = TenantContext.requireUserId();
        } catch (Exception e) {
            log.warn("无法获取操作人 ID，使用 0 占位", e);
            operatorId = 0L;
        }

        ParkingLotCapacityLog logEntry = new ParkingLotCapacityLog();
        logEntry.setTenantId(tenantId);
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
    private void writeStatusLog(Long parkingLotId, Long tenantId, String beforeStatus,
                                 String afterStatus, String reason) {
        Long operatorId;
        try {
            operatorId = TenantContext.requireUserId();
        } catch (Exception e) {
            log.warn("无法获取操作人 ID，使用 0 占位", e);
            operatorId = 0L;
        }

        ParkingLotStatusLog logEntry = new ParkingLotStatusLog();
        logEntry.setTenantId(tenantId);
        logEntry.setParkingLotId(parkingLotId);
        logEntry.setBeforeStatus(beforeStatus);
        logEntry.setAfterStatus(afterStatus);
        logEntry.setOperatorId(operatorId);
        logEntry.setReason(reason != null ? reason : "");
        logEntry.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(logEntry);
    }

    /**
     * 删除停车场（物理删除）。
     * <p>
     * 删除前校验租户归属。关联的区域、通道、设备会因外键级联或业务约束需要提前处理。
     *
     * @param id 停车场 ID
     */
    @Transactional
    public void delete(Long id) {
        Long tenantId = resolveTenantId();
        ParkingLot lot = parkingLotMapper.selectById(id);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        // 平台用户允许跨租户删除；租户用户只能删除本租户的停车场
        if (tenantId != null && !Objects.equals(lot.getTenantId(), tenantId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权删除该停车场");
        }
        parkingLotMapper.deleteById(id);
        log.info("删除停车场成功: parkingLotId={}, name={}", id, lot.getName());
    }

    /**
     * ParkingLot → ParkingLotVO 转换。
     */
    private ParkingLotVO toVO(ParkingLot lot) {
        ParkingLotVO vo = new ParkingLotVO();
        vo.setId(lot.getId());
        vo.setTenantId(lot.getTenantId());
        vo.setCompanyId(lot.getCompanyId());
        vo.setGroupId(lot.getGroupId());
        vo.setName(lot.getName());
        vo.setAddress(lot.getAddress());
        vo.setContactPhone(lot.getContactPhone());
        vo.setContactName(lot.getContactName());
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
        vo.setDuplicateEntryPolicy(lot.getDuplicateEntryPolicy());
        vo.setFeeRuleId(lot.getFeeRuleId());
        vo.setCreatedAt(lot.getCreatedAt());
        vo.setUpdatedAt(lot.getUpdatedAt());

        // 回填公司名称
        if (lot.getCompanyId() != null) {
            Company company = companyMapper.selectById(lot.getCompanyId());
            if (company != null && company.getDeletedAt() == null) {
                vo.setCompanyName(company.getName());
            }
        }
        return vo;
    }

    /**
     * 解析并校验停车场所属公司。
     */
    private Company resolveCompany(Long companyId, Long tenantId) {
        if (companyId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "所属公司不能为空");
        }
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "所属公司不存在");
        }
        if (!Objects.equals(company.getTenantId(), tenantId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "所属公司不属于本租户");
        }
        return company;
    }

    private static String defaultString(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("经纬度格式无效: {}", value);
            return null;
        }
    }
}
