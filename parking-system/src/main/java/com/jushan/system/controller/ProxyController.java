package com.jushan.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.jushan.common.R;
import com.jushan.system.dto.ProxyRequest;
import com.jushan.system.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 平台代操作控制器（总后台）。
 * <p>
 * 提供超级管理员启动/停止代操作模式、获取当前代操作状态。
 * 启动代操作后，所有后续请求在目标租户上下文中执行，并记录真实操作人。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/proxy")
public class ProxyController {

    private final AuditService auditService;

    public ProxyController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 启动代操作模式。
     * <p>
     * 权限：仅超级管理员（super_admin）
     * <p>
     * 启动后当前用户的所有后续请求将：
     * <ul>
     *   <li>以目标租户身份执行数据操作</li>
     *   <li>所有写操作记录真实平台用户身份</li>
     *   <li>TenantContext.isProxyMode() 返回 true</li>
     * </ul>
     *
     * @param request 目标租户 ID 和原因
     * @param httpReq 用于提取客户端 IP
     */
    @PostMapping("/start")
    @SaCheckRole("super_admin")
    public R<Void> startProxy(@Valid @RequestBody ProxyRequest request, HttpServletRequest httpReq) {
        String clientIp = extractIp(httpReq);
        auditService.startProxy(request.getTenantId(), request.getReason(), clientIp);
        return R.ok();
    }

    /**
     * 停止代操作模式。
     * <p>
     * 清除代理状态，恢复到代操作前的平台用户身份。
     */
    @PostMapping("/stop")
    public R<Void> stopProxy(HttpServletRequest httpReq) {
        String clientIp = extractIp(httpReq);
        auditService.stopProxy(clientIp);
        return R.ok();
    }

    /**
     * 获取当前代操作状态。
     * <p>
     * 前端可据此显示"平台代操作"标识。
     */
    @GetMapping("/status")
    public R<java.util.Map<String, Object>> status() {
        return R.ok(auditService.getProxyStatus());
    }

    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
