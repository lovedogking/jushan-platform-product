package com.jushan.platform.modules.account.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.dto.AdminAccountCreateCmd;
import com.jushan.platform.modules.account.dto.AdminAccountUpdateCmd;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.entity.SysAdminAccountParkingLot;
import com.jushan.platform.modules.account.entity.SysAdminAccountRole;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountParkingLotMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.service.SysAdminAccountService;
import com.jushan.platform.modules.account.vo.AdminAccountVO;
import com.jushan.platform.modules.account.vo.ResetPasswordVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理员账号服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class SysAdminAccountServiceImpl implements SysAdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(SysAdminAccountServiceImpl.class);

    /** 账号状态：正常（启用） */
    private static final int STATUS_NORMAL = 1;
    /** 账号状态：禁用 */
    private static final int STATUS_DISABLED = 0;
    /** 账号状态：锁定 */
    private static final int STATUS_LOCKED = 2;

    /** 管理员级别：平台 */
    private static final int LEVEL_PLATFORM = 1;
    /** 管理员级别：公司 */
    private static final int LEVEL_COMPANY = 2;
    /** 管理员级别：停车场 */
    private static final int LEVEL_LOT = 3;

    /** 最大登录失败次数 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    /** 锁定时长（分钟） */
    private static final int LOCK_MINUTES = 30;

    /** 随机密码字符集 */
    private static final String PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    /** 随机密码长度 */
    private static final int RANDOM_PASSWORD_LENGTH = 8;

    private final SysAdminAccountMapper adminAccountMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final SysAdminAccountParkingLotMapper parkingLotMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public SysAdminAccountServiceImpl(SysAdminAccountMapper adminAccountMapper,
                                      SysAdminAccountRoleMapper adminAccountRoleMapper,
                                      SysAdminAccountParkingLotMapper parkingLotMapper,
                                      BCryptPasswordEncoder passwordEncoder) {
        this.adminAccountMapper = adminAccountMapper;
        this.adminAccountRoleMapper = adminAccountRoleMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AdminAccountVO create(AdminAccountCreateCmd cmd) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        CurrentScope scope = resolveCurrentScope(current);

        Integer level = cmd.getLevel();
        Long companyId = cmd.getCompanyId();
        Long lotId = cmd.getLotId();
        Long tenantId = resolveTargetTenantId(current, scope, cmd.getTenantId(), level);

        validateLevelBinding(level, companyId, lotId);
        validateCreateScope(scope, level, companyId, lotId);

        // 校验账号唯一性
        SysAdminAccount exist = adminAccountMapper.selectByUsernameIgnoreTenant(cmd.getUsername().trim());
        if (exist != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "登录账号已存在");
        }

        String rawPassword = cmd.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = generateRandomPassword();
        }

        SysAdminAccount account = new SysAdminAccount();
        account.setTenantId(tenantId);
        account.setCompanyId(companyId);
        account.setLotId(lotId);
        account.setUsername(cmd.getUsername().trim());
        account.setPassword(passwordEncoder.encode(rawPassword));
        account.setRealName(cmd.getRealName().trim());
        account.setPhone(cmd.getPhone());
        account.setEmail(cmd.getEmail());
        account.setLevel(level);
        account.setStatus(cmd.getStatus() != null ? cmd.getStatus() : STATUS_NORMAL);
        account.setMustChangePassword(cmd.getMustChangePassword() != null ? cmd.getMustChangePassword() : 1);
        account.setAllowFeeReduction(cmd.getAllowFeeReduction() != null ? cmd.getAllowFeeReduction() : 0);
        account.setLoginFailCount(0);
        account.setLockUntil(null);
        account.setLastLoginTime(null);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        adminAccountMapper.insert(account);
        saveAccountRoles(account.getId(), cmd.getRoleIds());
        saveAccountParkingLots(account.getId(), tenantId, cmd.getParkingLotIds());

        log.info("创建管理员账号成功: id={}, level={}, tenantId={}, operator={}",
                account.getId(), level, tenantId, current.userId());

        AdminAccountVO vo = toVO(account);
        vo.setRoleIds(cmd.getRoleIds());
        return vo;
    }

    @Override
    @Transactional
    public AdminAccountVO update(Long id, AdminAccountUpdateCmd cmd) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        CurrentScope scope = resolveCurrentScope(current);

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(id);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "管理员账号不存在");
        }
        validateDataAccess(scope, account);

        Integer level = cmd.getLevel();
        Long companyId = cmd.getCompanyId();
        Long lotId = cmd.getLotId();
        validateLevelBinding(level, companyId, lotId);
        validateUpdateScope(scope, level, companyId, lotId);

        account.setCompanyId(companyId);
        account.setLotId(lotId);
        account.setRealName(cmd.getRealName().trim());
        if (cmd.getPhone() != null) {
            account.setPhone(cmd.getPhone());
        }
        if (cmd.getEmail() != null) {
            account.setEmail(cmd.getEmail());
        }
        account.setLevel(level);
        account.setStatus(cmd.getStatus());
        if (cmd.getMustChangePassword() != null) {
            account.setMustChangePassword(cmd.getMustChangePassword());
        }
        if (cmd.getAllowFeeReduction() != null) {
            account.setAllowFeeReduction(cmd.getAllowFeeReduction());
        }
        account.setUpdatedAt(LocalDateTime.now());

        int rows = adminAccountMapper.updateByIdIgnoreTenant(account);
        if (rows == 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "账号更新失败，请刷新后重试");
        }

        saveAccountRoles(id, cmd.getRoleIds());
        saveAccountParkingLots(id, account.getTenantId(), cmd.getParkingLotIds());

        log.info("编辑管理员账号成功: id={}, operator={}", id, current.userId());

        AdminAccountVO vo = toVO(account);
        vo.setRoleIds(cmd.getRoleIds());
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        CurrentScope scope = resolveCurrentScope(current);

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(id);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "管理员账号不存在");
        }
        validateDataAccess(scope, account);

        int rows = adminAccountMapper.deleteByIdIgnoreTenant(id);
        if (rows == 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "账号删除失败，请刷新后重试");
        }

        log.info("删除管理员账号成功: id={}, operator={}", id, current.userId());
    }

    @Override
    public AdminAccountVO getById(Long id) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        CurrentScope scope = resolveCurrentScope(current);

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(id);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "管理员账号不存在");
        }
        validateDataAccess(scope, account);

        return toVOWithRoles(account);
    }

    @Override
    public IPage<AdminAccountVO> list(int page, int size, String keyword, Integer status) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        CurrentScope scope = resolveCurrentScope(current);

        Long tenantId = scope.isPlatform() ? null : current.tenantId();
        Long companyId = null;
        Long lotId = null;

        if (!scope.isPlatform()) {
            if (scope.getLevel() == LEVEL_COMPANY || scope.getLevel() == LEVEL_LOT) {
                companyId = scope.getCompanyId();
            }
            if (scope.getLevel() == LEVEL_LOT) {
                lotId = scope.getLotId();
            }
        }

        IPage<SysAdminAccount> entityPage = adminAccountMapper.selectPageList(
                new Page<>(page, size), tenantId, companyId, lotId, keyword, status);

        return entityPage.convert(this::toVOWithRoles);
    }

    @Override
    @Transactional
    public ResetPasswordVO resetPassword(Long id) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        CurrentScope scope = resolveCurrentScope(current);

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(id);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "管理员账号不存在");
        }
        validateDataAccess(scope, account);

        String plainPassword = generateRandomPassword();
        account.setPassword(passwordEncoder.encode(plainPassword));
        account.setUpdatedAt(LocalDateTime.now());
        account.setLoginFailCount(0);
        account.setLockUntil(null);

        int rows = adminAccountMapper.updateByIdIgnoreTenant(account);
        if (rows == 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "密码重置失败，请刷新后重试");
        }

        log.info("重置管理员密码成功: id={}, operator={}", id, current.userId());
        return new ResetPasswordVO(plainPassword);
    }

    @Override
    @Transactional
    public void onLoginSuccess(String username) {
        SysAdminAccount account = adminAccountMapper.selectByUsernameIgnoreTenant(username);
        if (account == null) {
            return;
        }
        account.setLoginFailCount(0);
        account.setLockUntil(null);
        account.setLastLoginTime(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        adminAccountMapper.updateByIdIgnoreTenant(account);
    }

    @Override
    @Transactional
    public void onLoginFail(String username) {
        SysAdminAccount account = adminAccountMapper.selectByUsernameIgnoreTenant(username);
        if (account == null) {
            return;
        }
        int failCount = account.getLoginFailCount() == null ? 0 : account.getLoginFailCount();
        failCount++;
        account.setLoginFailCount(failCount);
        account.setUpdatedAt(LocalDateTime.now());

        if (failCount >= MAX_LOGIN_FAIL_COUNT) {
            account.setStatus(STATUS_LOCKED);
            account.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            log.warn("管理员账号登录失败次数超限已锁定: username={}, lockUntil={}", username, account.getLockUntil());
        }

        adminAccountMapper.updateByIdIgnoreTenant(account);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验当前会话是否已初始化。
     */
    private TenantContext.Snapshot requireCurrentSnapshot() {
        TenantContext.Snapshot s = TenantContext.get();
        if (s == null || s.userId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }
        return s;
    }

    /**
     * 解析当前操作人的数据范围。
     */
    private CurrentScope resolveCurrentScope(TenantContext.Snapshot current) {
        if (current.isPlatformUser()) {
            SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(current.userId());
            if (account != null) {
                return new CurrentScope(false, account.getLevel(), account.getTenantId(),
                        account.getCompanyId(), account.getLotId());
            }
            // 平台用户无账号记录时默认拥有全平台范围
            return new CurrentScope(true, LEVEL_PLATFORM, null, null, null);
        }

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(current.userId());
        if (account == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "当前管理员账号不存在");
        }
        return new CurrentScope(false, account.getLevel(), account.getTenantId(),
                account.getCompanyId(), account.getLotId());
    }

    /**
     * 确定目标账号的租户 ID。
     */
    private Long resolveTargetTenantId(TenantContext.Snapshot current, CurrentScope scope,
                                       Long cmdTenantId, Integer level) {
        if (level == LEVEL_PLATFORM) {
            return null;
        }
        if (current.isPlatformUser()) {
            if (cmdTenantId == null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "创建二级/三级管理员必须指定租户 ID");
            }
            return cmdTenantId;
        }
        return current.tenantId();
    }

    /**
     * 校验级别与公司/停车场绑定关系。
     */
    private void validateLevelBinding(Integer level, Long companyId, Long lotId) {
        if (level == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "管理员级别不能为空");
        }
        if (level == LEVEL_PLATFORM) {
            if (companyId != null || lotId != null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "平台管理员不能绑定公司或停车场");
            }
            return;
        }
        if (level == LEVEL_COMPANY) {
            if (companyId == null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "公司管理员必须绑定公司");
            }
            if (lotId != null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "公司管理员不能绑定停车场");
            }
            return;
        }
        if (level == LEVEL_LOT) {
            if (companyId == null || lotId == null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场管理员必须同时绑定公司和停车场");
            }
            return;
        }
        throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的管理员级别: " + level);
    }

    /**
     * 校验创建操作的数据范围。
     */
    private void validateCreateScope(CurrentScope scope, Integer level, Long companyId, Long lotId) {
        if (scope.isPlatform()) {
            return;
        }
        if (level == LEVEL_PLATFORM) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权创建平台管理员");
        }
        if (scope.getLevel() == LEVEL_COMPANY || scope.getLevel() == LEVEL_LOT) {
            if (!Objects.equals(scope.getCompanyId(), companyId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能创建同公司范围内的管理员");
            }
        }
        if (scope.getLevel() == LEVEL_LOT) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "停车场管理员无权创建账号");
        }
    }

    /**
     * 校验编辑操作的数据范围。
     */
    private void validateUpdateScope(CurrentScope scope, Integer level, Long companyId, Long lotId) {
        if (scope.isPlatform()) {
            return;
        }
        if (level == LEVEL_PLATFORM) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权修改为平台管理员");
        }
        if (scope.getLevel() == LEVEL_COMPANY || scope.getLevel() == LEVEL_LOT) {
            if (!Objects.equals(scope.getCompanyId(), companyId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能修改同公司范围内的管理员");
            }
        }
        if (scope.getLevel() == LEVEL_LOT) {
            if (!Objects.equals(scope.getLotId(), lotId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能修改同停车场范围内的管理员");
            }
        }
    }

    /**
     * 校验当前操作人能否访问目标账号。
     */
    private void validateDataAccess(CurrentScope scope, SysAdminAccount account) {
        if (scope.isPlatform()) {
            return;
        }
        if (!Objects.equals(scope.getTenantId(), account.getTenantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "账号不属于本租户");
        }
        if (scope.getLevel() == LEVEL_COMPANY || scope.getLevel() == LEVEL_LOT) {
            if (!Objects.equals(scope.getCompanyId(), account.getCompanyId())) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "账号不属于本公司");
            }
        }
        if (scope.getLevel() == LEVEL_LOT) {
            if (!Objects.equals(scope.getLotId(), account.getLotId())) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "账号不属于本停车场");
            }
        }
    }

    /**
     * 保存账号角色关联（先删后增）。
     */
    private void saveAccountRoles(Long adminAccountId, List<Long> roleIds) {
        adminAccountRoleMapper.deleteByAdminAccountId(adminAccountId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        List<Long> distinctRoleIds = roleIds.stream().distinct().collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : distinctRoleIds) {
            SysAdminAccountRole relation = new SysAdminAccountRole();
            relation.setId(IdWorker.getId());
            relation.setAdminAccountId(adminAccountId);
            relation.setRoleId(roleId);
            relation.setCreatedAt(now);
            adminAccountRoleMapper.insertIgnoreTenant(relation);
        }
    }

    /**
     * 保存管理员账号与停车场的多对多关联（先删后增）。
     */
    private void saveAccountParkingLots(Long accountId, Long tenantId, List<Long> parkingLotIds) {
        parkingLotMapper.deleteByAdminAccountId(accountId);
        if (parkingLotIds == null || parkingLotIds.isEmpty()) {
            return;
        }
        for (Long lotId : parkingLotIds) {
            SysAdminAccountParkingLot mapping = new SysAdminAccountParkingLot();
            mapping.setAdminAccountId(accountId);
            mapping.setTenantId(tenantId);
            mapping.setParkingLotId(lotId);
            mapping.setCreatedAt(LocalDateTime.now());
            parkingLotMapper.insert(mapping);
        }
    }

    /**
     * 生成随机密码。
     */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(RANDOM_PASSWORD_LENGTH);
        for (int i = 0; i < RANDOM_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 转换为基础 VO（不含角色）。
     */
    private AdminAccountVO toVO(SysAdminAccount account) {
        AdminAccountVO vo = new AdminAccountVO();
        vo.setId(account.getId());
        vo.setTenantId(account.getTenantId());
        vo.setCompanyId(account.getCompanyId());
        vo.setLotId(account.getLotId());
        vo.setUsername(account.getUsername());
        vo.setRealName(account.getRealName());
        vo.setPhone(account.getPhone());
        vo.setEmail(account.getEmail());
        vo.setLevel(account.getLevel());
        vo.setStatus(account.getStatus());
        vo.setLoginFailCount(account.getLoginFailCount());
        vo.setLockUntil(account.getLockUntil());
        vo.setLastLoginTime(account.getLastLoginTime());
        vo.setCreatedAt(account.getCreatedAt());
        vo.setUpdatedAt(account.getUpdatedAt());
        vo.setMustChangePassword(account.getMustChangePassword());
        vo.setAllowFeeReduction(account.getAllowFeeReduction());
        return vo;
    }

    /**
     * 转换为完整 VO（含角色）。
     */
    private AdminAccountVO toVOWithRoles(SysAdminAccount account) {
        AdminAccountVO vo = toVO(account);
        List<Long> roleIds = adminAccountRoleMapper.selectRoleIdsByAdminAccountId(account.getId());
        vo.setRoleIds(roleIds);
        return vo;
    }

    /**
     * 当前操作人数据范围。
     */
    private static class CurrentScope {
        private final boolean platform;
        private final int level;
        private final Long tenantId;
        private final Long companyId;
        private final Long lotId;

        CurrentScope(boolean platform, int level, Long tenantId, Long companyId, Long lotId) {
            this.platform = platform;
            this.level = level;
            this.tenantId = tenantId;
            this.companyId = companyId;
            this.lotId = lotId;
        }

        boolean isPlatform() { return platform; }
        int getLevel() { return level; }
        Long getTenantId() { return tenantId; }
        Long getCompanyId() { return companyId; }
        Long getLotId() { return lotId; }
    }
}
