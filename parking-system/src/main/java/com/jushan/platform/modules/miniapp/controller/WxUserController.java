package com.jushan.platform.modules.miniapp.controller;

import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.platform.modules.miniapp.dto.BindPhoneRequest;
import com.jushan.platform.modules.miniapp.dto.BindPlateRequest;
import com.jushan.platform.modules.miniapp.dto.MiniLoginRequest;
import com.jushan.platform.modules.miniapp.dto.UnbindPlateRequest;
import com.jushan.platform.modules.miniapp.dto.WxLoginRequest;
import com.jushan.platform.modules.miniapp.service.MiniAuthService;
import com.jushan.platform.modules.miniapp.service.WxUserService;
import com.jushan.platform.modules.miniapp.vo.PlateBindingVo;
import com.jushan.platform.modules.miniapp.vo.WxLoginResult;
import com.jushan.platform.modules.miniapp.vo.WxUserVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信用户控制器（车主端）。
 * <p>
 * 提供微信登录、车牌绑定和个人信息接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/wx")
public class WxUserController {

    private final WxUserService wxUserService;
    private final MiniAuthService miniAuthService;

    public WxUserController(WxUserService wxUserService, MiniAuthService miniAuthService) {
        this.wxUserService = wxUserService;
        this.miniAuthService = miniAuthService;
    }

    /**
     * 微信登录（已废弃，请使用 POST /api/v1/mini/login）。
     * <p>
     * 当前委托新 MiniAuthService 处理，保持向后兼容。
     *
     * @deprecated 请迁移至 POST /api/v1/mini/login
     */
    @PostMapping("/login")
    @Deprecated
    public R<WxLoginResult> login(@Valid @RequestBody WxLoginRequest request) {
        // 将 WxLoginRequest 转换为 MiniLoginRequest
        MiniLoginRequest miniRequest = new MiniLoginRequest();
        miniRequest.setCode(request.getCode());
        miniRequest.setNickname(request.getNickname());
        miniRequest.setAvatarUrl(request.getAvatarUrl());
        return R.ok(miniAuthService.login(miniRequest));
    }

    /**
     * 退出登录。
     * <p>
     * 前端调用：{@code POST /api/wx/logout}
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        wxUserService.logout();
        return R.ok();
    }

    /**
     * 获取当前微信用户信息。
     * <p>
     * 前端调用：{@code GET /api/wx/user}
     */
    @GetMapping("/user")
    public R<WxUserVo> getCurrentUser() {
        WxUserVo user = wxUserService.getCurrentUser();
        return R.ok(user);
    }

    /**
     * 绑定车牌。
     * <p>
     * 前端调用：{@code POST /api/wx/plates}
     */
    @PostMapping("/plates")
    public R<PlateBindingVo> bindPlate(@Valid @RequestBody BindPlateRequest request) {
        PlateBindingVo binding = wxUserService.bindPlate(request);
        return R.ok(binding);
    }

    /**
     * 解绑车牌。
     * <p>
     * 前端调用：{@code DELETE /api/wx/plates/:bindingId}
     */
    @DeleteMapping("/plates/{bindingId}")
    public R<Void> unbindPlate(@PathVariable Long bindingId) {
        UnbindPlateRequest request = new UnbindPlateRequest();
        request.setBindingId(bindingId);
        wxUserService.unbindPlate(request);
        return R.ok();
    }

    /**
     * 设置默认车牌。
     * <p>
     * 前端调用：{@code PUT /api/wx/plates/:bindingId/default}
     */
    @PutMapping("/plates/{bindingId}/default")
    public R<Void> setDefaultPlate(@PathVariable Long bindingId) {
        wxUserService.setDefaultPlate(bindingId);
        return R.ok();
    }

    /**
     * 绑定手机号（已废弃，请使用 POST /api/v1/mini/phone）。
     * <p>
     * 旧接口明文手机号绑定已废弃，getPhoneNumber 流程请用 /api/v1/mini/phone。
     *
     * @deprecated 请迁移至 POST /api/v1/mini/phone
     */
    @PostMapping("/phone")
    @Deprecated
    public R<Void> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        return R.fail(CommonErrorCode.METHOD_NOT_ALLOWED.getCode(),
                "此接口已废弃，请在微信小程序中更新版本");
    }
}