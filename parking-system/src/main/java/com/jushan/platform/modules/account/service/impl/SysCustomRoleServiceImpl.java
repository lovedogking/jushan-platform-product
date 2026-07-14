package com.jushan.platform.modules.account.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.dto.CustomRoleCreateCmd;
import com.jushan.platform.modules.account.dto.CustomRoleUpdateCmd;
import com.jushan.platform.modules.account.dto.RolePermissionSaveCmd;
import com.jushan.platform.modules.account.entity.SysAdminAccountRole;
import com.jushan.platform.modules.account.entity.SysCustomRole;
import com.jushan.platform.modules.account.entity.SysRolePermission;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.mapper.SysCustomRoleMapper;
import com.jushan.platform.modules.account.mapper.SysRolePermissionMapper;
import com.jushan.platform.modules.account.service.SysCustomRoleService;
import com.jushan.platform.modules.account.vo.CustomRoleVO;
import com.jushan.platform.modules.account.vo.RolePermissionVO;
import com.jushan.platform.infra.security.PermissionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自定义角色服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class SysCustomRoleServiceImpl implements SysCustomRoleService {

    private static final Logger log = LoggerFactory.getLogger(SysCustomRoleServiceImpl.class);

    private final SysCustomRoleMapper customRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final PermissionProvider permissionProvider;

    public SysCustomRoleServiceImpl(SysCustomRoleMapper customRoleMapper,
                                    SysRolePermissionMapper rolePermissionMapper,
                                    SysAdminAccountRoleMapper adminAccountRoleMapper,
                                    PermissionProvider permissionProvider) {
        this.customRoleMapper = customRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.adminAccountRoleMapper = adminAccountRoleMapper;
        this.permissionProvider = permissionProvider;
    }

    @Override
    @Transactional
    public CustomRoleVO create(CustomRoleCreateCmd cmd) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        Long tenantId = resolveTenantId(current, cmd.getTenantId());

        validateRoleCodeUnique(tenantId, cmd.getRoleCode().trim(), null);

        SysCustomRole role = new SysCustomRole();
        role.setTenantId(tenantId);
        role.setRoleName(cmd.getRoleName().trim());
        role.setRoleCode(cmd.getRoleCode().trim());
        role.setDescription(cmd.getDescription());
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());

        customRoleMapper.insert(role);

        log.info("创建自定义角色成功: id={}, tenantId={}, operator={}",
                role.getId(), tenantId, current.userId());

        return toVO(role);
    }

    @Override
    @Transactional
    public CustomRoleVO update(Long id, CustomRoleUpdateCmd cmd) {
        TenantContext.Snapshot current = requireCurrentSnapshot();

        SysCustomRole role = customRoleMapper.selectByIdIgnoreTenant(id);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        validateTenantAccess(current, role.getTenantId());

        String newRoleCode = cmd.getRoleCode().trim();
        if (!Objects.equals(role.getRoleCode(), newRoleCode)) {
            validateRoleCodeUnique(role.getTenantId(), newRoleCode, id);
        }

        role.setRoleName(cmd.getRoleName().trim());
        role.setRoleCode(newRoleCode);
        if (cmd.getDescription() != null) {
            role.setDescription(cmd.getDescription());
        }
        role.setUpdatedAt(LocalDateTime.now());

        int rows = customRoleMapper.updateByIdIgnoreTenant(role);
        if (rows == 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "角色更新失败，请刷新后重试");
        }

        log.info("编辑自定义角色成功: id={}, operator={}", id, current.userId());
        return toVO(role);
    }

    @Override
    public CustomRoleVO getById(Long id) {
        TenantContext.Snapshot current = requireCurrentSnapshot();

        SysCustomRole role = customRoleMapper.selectByIdIgnoreTenant(id);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        validateTenantAccess(current, role.getTenantId());

        return toVO(role);
    }

    @Override
    public IPage<CustomRoleVO> list(int page, int size, String keyword) {
        TenantContext.Snapshot current = requireCurrentSnapshot();
        Long tenantId = current.isPlatformUser() ? null : current.tenantId();

        IPage<SysCustomRole> entityPage = customRoleMapper.selectPageList(
                new Page<>(page, size), tenantId, keyword);

        return entityPage.convert(this::toVO);
    }

    @Override
    @Transactional
    public void savePermissions(Long roleId, RolePermissionSaveCmd cmd) {
        TenantContext.Snapshot current = requireCurrentSnapshot();

        SysCustomRole role = customRoleMapper.selectByIdIgnoreTenant(roleId);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        validateTenantAccess(current, role.getTenantId());

        // 先删除旧权限，再批量插入新权限
        rolePermissionMapper.deleteByRoleId(roleId);

        LocalDateTime now = LocalDateTime.now();
        List<RolePermissionSaveCmd.Item> permissions = cmd.getPermissions();
        if (!CollectionUtils.isEmpty(permissions)) {
            for (RolePermissionSaveCmd.Item item : permissions) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionCode(item.getPermissionCode().trim());
                rp.setPermissionType(item.getPermissionType());
                rp.setDataScope(item.getDataScope());
                rp.setUpdatedAt(now);
                rolePermissionMapper.insert(rp);
            }
        }

        // 刷新持有该角色的所有管理员权限缓存
        refreshRolePermissionCache(roleId);

        log.info("保存角色权限矩阵成功: roleId={}, permissionCount={}, operator={}",
                roleId, permissions == null ? 0 : permissions.size(), current.userId());
    }

    @Override
    public List<RolePermissionVO> getPermissions(Long roleId) {
        TenantContext.Snapshot current = requireCurrentSnapshot();

        SysCustomRole role = customRoleMapper.selectByIdIgnoreTenant(roleId);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        validateTenantAccess(current, role.getTenantId());

        List<SysRolePermission> list = rolePermissionMapper.selectByRoleId(roleId);
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toPermissionVO).collect(Collectors.toList());
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
     * 解析角色所属租户。
     */
    private Long resolveTenantId(TenantContext.Snapshot current, Long cmdTenantId) {
        if (current.isPlatformUser()) {
            return cmdTenantId;
        }
        if (cmdTenantId != null && !Objects.equals(cmdTenantId, current.tenantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能创建本租户下的角色");
        }
        return current.tenantId();
    }

    /**
     * 校验租户访问权限。
     */
    private void validateTenantAccess(TenantContext.Snapshot current, Long roleTenantId) {
        if (current.isPlatformUser()) {
            return;
        }
        if (!Objects.equals(current.tenantId(), roleTenantId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "角色不属于本租户");
        }
    }

    /**
     * 校验角色编码租户内唯一。
     */
    private void validateRoleCodeUnique(Long tenantId, String roleCode, Long excludeId) {
        SysCustomRole exist = customRoleMapper.selectByRoleCodeIgnoreTenant(roleCode);
        if (exist != null && !Objects.equals(exist.getId(), excludeId)) {
            if (Objects.equals(exist.getTenantId(), tenantId)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "角色编码已存在");
            }
        }
    }

    /**
     * 刷新持有该角色的管理员权限缓存。
     */
    private void refreshRolePermissionCache(Long roleId) {
        try {
            // 查询所有绑定该角色的管理员账号 ID
            List<SysAdminAccountRole> relations = adminAccountRoleMapper.selectByRoleIdIgnoreTenant(roleId);
            if (CollectionUtils.isEmpty(relations)) {
                return;
            }
            for (SysAdminAccountRole relation : relations) {
                permissionProvider.refresh(relation.getAdminAccountId());
            }
        } catch (Exception e) {
            log.warn("刷新角色权限缓存失败（不影响权限保存）: roleId={}", roleId, e);
        }
    }

    /**
     * 角色实体转 VO。
     */
    private CustomRoleVO toVO(SysCustomRole role) {
        CustomRoleVO vo = new CustomRoleVO();
        vo.setId(role.getId());
        vo.setTenantId(role.getTenantId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setDescription(role.getDescription());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedAt(role.getUpdatedAt());
        return vo;
    }

    /**
     * 权限矩阵实体转 VO。
     */
    private RolePermissionVO toPermissionVO(SysRolePermission rp) {
        RolePermissionVO vo = new RolePermissionVO();
        vo.setId(rp.getId());
        vo.setRoleId(rp.getRoleId());
        vo.setPermissionCode(rp.getPermissionCode());
        vo.setPermissionType(rp.getPermissionType());
        vo.setDataScope(rp.getDataScope());
        vo.setCreatedAt(rp.getCreatedAt());
        return vo;
    }
}
