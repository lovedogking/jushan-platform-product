package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.platform.modules.auth.controller.AuthController;
import com.jushan.platform.modules.auth.dto.LoginRequest;
import com.jushan.platform.modules.auth.vo.LoginResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 岗亭端认证控制器 — 桥接 Frontend → Backend 认证路径。
 * <p>
 * frontend vite 代理将 /api/auth/login 转发到 /auth/login，
 * 本控制器将请求转发给实际的 AuthController 并适配响应格式。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/auth")
public class BoothAuthController {

    private static final Logger log = LoggerFactory.getLogger(BoothAuthController.class);

    private final AuthController authController;

    public BoothAuthController(AuthController authController) {
        this.authController = authController;
    }

    /**
     * 登录 — 适配 frontend 期望的响应格式。
     * frontend 期望：
     * {
     *   accessToken: string,
     *   tokenType: string,
     *   expiresInSeconds: number,
     *   user: { userId, username, displayName, roles, permissions }
     * }
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        log.info("岗亭端登录: username={}", request.getUsername());
        R<LoginResult> original = authController.login(request);
        LoginResult data = original.getData();
        if (data == null) {
            return R.fail(original.getCode(), original.getMessage());
        }

        LoginResult.UserInfo userInfo = data.getUserInfo();
        Map<String, Object> boothResult = Map.of(
            "accessToken", data.getToken(),
            "tokenType", "Bearer",
            "expiresInSeconds", 86400,
            "user", Map.of(
                "userId", userInfo != null ? String.valueOf(userInfo.getUserId()) : "",
                "username", userInfo != null ? userInfo.getUsername() : "",
                "displayName", userInfo != null ? userInfo.getRealName() : "",
                "roles", List.of(),
                "permissions", data.getPermissions() != null ? data.getPermissions() : List.of()
            )
        );
        return R.ok(boothResult);
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        return authController.logout();
    }

    @GetMapping("/session")
    public R<Map<String, Object>> session() {
        // 返回适配 frontend 格式的会话信息
        R<LoginResult.UserInfo> original = authController.userinfo();
        LoginResult.UserInfo userInfo = original.getData();
        if (userInfo == null) {
            return R.fail(original.getCode(), original.getMessage());
        }
        Map<String, Object> sessionInfo = Map.of(
            "userId", String.valueOf(userInfo.getUserId()),
            "username", userInfo.getUsername(),
            "displayName", userInfo.getRealName() != null ? userInfo.getRealName() : userInfo.getUsername(),
            "roles", List.of(),
            "permissions", List.of()
        );
        return R.ok(sessionInfo);
    }
}
