package com.jushan.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.dto.RegisterRequest;
import com.jushan.system.dto.TenantAuditRequest;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.TenantAuditLog;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantAuditLogMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.vo.TenantVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 租户服务。
 * <p>
 * 负责客户注册、审核、启用/禁用以及关联的账号管理。
 * 所有审核操作均写入审计日志。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    /** 租户状态常量 */
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** 审核操作类型常量 */
    public static final String ACTION_APPROVED = "APPROVED";
    public static final String ACTION_REJECTED = "REJECTED";
    public static final String ACTION_ENABLED = "ENABLED";
    public static final String ACTION_DISABLED = "DISABLED";

    /** sys_user 状态：待审核 */
    private static final String USER_STATUS_PENDING = "PENDING_REVIEW";

    /** 合法操作类型集合 */
    private static final List<String> VALID_ACTIONS = Arrays.asList(
            ACTION_APPROVED, ACTION_REJECTED, ACTION_ENABLED, ACTION_DISABLED);

    /** 允许审核的租户状态（APPROVED/REJECTED 只对 PENDING_REVIEW 有效） */
    private static final List<String> REVIEWABLE_STATUSES = List.of(STATUS_PENDING_REVIEW);

    /** 允许启停的租户状态（ENABLED/DISABLED 在 ENABLED 和 DISABLED 之间切换） */
    private static final List<String> TOGGLEABLE_STATUSES = Arrays.asList(STATUS_ENABLED, STATUS_DISABLED);

    private final TenantMapper tenantMapper;
    private final TenantAuditLogMapper tenantAuditLogMapper;
    private final SysUserMapper sysUserMapper;
    private final StpInterfaceImpl stpInterface;

    public TenantService(TenantMapper tenantMapper,
                         TenantAuditLogMapper tenantAuditLogMapper,
                         SysUserMapper sysUserMapper,
                         StpInterfaceImpl stpInterface) {
        this.tenantMapper = tenantMapper;
        this.tenantAuditLogMapper = tenantAuditLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.stpInterface = stpInterface;
    }

    // ==================== 客户注册 ====================

    /**
     * 客户自助注册。
     * <p>
     * 1. 校验企业名称和手机号唯一性
     * 2. 创建待审核管理员账号（sys_user）
     * 3. 创建待审核租户（tenant）
     * <p>
     * 整个操作在一个事务中完成。
     *
     * @param request 注册请求
     */
    @Transactional
    public void register(RegisterRequest request) {
        String companyName = request.getCompanyName().trim();
        String contactPhone = request.getContactPhone().trim();

        // 1. 唯一性校验：企业名称
        Long nameCount = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getName, companyName));
        if (nameCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该企业名称已被注册");
        }

        // 2. 唯一性校验：手机号
        Long phoneCount = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, contactPhone));
        if (phoneCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该手机号已被注册");
        }

        // 3. 校验手机号未被平台用户占用
        Long userCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, contactPhone));
        if (userCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该手机号已被使用");
        }

        // 4. 创建待审核管理员账号
        SysUser adminUser = new SysUser();
        adminUser.setUsername(contactPhone);
        adminUser.setPasswordHash(BCrypt.hashpw(request.getPassword()));
        adminUser.setDisplayName(request.getContactPerson().trim());
        adminUser.setStatus(USER_STATUS_PENDING);
        adminUser.setRoles("[\"customer_admin\"]");
        adminUser.setTenantId(null); // 先创建用户，tenantId 在租户创建后回填
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(adminUser);

        // 5. 创建待审核租户
        Tenant tenant = new Tenant();
        tenant.setName(companyName);
        tenant.setContactPerson(request.getContactPerson().trim());
        tenant.setContactPhone(contactPhone);
        tenant.setStatus(STATUS_PENDING_REVIEW);
        tenant.setAdminUserId(adminUser.getId());
        tenant.setMaxParkingLots(3);
        tenant.setMaxDevices(10);
        tenant.setMaxEmployees(20);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);

        // 6. 回填 sys_user.tenant_id
        sysUserMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                        .set(SysUser::getTenantId, tenant.getId())
                        .eq(SysUser::getId, adminUser.getId()));

        log.info("客户注册成功: tenantId={}, companyName={}, adminUserId={}",
                tenant.getId(), companyName, adminUser.getId());
    }

    // ==================== 审核与启停 ====================

    /**
     * 审核/启停租户。
     * <p>
     * 仅超级管理员和平台运营可操作。使用条件更新确保状态流转合法。
     *
     * @param tenantId 租户 ID
     * @param request  审核请求（操作类型 + 原因）
     */
    @Transactional
    public void audit(Long tenantId, TenantAuditRequest request) {
        String action = request.getAction().trim();
        String reason = request.getReason() != null ? request.getReason().trim() : "";

        // 1. 校验操作类型
        if (!VALID_ACTIONS.contains(action)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的操作类型: " + action);
        }

        // 2. 拒绝操作必须填写原因
        if (ACTION_REJECTED.equals(action) && reason.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "拒绝操作必须填写原因");
        }

        // 3. 查询租户
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "租户不存在");
        }

        String beforeStatus = tenant.getStatus();

        // 4. 状态流转校验
        validateStateTransition(action, beforeStatus);

        // 5. 计算目标状态
        String afterStatus = resolveTargetStatus(action);

        // 6. 条件更新租户状态（防并发）
        boolean updated = tenantMapper.update(null,
                new LambdaUpdateWrapper<Tenant>()
                        .set(Tenant::getStatus, afterStatus)
                        .set(Tenant::getUpdatedAt, LocalDateTime.now())
                        .eq(Tenant::getId, tenantId)
                        .eq(Tenant::getStatus, beforeStatus)) > 0;

        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "租户状态已变更，请刷新后重试");
        }

        // 7. 同步更新关联的管理员账号状态
        updateAdminUserStatus(tenant.getAdminUserId(), action);

        // 8. FIX-06：租户禁用后撤销该租户所有用户的会话
        if (ACTION_DISABLED.equals(action)) {
            revokeTenantSessions(tenantId);
        }

        // 9. 写入审计日志
        writeAuditLog(tenantId, action, reason, beforeStatus, afterStatus);

        log.info("租户审核操作完成: tenantId={}, action={}, {} -> {}",
                tenantId, action, beforeStatus, afterStatus);
    }

    // ==================== 租户查询 ====================

    /**
     * 分页查询租户列表。
     *
     * @param page   页码
     * @param size   每页大小
     * @param status 状态筛选（可选）
     * @return 分页结果
     */
    public IPage<TenantVO> listTenants(int page, int size, String status) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<Tenant>()
                .eq(status != null && !status.isBlank(), Tenant::getStatus, status)
                .orderByDesc(Tenant::getCreatedAt);

        IPage<Tenant> tenantPage = tenantMapper.selectPage(new Page<>(page, size), wrapper);

        return tenantPage.convert(this::toVO);
    }

    /**
     * 查询单个租户详情。
     *
     * @param tenantId 租户 ID
     * @return 租户视图
     */
    public TenantVO getTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "租户不存在");
        }
        return toVO(tenant);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验状态流转是否合法。
     */
    private void validateStateTransition(String action, String currentStatus) {
        switch (action) {
            case ACTION_APPROVED:
                if (!REVIEWABLE_STATUSES.contains(currentStatus)) {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                            "只能审核待审核状态的租户，当前状态: " + currentStatus);
                }
                break;
            case ACTION_REJECTED:
                if (!REVIEWABLE_STATUSES.contains(currentStatus)) {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                            "只能拒绝待审核状态的租户，当前状态: " + currentStatus);
                }
                break;
            case ACTION_ENABLED:
                if (!TOGGLEABLE_STATUSES.contains(currentStatus)) {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                            "只能启用已禁用状态的租户，当前状态: " + currentStatus);
                }
                if (STATUS_ENABLED.equals(currentStatus)) {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "租户已是启用状态");
                }
                break;
            case ACTION_DISABLED:
                if (!TOGGLEABLE_STATUSES.contains(currentStatus)) {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                            "只能禁用已启用状态的租户，当前状态: " + currentStatus);
                }
                if (STATUS_DISABLED.equals(currentStatus)) {
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "租户已是禁用状态");
                }
                break;
            default:
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "未知操作类型: " + action);
        }
    }

    /**
     * 根据操作类型计算目标状态。
     */
    private String resolveTargetStatus(String action) {
        return switch (action) {
            case ACTION_APPROVED -> STATUS_ENABLED;
            case ACTION_REJECTED -> STATUS_REJECTED;
            case ACTION_ENABLED -> STATUS_ENABLED;
            case ACTION_DISABLED -> STATUS_DISABLED;
            default -> throw new BusinessException(CommonErrorCode.PARAM_ERROR, "未知操作: " + action);
        };
    }

    /**
     * 同步更新关联的管理员账号状态。
     * <p>
     * 使用条件更新（按 ID + 当前状态），防止并发覆盖。
     */
    private void updateAdminUserStatus(Long adminUserId, String action) {
        if (adminUserId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(adminUserId);
        if (user == null) {
            log.warn("审核同步用户状态时找不到用户: userId={}", adminUserId);
            return;
        }

        String newUserStatus = switch (action) {
            case ACTION_APPROVED, ACTION_ENABLED -> "ENABLED";
            case ACTION_REJECTED, ACTION_DISABLED -> "DISABLED";
            default -> null;
        };

        if (newUserStatus == null) {
            return;
        }

        sysUserMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                        .set(SysUser::getStatus, newUserStatus)
                        .set(SysUser::getUpdatedAt, LocalDateTime.now())
                        .eq(SysUser::getId, adminUserId)
                        .eq(SysUser::getStatus, user.getStatus()));
    }

    /**
     * 租户禁用后撤销该租户内所有用户的会话（FIX-06）。
     * <p>
     * 查询该租户下的所有 sys_user，逐一调用 logout 使 Token 立即失效。
     * 并清除权限缓存，确保下一请求无法使用旧权限。
     */
    private void revokeTenantSessions(Long tenantId) {
        try {
            List<SysUser> tenantUsers = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getTenantId, tenantId));
            int revokedCount = 0;
            for (SysUser user : tenantUsers) {
                try {
                    StpUtil.logout(user.getId());
                    stpInterface.clearCache(user.getId());
                    revokedCount++;
                } catch (Exception e) {
                    log.warn("撤销用户会话失败: userId={}, tenantId={}", user.getId(), tenantId, e);
                }
            }
            log.info("租户禁用后已撤销 {} 个用户的会话: tenantId={}", revokedCount, tenantId);
        } catch (Exception e) {
            log.error("批量撤销租户会话失败: tenantId={}", tenantId, e);
        }
    }

    /**
     * 写入审核日志。
     */
    private void writeAuditLog(Long tenantId, String action, String reason,
                                String beforeStatus, String afterStatus) {
        long operatorId;
        try {
            operatorId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            log.error("获取当前操作人 ID 失败", e);
            operatorId = 0L; // 不应发生，审核操作必须登录
        }

        TenantAuditLog auditLog = new TenantAuditLog();
        auditLog.setTenantId(tenantId);
        auditLog.setAction(action);
        auditLog.setOperatorId(operatorId);
        auditLog.setReason(reason);
        auditLog.setBeforeStatus(beforeStatus);
        auditLog.setAfterStatus(afterStatus);
        auditLog.setCreatedAt(LocalDateTime.now());
        tenantAuditLogMapper.insert(auditLog);
    }

    /**
     * Tenant → TenantVO 转换。
     */
    private TenantVO toVO(Tenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setContactPerson(tenant.getContactPerson());
        vo.setContactPhone(maskPhone(tenant.getContactPhone()));
        vo.setStatus(tenant.getStatus());
        vo.setAdminUserId(tenant.getAdminUserId());
        vo.setAdminUsername(getAdminUsername(tenant.getAdminUserId()));
        vo.setMaxParkingLots(tenant.getMaxParkingLots());
        vo.setMaxDevices(tenant.getMaxDevices());
        vo.setMaxEmployees(tenant.getMaxEmployees());
        vo.setCreatedAt(tenant.getCreatedAt());
        vo.setUpdatedAt(tenant.getUpdatedAt());
        return vo;
    }

    /**
     * 手机号脱敏：138****5678。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 根据用户 ID 查询登录账号名。
     */
    private String getAdminUsername(Long adminUserId) {
        if (adminUserId == null) {
            return "";
        }
        SysUser user = sysUserMapper.selectById(adminUserId);
        return user != null ? user.getUsername() : "";
    }
}
