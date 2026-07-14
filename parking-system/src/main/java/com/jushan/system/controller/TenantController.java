package com.jushan.system.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.system.dto.TenantAuditRequest;
import com.jushan.system.service.TenantService;
import com.jushan.system.vo.TenantVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 租户管理控制器（总后台）。
 * <p>
 * 提供租户审核、启停、列表查询和详情查看接口。
 * 仅超级管理员和平台运营可访问。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * 分页查询租户列表。
     * <p>
     * 权限：tenant:read
     *
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @param status 状态筛选（可选：PENDING_REVIEW / ENABLED / DISABLED / REJECTED）
     */
    @GetMapping
    @RequirePermission("tenant:read")
    public R<IPage<TenantVO>> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String status) {
        IPage<TenantVO> result = tenantService.listTenants(page, size, status);
        return R.ok(result);
    }

    /**
     * 查询租户详情。
     * <p>
     * 权限：tenant:read
     */
    @GetMapping("/{id}")
    @RequirePermission("tenant:read")
    public R<TenantVO> detail(@PathVariable Long id) {
        TenantVO vo = tenantService.getTenant(id);
        return R.ok(vo);
    }

    /**
     * 审核/启停租户。
     * <p>
     * 权限：tenant:write
     * <p>
     * 支持的操作类型：
     * <ul>
     *   <li>APPROVED — 审核通过（PENDING_REVIEW → ENABLED）</li>
     *   <li>REJECTED — 审核拒绝（PENDING_REVIEW → REJECTED）</li>
     *   <li>ENABLED — 启用（DISABLED → ENABLED）</li>
     *   <li>DISABLED — 禁用（ENABLED → DISABLED）</li>
     * </ul>
     */
    @PostMapping("/{id}/audit")
    @RequirePermission("tenant:write")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody TenantAuditRequest request) {
        tenantService.audit(id, request);
        return R.ok();
    }
}
