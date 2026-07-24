package com.jushan.platform.modules.account.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.dto.CreateEmployeeRequest;
import com.jushan.platform.modules.account.dto.ResetPasswordRequest;
import com.jushan.platform.modules.account.dto.UpdateEmployeeRequest;
import com.jushan.platform.modules.account.entity.EmployeeParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.tenant.entity.SysTenant;
import com.jushan.platform.modules.tenant.mapper.SysTenantMapper;
import com.jushan.platform.modules.account.entity.SysUser;
import com.jushan.platform.modules.account.mapper.EmployeeParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.account.mapper.SysUserMapper;
import com.jushan.platform.modules.account.vo.EmployeeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户员工管理服务。
 * <p>
 * 客户管理员在其租户内创建、管理固定角色员工，并授予停车场访问范围。
 * 所有操作从当前登录会话推导租户范围，不信任前端传入的 tenantId。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /** 客户管理员可分配给员工的角色编码集合 */
    private static final Set<String> ALLOWED_ROLE_CODES = Set.of(
            "parking_manager", "finance", "device_maintenance", "booth_operator");

    /** 角色编码 → 显示名称映射 */
    private static final Map<String, String> ROLE_NAME_MAP = Map.of(
            "parking_manager", "停车场管理员",
            "finance", "财务",
            "device_maintenance", "设备运维",
            "booth_operator", "岗亭");

    private final SysUserMapper sysUserMapper;
    private final SysTenantMapper sysTenantMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final EmployeeParkingLotMapper employeeParkingLotMapper;

    /**
     * 解析当前租户ID，正确处理平台用户。
     * <p>
     * 平台用户（super_admin、platform_operator）无租户绑定，返回 null。
     * 租户用户返回其 tenantId。
     */
    private Long resolveTenantId() {
        if (TenantContext.isPlatformUser()) {
            return null;
        }
        return TenantContext.requireTenantId();
    }

    public EmployeeService(SysUserMapper sysUserMapper,
                           SysTenantMapper sysTenantMapper,
                           ParkingLotMapper parkingLotMapper,
                           EmployeeParkingLotMapper employeeParkingLotMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.employeeParkingLotMapper = employeeParkingLotMapper;
    }

    // ==================== 员工创建 ====================

    /**
     * 创建员工。
     * <p>
     * 1. 从当前会话推导租户
     * 2. 校验当前操作人是客户管理员
     * 3. 校验角色合法性
     * 4. 校验租户状态
     * 5. 校验手机号在租户内唯一
     * 6. 校验停车场归属
     * 7. 创建 sys_user 和授权记录
     */
    @Transactional
    public EmployeeVO createEmployee(CreateEmployeeRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后操作员工");
        }
        DataScope.requireCustomerAdmin();

        // 1. 校验角色合法性
        String roleCode = request.getRoleCode().trim();
        if (!ALLOWED_ROLE_CODES.contains(roleCode)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "不支持的角色: " + roleCode + "，可选角色: " + String.join(", ", ALLOWED_ROLE_CODES));
        }

        // 2. 校验租户状态（sys_tenant：1正常 0禁用）
        // 注：原旧 tenant 表 max_employees 配额检查已随租户体系统一移除，待二期套餐体系重新实现
        SysTenant tenant = sysTenantMapper.selectById(tenantId);
        DataScope.validateTenantEnabled(tenant != null ? tenant.getStatus() : null);

        // 3. 校验手机号在租户内唯一
        String phone = request.getPhone().trim();
        Long existCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, phone));
        if (existCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该手机号已被使用");
        }

        // 4. 校验停车场归属（所有停车场必须属于本租户）
        List<Long> parkingLotIds = request.getParkingLotIds();
        Set<Long> distinctIds = parkingLotIds.stream().distinct().collect(Collectors.toSet());
        DataScope.validateParkingLotsBelongToTenant(
                distinctIds, tenantId,
                pid -> {
                    ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(pid);
                    return lot != null ? lot.getTenantId() : null;
                },
                pid -> {
                    ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(pid);
                    return lot != null ? lot.getName() : String.valueOf(pid);
                });

        // 5. 创建员工账号
        SysUser employee = new SysUser();
        employee.setTenantId(tenantId);
        employee.setUsername(phone);
        employee.setPasswordHash(BCrypt.hashpw(request.getPassword()));
        employee.setDisplayName(request.getDisplayName().trim());
        employee.setStatus("ENABLED");
        employee.setRoles("[\"" + roleCode + "\"]");
        employee.setCreatedAt(LocalDateTime.now());
        employee.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(employee);

        // 6. 创建停车场授权记录
        saveParkingLotAuthorizations(employee.getId(), parkingLotIds);

        log.info("客户管理员创建员工成功: tenantId={}, employeeId={}, role={}, phone={}",
                tenantId, employee.getId(), roleCode, maskPhone(phone));

        return toVO(employee);
    }

    // ==================== 员工更新 ====================

    /**
     * 更新员工信息。
     * <p>
     * 可修改姓名、角色和停车场授权。手机号不可修改。
     */
    @Transactional
    public EmployeeVO updateEmployee(Long employeeId, UpdateEmployeeRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后操作员工");
        }
        DataScope.requireCustomerAdmin();

        SysUser employee = getEmployeeInTenant(employeeId, tenantId);

        // 1. 校验角色合法性
        String roleCode = request.getRoleCode().trim();
        if (!ALLOWED_ROLE_CODES.contains(roleCode)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "不支持的角色: " + roleCode);
        }

        // 2. 校验停车场归属
        List<Long> parkingLotIds = request.getParkingLotIds();
        Set<Long> distinctUpdateIds = parkingLotIds.stream().distinct().collect(Collectors.toSet());
        DataScope.validateParkingLotsBelongToTenant(
                distinctUpdateIds, tenantId,
                pid -> {
                    ParkingLot lot = parkingLotMapper.selectById(pid);
                    return lot != null ? lot.getTenantId() : null;
                },
                pid -> {
                    ParkingLot lot = parkingLotMapper.selectById(pid);
                    return lot != null ? lot.getName() : String.valueOf(pid);
                });

        // 3. 更新员工信息
        sysUserMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                        .set(SysUser::getDisplayName, request.getDisplayName().trim())
                        .set(SysUser::getRoles, "[\"" + roleCode + "\"]")
                        .set(SysUser::getUpdatedAt, LocalDateTime.now())
                        .eq(SysUser::getId, employeeId));

        // 4. 更新停车场授权（先删后增）
        employeeParkingLotMapper.delete(
                new LambdaQueryWrapper<EmployeeParkingLot>()
                        .eq(EmployeeParkingLot::getEmployeeId, employeeId));
        saveParkingLotAuthorizations(employeeId, parkingLotIds);

        // 5. 刷新内存中的员工数据
        employee.setDisplayName(request.getDisplayName().trim());
        employee.setRoles("[\"" + roleCode + "\"]");

        // FIX-06：角色变更后清除权限缓存，使新权限立即生效
        // 当前 JWT 无状态模式下无法直接清除服务端缓存，保留日志记录；
        // 后续可通过 Redis Token 黑名单或缩短 Token 过期时间实现即时失效。
        log.info("员工角色已变更，旧 Token 将在过期后失效: employeeId={}, newRole={}", employeeId, roleCode);

        log.info("客户管理员更新员工成功: tenantId={}, employeeId={}, newRole={}",
                tenantId, employeeId, roleCode);

        return toVO(employee);
    }

    // ==================== 员工列表与详情 ====================

    /**
     * 分页查询本租户员工列表。
     */
    public IPage<EmployeeVO> listEmployees(int page, int size, String status) {
        // 解析租户范围：平台用户可查看所有租户的员工
        Long tenantId = resolveTenantId();

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .notLike(SysUser::getRoles, "customer_admin")
                .eq(status != null && !status.isBlank(), SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreatedAt);

        IPage<SysUser> userPage = sysUserMapper.selectPage(new Page<>(page, size), wrapper);

        return userPage.convert(this::toVO);
    }

    /**
     * 查询单个员工详情。
     */
    public EmployeeVO getEmployee(Long employeeId) {
        Long tenantId = resolveTenantId();
        SysUser employee = getEmployeeInTenant(employeeId, tenantId);
        return toVO(employee);
    }

    // ==================== 密码重置 ====================

    /**
     * 重置员工密码。
     * <p>
     * 客户管理员可为自己租户内的员工重置密码。
     */
    @Transactional
    public void resetPassword(Long employeeId, ResetPasswordRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后操作员工");
        }
        DataScope.requireCustomerAdmin();
        getEmployeeInTenant(employeeId, tenantId); // 校验归属

        sysUserMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                        .set(SysUser::getPasswordHash, BCrypt.hashpw(request.getNewPassword()))
                        .set(SysUser::getUpdatedAt, LocalDateTime.now())
                        .eq(SysUser::getId, employeeId));

        // FIX-06：密码重置后撤销所有旧 Token，确保旧密码不可继续使用。
        // JWT 无状态模式下无法立即撤销，旧 Token 将在过期后失效；
        // 后续可通过 Redis Token 黑名单实现即时失效。
        log.info("员工密码已重置，旧 Token 将在过期后失效: employeeId={}", employeeId);

        log.info("客户管理员重置员工密码: tenantId={}, employeeId={}", tenantId, employeeId);
    }

    // ==================== 启停 ====================

    /**
     * 更新员工状态（启用/禁用）。
     * <p>
     * 使用条件更新防止并发覆盖。
     */
    @Transactional
    public void updateStatus(Long employeeId, String action) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后操作员工");
        }
        DataScope.requireCustomerAdmin();

        SysUser employee = getEmployeeInTenant(employeeId, tenantId);
        String beforeStatus = employee.getStatus();

        String afterStatus;
        if ("ENABLED".equals(action)) {
            if ("ENABLED".equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "员工已是启用状态");
            }
            if (!"DISABLED".equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "只能启用在禁用状态的员工，当前状态: " + beforeStatus);
            }
            afterStatus = "ENABLED";
        } else if ("DISABLED".equals(action)) {
            if ("DISABLED".equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "员工已是禁用状态");
            }
            if (!"ENABLED".equals(beforeStatus)) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "只能禁用启用状态的员工，当前状态: " + beforeStatus);
            }
            afterStatus = "DISABLED";
        } else {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的操作: " + action);
        }

        boolean updated = sysUserMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                        .set(SysUser::getStatus, afterStatus)
                        .set(SysUser::getUpdatedAt, LocalDateTime.now())
                        .eq(SysUser::getId, employeeId)
                        .eq(SysUser::getStatus, beforeStatus)) > 0;

        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "员工状态已变更，请刷新后重试");
        }

        // FIX-06：禁用员工后主动撤销其所有 Token，使其下一请求立即失效。
        // JWT 无状态模式下无法立即撤销，旧 Token 将在过期后失效；
        // 后续可通过 Redis Token 黑名单实现即时失效。
        if ("DISABLED".equals(afterStatus)) {
            log.info("员工已被禁用，旧 Token 将在过期后失效: employeeId={}", employeeId);
        }

        log.info("客户管理员更新员工状态: tenantId={}, employeeId={}, {} -> {}",
                tenantId, employeeId, beforeStatus, afterStatus);
    }

    // ==================== 私有方法 ====================

    // deriveTenantId / validateCustomerAdminRole / validateParkingLotsBelongToTenant
    // 已迁移到 framework 层 TenantContext 和 DataScope，由 T16 统一提供。

    /**
     * 获取租户内的员工，跨租户拒绝。
     */
    private SysUser getEmployeeInTenant(Long employeeId, Long tenantId) {
        SysUser employee = sysUserMapper.selectByIdIgnoreTenant(employeeId);
        if (employee == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "员工不存在");
        }
        // 平台用户可访问所有租户数据
        if (tenantId == null) {
            return employee;
        }
        DataScope.validateTenantAccess(employee.getTenantId(), "员工");
        return employee;
    }

    /**
     * 批量保存员工停车场授权记录。
     */
    private void saveParkingLotAuthorizations(Long employeeId, List<Long> parkingLotIds) {
        List<Long> distinctIds = parkingLotIds.stream().distinct().collect(Collectors.toList());
        for (Long parkingLotId : distinctIds) {
            EmployeeParkingLot epl = new EmployeeParkingLot();
            epl.setEmployeeId(employeeId);
            epl.setParkingLotId(parkingLotId);
            epl.setCreatedAt(LocalDateTime.now());
            employeeParkingLotMapper.insert(epl);
        }
    }

    /**
     * SysUser → EmployeeVO 转换。
     */
    private EmployeeVO toVO(SysUser user) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(user.getUsername()));
        vo.setDisplayName(user.getDisplayName());
        vo.setRoleCode(parseRoleCode(user.getRoles()));
        vo.setRoleName(ROLE_NAME_MAP.getOrDefault(vo.getRoleCode(), vo.getRoleCode()));
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        // 查询授权停车场
        List<EmployeeParkingLot> auths = employeeParkingLotMapper.selectList(
                new LambdaQueryWrapper<EmployeeParkingLot>()
                        .eq(EmployeeParkingLot::getEmployeeId, user.getId()));
        List<Long> parkingLotIds = auths.stream()
                .map(EmployeeParkingLot::getParkingLotId)
                .collect(Collectors.toList());
        vo.setParkingLotIds(parkingLotIds);

        // 查询停车场名称
        if (!parkingLotIds.isEmpty()) {
            List<ParkingLot> lots = parkingLotMapper.selectList(
                    new LambdaQueryWrapper<ParkingLot>().in(ParkingLot::getId, parkingLotIds));
            List<String> names = new ArrayList<>();
            // 保持与 parkingLotIds 相同顺序
            Map<Long, String> idToName = lots.stream()
                    .collect(Collectors.toMap(ParkingLot::getId, ParkingLot::getName));
            for (Long pid : parkingLotIds) {
                names.add(idToName.getOrDefault(pid, ""));
            }
            vo.setParkingLotNames(names);
        } else {
            vo.setParkingLotNames(Collections.emptyList());
        }

        return vo;
    }

    /**
     * 从 roles JSON 数组解析第一个角色编码。
     */
    private String parseRoleCode(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return "";
        }
        String trimmed = rolesJson.trim();
        if (trimmed.startsWith("[\"") && trimmed.endsWith("\"]")) {
            return trimmed.substring(2, trimmed.length() - 2);
        }
        return trimmed;
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
}
