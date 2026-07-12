package com.jushan.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.jushan.common.R;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限测试控制器。
 * <p>
 * 提供按不同权限等级保护的方法端点，
 * 用于验证 T13 固定角色和权限模型的集成测试。
 * <p>
 * <strong>此控制器仅用于测试，不需要注册到前端路由。</strong>
 * <p>
 * <strong>P0 安全修复（FIX-16）</strong>：
 * 限定为 dev/test Profile，生产环境不注册。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Profile({"dev", "test"})
@RestController
@RequestMapping("/test/permission")
public class PermissionTestController {

    // ==================== 权限级 — 注解方式 ====================

    @GetMapping("/gate-open")
    @SaCheckPermission("gate:open")
    public R<Map<String, String>> requireGateOpen() {
        return R.ok(Map.of("permission", "gate:open", "result", "allowed"));
    }

    @GetMapping("/gate-manual")
    @SaCheckPermission("gate:manual")
    public R<Map<String, String>> requireGateManual() {
        return R.ok(Map.of("permission", "gate:manual", "result", "allowed"));
    }

    @GetMapping("/device-manage")
    @SaCheckPermission("device:manage")
    public R<Map<String, String>> requireDeviceManage() {
        return R.ok(Map.of("permission", "device:manage", "result", "allowed"));
    }

    @GetMapping("/finance-manage")
    @SaCheckPermission("finance:manage")
    public R<Map<String, String>> requireFinanceManage() {
        return R.ok(Map.of("permission", "finance:manage", "result", "allowed"));
    }

    @GetMapping("/tenant-write")
    @SaCheckPermission("tenant:write")
    public R<Map<String, String>> requireTenantWrite() {
        return R.ok(Map.of("permission", "tenant:write", "result", "allowed"));
    }

    @GetMapping("/user-write")
    @SaCheckPermission("user:write")
    public R<Map<String, String>> requireUserWrite() {
        return R.ok(Map.of("permission", "user:write", "result", "allowed"));
    }

    @GetMapping("/parking-disable")
    @SaCheckPermission("parking:disable")
    public R<Map<String, String>> requireParkingDisable() {
        return R.ok(Map.of("permission", "parking:disable", "result", "allowed"));
    }

    @GetMapping("/fee-rule-write")
    @SaCheckPermission("fee-rule:write")
    public R<Map<String, String>> requireFeeRuleWrite() {
        return R.ok(Map.of("permission", "fee-rule:write", "result", "allowed"));
    }

    // ==================== 权限级 — 编程方式 ====================

    @GetMapping("/check-any-gate")
    public R<Map<String, Object>> checkAnyGatePermission() {
        boolean hasOpen = StpUtil.hasPermission("gate:open");
        boolean hasManual = StpUtil.hasPermission("gate:manual");
        return R.ok(Map.of(
                "gate:open", hasOpen,
                "gate:manual", hasManual
        ));
    }

    // ==================== 角色级 ====================

    @GetMapping("/role-super-admin")
    @SaCheckRole("super_admin")
    public R<Map<String, String>> requireSuperAdminRole() {
        return R.ok(Map.of("role", "super_admin", "result", "allowed"));
    }

    @GetMapping("/role-booth-operator")
    @SaCheckRole("booth_operator")
    public R<Map<String, String>> requireBoothOperatorRole() {
        return R.ok(Map.of("role", "booth_operator", "result", "allowed"));
    }

    // ==================== 公开端点（无需权限） ====================

    @GetMapping("/public")
    public R<Map<String, String>> publicEndpoint() {
        return R.ok(Map.of("access", "public", "result", "anyone"));
    }

    // ==================== 当前用户权限查询 ====================

    @GetMapping("/my-permissions")
    public R<Map<String, Object>> myPermissions() {
        List<String> permissions = StpUtil.getPermissionList();
        List<String> roles = StpUtil.getRoleList();
        return R.ok(Map.of(
                "roles", roles,
                "permissions", permissions
        ));
    }
}
