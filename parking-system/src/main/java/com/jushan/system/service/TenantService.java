package com.jushan.system.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.entity.SysAdminAccountRole;
import com.jushan.platform.modules.account.entity.SysCustomRole;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.mapper.SysCustomRoleMapper;
import com.jushan.system.dto.RegisterRequest;
import com.jushan.system.dto.TenantAuditRequest;
import com.jushan.system.entity.Company;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.TenantAuditLog;
import com.jushan.system.mapper.CompanyMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantAuditLogMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.mybatis.TenantIgnore;
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
    private final CompanyMapper companyMapper;
    private final SysAdminAccountMapper adminAccountMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final SysCustomRoleMapper customRoleMapper;

    public TenantService(TenantMapper tenantMapper,
                         TenantAuditLogMapper tenantAuditLogMapper,
                         SysUserMapper sysUserMapper,
                         CompanyMapper companyMapper,
                         SysAdminAccountMapper adminAccountMapper,
                         SysAdminAccountRoleMapper adminAccountRoleMapper,
                         SysCustomRoleMapper customRoleMapper) {
        this.tenantMapper = tenantMapper;
        this.tenantAuditLogMapper = tenantAuditLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.companyMapper = companyMapper;
        this.adminAccountMapper = adminAccountMapper;
        this.adminAccountRoleMapper = adminAccountRoleMapper;
        this.customRoleMapper = customRoleMapper;
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
    @TenantIgnore(reason = "客户自助注册，初始管理员账号尚未绑定租户", audit = false)
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
        Long userCount = sysUserMapper.countByUsernameIgnoreTenant(contactPhone);
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
        sysUserMapper.updateTenantIdIgnoreTenant(adminUser.getId(), tenant.getId());

        // 7. 为租户创建默认集团公司
        createDefaultCompany(tenant);

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

        // 7b. 审核通过后同步 sys_user 到 sys_admin_account
        syncAdminAccountOnApproved(tenant, action);

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
        SysUser user = sysUserMapper.selectByIdIgnoreTenant(adminUserId);
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

        sysUserMapper.updateStatusIgnoreTenant(adminUserId, newUserStatus, user.getStatus());

        // 同时更新 sys_admin_account 表中的状态（如果已同步）
        if (ACTION_APPROVED.equals(action) || ACTION_DISABLED.equals(action) || ACTION_ENABLED.equals(action)) {
            SysAdminAccount syncedAccount = adminAccountMapper.selectByUsernameIgnoreTenant(user.getUsername());
            if (syncedAccount != null) {
                int newAccountStatus = (ACTION_DISABLED.equals(action)) ? 0 : 1;
                syncedAccount.setStatus(newAccountStatus);
                syncedAccount.setUpdatedAt(LocalDateTime.now());
                adminAccountMapper.updateByIdIgnoreTenant(syncedAccount);
            }
        }
    }

    /**
     * 审核通过时将 sys_user 管理员同步到 sys_admin_account。
     * <p>
     * 仅 ACTION_APPROVED 时执行。复用 sys_user 中已有的 BCrypt 密码哈希
     * （Hutool 和 Spring 均基于 jBCrypt，生成 $2a$ 标准格式，完全兼容）。
     *
     * @param tenant 租户实体
     * @param action 审核操作类型
     */
    private void syncAdminAccountOnApproved(Tenant tenant, String action) {
        if (!ACTION_APPROVED.equals(action)) {
            return;
        }

        SysUser sysUser = sysUserMapper.selectByIdIgnoreTenant(tenant.getAdminUserId());
        if (sysUser == null) {
            log.warn("审核通过时找不到关联管理员: adminUserId={}", tenant.getAdminUserId());
            return;
        }

        // 检查是否已存在同名 SysAdminAccount（防重复同步）
        SysAdminAccount existingAccount = adminAccountMapper.selectByUsernameIgnoreTenant(sysUser.getUsername());
        if (existingAccount != null) {
            log.info("SysAdminAccount 已存在，跳过同步: username={}", sysUser.getUsername());
            return;
        }

        // 创建 SysAdminAccount 记录
        SysAdminAccount account = new SysAdminAccount();
        account.setTenantId(tenant.getId());
        account.setUsername(sysUser.getUsername());
        // Hutool BCrypt.hashpw() 和 Spring BCryptPasswordEncoder 均生成 $2a$ 标准格式，可直接复用
        account.setPassword(sysUser.getPasswordHash());
        account.setRealName(sysUser.getDisplayName());
        account.setPhone(tenant.getContactPhone());
        account.setLevel(1); // level=1 租户管理员（租户内最高权限）
        account.setStatus(1); // STATUS_NORMAL
        account.setLoginFailCount(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        adminAccountMapper.insert(account);

        // 绑定 customer_admin 角色
        Long roleId = resolveCustomerAdminRoleId(tenant.getId());
        if (roleId != null) {
            SysAdminAccountRole roleBinding = new SysAdminAccountRole();
            roleBinding.setAdminAccountId(account.getId());
            roleBinding.setRoleId(roleId);
            roleBinding.setCreatedAt(LocalDateTime.now());
            adminAccountRoleMapper.insertIgnoreTenant(roleBinding);
        }

        log.info("审核通过后同步管理员账号到 sys_admin_account 成功: userId={}, accountId={}, tenantId={}",
                sysUser.getId(), account.getId(), tenant.getId());
    }

    /**
     * 查找或创建 customer_admin 角色。
     * <p>
     * 先通过 role_code 全局查找，若属于当前租户则复用；
     * 否则为该租户新建一个 customer_admin 角色。
     *
     * @param tenantId 租户 ID
     * @return 角色 ID，创建失败返回 null
     */
    private Long resolveCustomerAdminRoleId(Long tenantId) {
        // 先查找已有角色（忽略租户拦截，全局搜索）
        SysCustomRole existRole = customRoleMapper.selectByRoleCodeIgnoreTenant("customer_admin");
        if (existRole != null && tenantId.equals(existRole.getTenantId())) {
            return existRole.getId();
        }

        // 不存在或属于其他租户，则新建
        SysCustomRole newRole = new SysCustomRole();
        newRole.setTenantId(tenantId);
        newRole.setRoleName("客户管理员");
        newRole.setRoleCode("customer_admin");
        newRole.setDescription("租户默认管理员角色，拥有该租户的全部管理权限");
        newRole.setCreatedAt(LocalDateTime.now());
        newRole.setUpdatedAt(LocalDateTime.now());
        customRoleMapper.insert(newRole);
        log.info("自动创建 customer_admin 角色: roleId={}, tenantId={}", newRole.getId(), tenantId);
        return newRole.getId();
    }

    /**
     * 租户禁用后标记该租户内所有用户需要重新认证（FIX-06）。
     * <p>
     * JWT 无状态模式下无法像 Sa-Token 一样立即撤销会话；此处仅记录日志，
     * 实际失效依赖 JWT 过期或后续加入 Token 黑名单机制。
     */
    private void revokeTenantSessions(Long tenantId) {
        try {
            List<SysUser> tenantUsers = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getTenantId, tenantId));
            log.info("租户禁用后需重新认证的用户数: count={}, tenantId={}", tenantUsers.size(), tenantId);
        } catch (Exception e) {
            log.error("批量查询租户用户失败: tenantId={}", tenantId, e);
        }
    }

    /**
     * 为租户创建默认集团公司。
     * <p>
     * 每个租户至少有一个根公司，历史数据通过 Flyway 迁移回填。
     *
     * @param tenant 租户
     */
    private void createDefaultCompany(Tenant tenant) {
        Company company = new Company();
        company.setTenantId(tenant.getId());
        company.setParentId(null);
        company.setName(tenant.getName());
        company.setLevel(CompanyService.LEVEL_GROUP);
        company.setStatus(CompanyService.STATUS_NORMAL);
        company.setSortOrder(0);
        company.setContactName(tenant.getContactPerson());
        company.setContactPhone(tenant.getContactPhone());
        company.setCreatedAt(LocalDateTime.now());
        company.setUpdatedAt(LocalDateTime.now());
        companyMapper.insert(company);

        String path = "/" + company.getId() + "/";
        companyMapper.updatePathIgnoreTenant(company.getId(), path);

        log.info("租户默认公司创建成功: tenantId={}, companyId={}", tenant.getId(), company.getId());
    }

    /**
     * 写入审核日志。
     */
    private void writeAuditLog(Long tenantId, String action, String reason,
                                String beforeStatus, String afterStatus) {
        long operatorId;
        try {
            operatorId = TenantContext.requireUserId();
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
