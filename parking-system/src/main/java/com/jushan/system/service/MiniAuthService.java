package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.platform.infra.security.JwtUtils;
import com.jushan.system.client.WeChatApiClient;
import com.jushan.system.dto.MiniLoginRequest;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.WxUser;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.WxUserMapper;
import com.jushan.system.vo.WxLoginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 微信小程序认证服务。
 * <p>
 * 负责微信小程序登录与手机号绑定，使用 WeChatApiClient 代理微信服务端接口调用。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class MiniAuthService {

    private static final Logger log = LoggerFactory.getLogger(MiniAuthService.class);

    /** 微信用户登录类型标识 */
    private static final String LOGIN_TYPE_WX = "wx_user";

    private final WxUserMapper wxUserMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final WeChatApiClient weChatApiClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public MiniAuthService(WxUserMapper wxUserMapper,
                           PlateBindingMapper plateBindingMapper,
                           WeChatApiClient weChatApiClient) {
        this.wxUserMapper = wxUserMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.weChatApiClient = weChatApiClient;
    }

    /**
     * 微信小程序登录。
     * <p>
     * 使用 code 换取 openid，查找或创建微信用户，生成 JWT Token。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @Transactional
    public WxLoginResult login(MiniLoginRequest request) {
        String code = request.getCode();

        // 1. 调用微信 code2session 获取 openid（支持 mock 模式）
        WeChatApiClient.Code2SessionResult sessionResult = weChatApiClient.code2session(code);
        String openid = sessionResult.getOpenid();
        String sessionKey = sessionResult.getSessionKey();

        // 2. 查询或创建用户
        WxUser wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUser>().eq(WxUser::getOpenid, openid));

        boolean isNewUser = (wxUser == null);
        if (wxUser == null) {
            wxUser = createWxUser(openid, sessionKey, request);
        } else {
            updateLoginInfo(wxUser, sessionKey, request);
        }

        // 3. 生成 JWT（audience = "miniapp"）
        String rolesJson = "[\"" + LOGIN_TYPE_WX + "\"]";
        String token = JwtUtils.generateToken(
                wxUser.getId(), null, LOGIN_TYPE_WX,
                rolesJson, null, "miniapp", jwtSecret, jwtExpiration);

        // 4. 查询绑定车牌数量
        long plateCount = plateBindingMapper.selectCount(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, wxUser.getId())
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED));

        // 5. 构建返回
        WxLoginResult result = new WxLoginResult();
        result.setToken(token);
        result.setUserId(wxUser.getId());
        result.setNickname(wxUser.getMaskedNickname());
        result.setAvatarUrl(wxUser.getAvatarUrl());
        result.setIsNewUser(isNewUser);
        result.setPlateCount((int) plateCount);
        result.setLoginTime(LocalDateTime.now());
        result.setMessage(isNewUser ? "欢迎首次使用" : "欢迎回来");
        result.setPhoneBound(Boolean.TRUE.equals(wxUser.getPhoneVerified()));

        log.info("微信小程序用户 {} 登录成功, isNew={}, plateCount={}, phoneBound={}",
                wxUser.getMaskedNickname(), isNewUser, plateCount, result.getPhoneBound());

        return result;
    }

    /**
     * 创建微信用户。
     */
    private WxUser createWxUser(String openid, String sessionKey, MiniLoginRequest request) {
        WxUser wxUser = new WxUser();
        wxUser.setOpenid(openid);
        wxUser.setSessionKey(sessionKey);
        wxUser.setNickname(request.getNickname() != null ? request.getNickname() : "");
        wxUser.setAvatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : "");
        wxUser.setPhoneVerified(false);
        wxUser.setStatus("ACTIVE");
        wxUser.setLastLoginAt(LocalDateTime.now());
        wxUser.setCreatedAt(LocalDateTime.now());
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.insert(wxUser);
        return wxUser;
    }

    /**
     * 更新登录信息。
     */
    private void updateLoginInfo(WxUser wxUser, String sessionKey, MiniLoginRequest request) {
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            wxUser.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            wxUser.setAvatarUrl(request.getAvatarUrl());
        }
        wxUser.setSessionKey(sessionKey);
        wxUser.setLastLoginAt(LocalDateTime.now());
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.updateById(wxUser);
    }
}
