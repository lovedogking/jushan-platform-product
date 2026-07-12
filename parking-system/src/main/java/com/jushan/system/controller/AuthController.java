package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.system.dto.ChangePasswordRequest;
import com.jushan.system.dto.LoginRequest;
import com.jushan.system.dto.LoginResult;
import com.jushan.system.service.AuthService;
import com.jushan.system.vo.LoginUserVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器。
 * <p>
 * 提供登录、退出和会话查询接口，与前端 {@code api/auth.ts} 对齐。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 平台账号登录。
     * <p>
     * 前端调用：{@code POST /api/auth/login}
     *
     * @param request 登录请求（username + password）
     * @param httpReq Servlet 请求（提取 IP 和 User-Agent）
     * @return 登录结果（Token + 用户信息）
     */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpReq) {
        String ip = extractIp(httpReq);
        String ua = httpReq.getHeader("User-Agent");
        LoginResult result = authService.login(request, ip, ua);
        return R.ok(result);
    }

    /**
     * 退出登录。
     * <p>
     * 前端调用：{@code POST /api/auth/logout}
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 获取当前会话用户信息。
     * <p>
     * 前端调用：{@code GET /api/auth/session}
     */
    @GetMapping("/session")
    public R<LoginUserVo> session() {
        LoginUserVo user = authService.getSessionUser();
        return R.ok(user);
    }

    /**
     * 修改密码（FIX-04）。
     * <p>
     * 验证旧密码后更新为新密码，并将凭据状态设为 ACTIVE。
     * 修改成功后旧 Token 失效，前端应跳转登录页。
     */
    @PostMapping("/change-password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }

    /**
     * 提取客户端 IP。
     */
    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
