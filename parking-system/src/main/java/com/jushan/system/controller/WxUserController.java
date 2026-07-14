package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.system.dto.BindPhoneRequest;
import com.jushan.system.dto.BindPlateRequest;
import com.jushan.system.dto.UnbindPlateRequest;
import com.jushan.system.dto.WxLoginRequest;
import com.jushan.system.service.WxUserService;
import com.jushan.system.vo.PlateBindingVo;
import com.jushan.system.vo.WxLoginResult;
import com.jushan.system.vo.WxUserVo;
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

    public WxUserController(WxUserService wxUserService) {
        this.wxUserService = wxUserService;
    }

    /**
     * 微信登录。
     * <p>
     * 前端调用：{@code POST /api/wx/login}
     * <p>
     * 微信小程序通过 wx.login() 获取 code，后端使用 code 换取 openid。
     * 真实微信登录需要调用微信接口；本版本提供 Mock 登录用于 local/test 环境。
     */
    @PostMapping("/login")
    public R<WxLoginResult> login(@Valid @RequestBody WxLoginRequest request) {
        WxLoginResult result = wxUserService.login(request);
        return R.ok(result);
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
     * 绑定手机号。
     * <p>
     * 前端调用：{@code POST /api/wx/phone}
     */
    @PostMapping("/phone")
    public R<Void> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        wxUserService.bindPhone(request);
        return R.ok();
    }
}