package com.jushan.platform.modules.miniapp.controller;

import com.jushan.common.R;
import com.jushan.platform.modules.miniapp.dto.MiniLoginRequest;
import com.jushan.platform.modules.miniapp.dto.MiniPhoneRequest;
import com.jushan.platform.modules.miniapp.service.MiniAuthService;
import com.jushan.platform.modules.miniapp.vo.BindPhoneResult;
import com.jushan.platform.modules.miniapp.vo.WxLoginResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序认证控制器。
 * <p>
 * 提供 POST /api/v1/mini/login 和 POST /api/v1/mini/phone。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/mini")
public class MiniAuthController {

    private final MiniAuthService miniAuthService;

    public MiniAuthController(MiniAuthService miniAuthService) {
        this.miniAuthService = miniAuthService;
    }

    /**
     * 小程序登录。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/login}
     * <p>
     * 通过 wx.login() 获取 code，后端使用 WeChatApiClient.code2session 换取 openid，
     * 签发 aud=miniapp 的 JWT。
     */
    @PostMapping("/login")
    public R<WxLoginResult> login(@Valid @RequestBody MiniLoginRequest request) {
        WxLoginResult result = miniAuthService.login(request);
        return R.ok(result);
    }

    /**
     * 绑定手机号。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/phone}
     * <p>
     * 通过 getPhoneNumber 返回的 code 换取真实手机号并绑定。
     */
    @PostMapping("/phone")
    public R<BindPhoneResult> bindPhone(@Valid @RequestBody MiniPhoneRequest request) {
        BindPhoneResult result = miniAuthService.bindPhone(request);
        return R.ok(result);
    }
}
