package com.jushan.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.system.service.AuditService;
import com.jushan.system.vo.AuditLogVO;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志控制器（总后台 / 运营端）。
 * <p>
 * 提供高风险操作审计日志的分页查询和详情查看。
 * 平台用户可查看全平台日志；租户用户只能查看本租户日志。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 分页查询审计日志。
     * <p>
     * 权限：tenant:read（平台用户和租户用户均可访问，数据范围自动限制）
     * <p>
     * 支持筛选：
     * <ul>
     *   <li>tenantId — 目标租户（平台用户可选）</li>
     *   <li>action — 操作类型</li>
     *   <li>isProxy — 是否代操作</li>
     *   <li>operatorId — 操作人</li>
     *   <li>startTime / endTime — 时间范围</li>
     * </ul>
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param tenantId   目标租户筛选（可选）
     * @param action     操作类型筛选（可选）
     * @param isProxy    是否代操作筛选（可选）
     * @param operatorId 操作人 ID 筛选（可选）
     * @param startTime  开始时间（可选，ISO 格式）
     * @param endTime    结束时间（可选，ISO 格式）
     */
    @GetMapping
    @SaCheckPermission("tenant:read")
    public R<IPage<AuditLogVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) Long tenantId,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(required = false) Integer isProxy,
                                      @RequestParam(required = false) Long operatorId,
                                      @RequestParam(required = false) String startTime,
                                      @RequestParam(required = false) String endTime) {
        IPage<AuditLogVO> result = auditService.listAuditLogs(
                page, size, tenantId, action, isProxy, operatorId, startTime, endTime);
        return R.ok(result);
    }

    /**
     * 查询单条审计日志详情。
     * <p>
     * 权限：tenant:read
     */
    @GetMapping("/{id}")
    @SaCheckPermission("tenant:read")
    public R<AuditLogVO> detail(@PathVariable Long id) {
        return R.ok(auditService.getAuditLog(id));
    }
}
