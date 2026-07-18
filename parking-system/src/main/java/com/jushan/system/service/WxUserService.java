package com.jushan.system.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.JwtUtils;
import com.jushan.system.client.WeChatApiClient;
import com.jushan.system.dto.BindPhoneRequest;
import com.jushan.system.dto.BindPlateRequest;
import com.jushan.system.dto.UnbindPlateRequest;
import com.jushan.system.dto.WxLoginRequest;
import com.jushan.system.entity.*;
import com.jushan.system.mapper.*;
import com.jushan.system.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 微信用户服务（车主端）。
 * <p>
 * 负责微信登录、手机号绑定、车辆/车牌管理和绑定验证策略。
 * <p>
 * 注意：微信登录依赖真实的 AppId/Secret（外部依赖 B05）。
 * 当前实现提供 Mock 登录用于 local/test 环境开发测试。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class WxUserService {

    private static final Logger log = LoggerFactory.getLogger(WxUserService.class);

    /** 微信用户登录类型标识 */
    private static final String LOGIN_TYPE_WX = "wx_user";

    private final WxUserMapper wxUserMapper;
    private final VehicleMapper vehicleMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final BindingPolicyMapper bindingPolicyMapper;
    private final WeChatApiClient weChatApiClient;
    private final ParamResolver paramResolver;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public WxUserService(WxUserMapper wxUserMapper,
                         VehicleMapper vehicleMapper,
                         PlateBindingMapper plateBindingMapper,
                         BindingPolicyMapper bindingPolicyMapper,
                         WeChatApiClient weChatApiClient,
                         ParamResolver paramResolver) {
        this.wxUserMapper = wxUserMapper;
        this.vehicleMapper = vehicleMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.bindingPolicyMapper = bindingPolicyMapper;
        this.weChatApiClient = weChatApiClient;
        this.paramResolver = paramResolver;
    }

    /**
     * 微信登录。
     * <p>
     * 真实微信登录需要调用微信接口使用 code 换取 openid。
     * 当前版本提供 Mock 登录用于开发测试（仅 local/test 环境）。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @Transactional
    public WxLoginResult login(WxLoginRequest request) {
        String code = request.getCode();

        // 1. 获取 openid（Mock 或真实）
        String openid = resolveOpenid(code);

        // 2. 查询或创建用户
        WxUser wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUser>().eq(WxUser::getOpenid, openid));

        boolean isNewUser = (wxUser == null);
        if (wxUser == null) {
            wxUser = createWxUser(openid, request);
        } else {
            // 更新登录信息
            updateLoginInfo(wxUser, request);
        }

        // 3. JWT 登录（微信用户使用独立类型）
        String rolesJson = "[\"" + LOGIN_TYPE_WX + "\"]";
        String token = JwtUtils.generateToken(
                wxUser.getId(), null, LOGIN_TYPE_WX,
                rolesJson, null, jwtSecret, jwtExpiration);

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
        result.setPhoneBound(Boolean.TRUE.equals(wxUser.getPhoneVerified()));
        result.setPlateCount((int) plateCount);
        result.setLoginTime(LocalDateTime.now());
        result.setMessage(isNewUser ? "欢迎首次使用" : "欢迎回来");

        log.info("微信用户 {} 登录成功, isNew={}, plateCount={}",
                wxUser.getMaskedNickname(), isNewUser, plateCount);

        return result;
    }

    /**
     * 解析 openid（委托给 WeChatApiClient，由它统一处理 mock/真实模式）。
     */
    private String resolveOpenid(String code) {
        return weChatApiClient.code2session(code).getOpenid();
    }

    /**
     * 创建微信用户。
     */
    private WxUser createWxUser(String openid, WxLoginRequest request) {
        WxUser wxUser = new WxUser();
        wxUser.setOpenid(openid);
        wxUser.setNickname(request.getNickname() != null ? request.getNickname() : "");
        wxUser.setAvatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : "");
        wxUser.setPhoneVerified(false);
        wxUser.setStatus(WxUser.class.getSimpleName().toUpperCase().contains("ACTIVE") ? "ACTIVE" : "ACTIVE");
        wxUser.setLastLoginAt(LocalDateTime.now());
        wxUser.setCreatedAt(LocalDateTime.now());
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.insert(wxUser);
        return wxUser;
    }

    /**
     * 更新登录信息。
     */
    private void updateLoginInfo(WxUser wxUser, WxLoginRequest request) {
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            wxUser.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            wxUser.setAvatarUrl(request.getAvatarUrl());
        }
        wxUser.setLastLoginAt(LocalDateTime.now());
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.updateById(wxUser);
    }

    /**
     * 获取当前微信用户信息。
     *
     * @return 用户信息
     */
    public WxUserVo getCurrentUser() {
        Long userId = getCurrentWxUserId();
        WxUser wxUser = wxUserMapper.selectById(userId);
        if (wxUser == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "用户不存在");
        }

        WxUserVo vo = new WxUserVo();
        vo.setId(wxUser.getId());
        vo.setNickname(wxUser.getMaskedNickname());
        vo.setAvatarUrl(wxUser.getAvatarUrl());
        vo.setMaskedPhone(wxUser.getMaskedPhone());
        vo.setPhoneVerified(wxUser.getPhoneVerified());
        vo.setStatus(wxUser.getStatus());
        vo.setLastLoginAt(wxUser.getLastLoginAt());

        // 查询绑定车牌
        List<PlateBinding> bindings = plateBindingMapper.selectList(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, userId)
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED)
                        .orderByDesc(PlateBinding::getIsDefault)
                        .orderByDesc(PlateBinding::getCreatedAt));

        vo.setPlateCount(bindings.size());

        // 填充车牌详情
        List<PlateBindingVo> plates = new ArrayList<>();
        PlateBindingVo defaultPlate = null;
        for (PlateBinding binding : bindings) {
            Vehicle vehicle = vehicleMapper.selectById(binding.getVehicleId());
            if (vehicle == null) {
                continue;
            }
            PlateBindingVo plateVo = toPlateBindingVo(binding, vehicle);
            plates.add(plateVo);
            if (Boolean.TRUE.equals(binding.getIsDefault())) {
                defaultPlate = plateVo;
            }
        }
        vo.setPlates(plates);
        vo.setDefaultPlate(defaultPlate);

        return vo;
    }

    /**
     * 绑定车牌。
     *
     * @param request 绑定请求
     * @return 绑定记录
     */
    @Transactional
    public PlateBindingVo bindPlate(BindPlateRequest request) {
        Long userId = getCurrentWxUserId();

        // 1. 检查绑定策略
        BindingPolicy policy = getBindingPolicy(null, null);
        checkBindingPolicy(userId, request.getPlate(), policy);

        // 2. 查找或创建车辆
        String plate = normalizePlate(request.getPlate());
        Vehicle vehicle = vehicleMapper.selectOne(
                new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getVehiclePlate, plate));
        if (vehicle == null) {
            vehicle = createVehicle(request, plate);
        }

        // 3. 检查是否已绑定
        PlateBinding existing = plateBindingMapper.selectOne(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, userId)
                        .eq(PlateBinding::getVehicleId, vehicle.getId()));
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该车牌已绑定");
        }

        // 4. 创建绑定记录
        String verifyMethod = request.getVerifyMethod() != null
                ? request.getVerifyMethod() : policy.getDefaultVerifyMethod();
        String verifyStatus = PlateBinding.VERIFY_METHOD_PLATE_ONLY.equals(verifyMethod)
                ? PlateBinding.VERIFY_STATUS_APPROVED : PlateBinding.VERIFY_STATUS_PENDING;

        PlateBinding binding = new PlateBinding();
        binding.setWxUserId(userId);
        binding.setVehicleId(vehicle.getId());
        binding.setBindingType(PlateBinding.BINDING_TYPE_OWNER);
        binding.setVerifyMethod(verifyMethod);
        binding.setVerifyStatus(verifyStatus);
        binding.setIsDefault(plateBindingMapper.selectCount(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, userId)) == 0);
        binding.setCreatedAt(LocalDateTime.now());
        binding.setUpdatedAt(LocalDateTime.now());
        if (PlateBinding.VERIFY_STATUS_APPROVED.equals(verifyStatus)) {
            binding.setVerifiedAt(LocalDateTime.now());
        }
        plateBindingMapper.insert(binding);

        log.info("用户 {} 绑定车牌 {}", userId, plate);

        return toPlateBindingVo(binding, vehicle);
    }

    /**
     * 解绑车牌。
     *
     * @param request 解绑请求
     */
    @Transactional
    public void unbindPlate(UnbindPlateRequest request) {
        Long userId = getCurrentWxUserId();

        PlateBinding binding = plateBindingMapper.selectById(request.getBindingId());
        if (binding == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "绑定记录不存在");
        }

        // 验证归属
        if (!binding.getWxUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作此绑定");
        }

        plateBindingMapper.deleteById(binding.getId());

        log.info("用户 {} 解绑车牌 ID={}, reason={}",
                userId, binding.getId(), request.getReason());
    }

    /**
     * 设置默认车牌。
     *
     * @param bindingId 绑定记录 ID
     */
    @Transactional
    public void setDefaultPlate(Long bindingId) {
        Long userId = getCurrentWxUserId();

        PlateBinding binding = plateBindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "绑定记录不存在");
        }
        if (!binding.getWxUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作此绑定");
        }

        // 清除其他默认
        plateBindingMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, userId)
                        .set(PlateBinding::getIsDefault, false));

        // 设置新的默认
        binding.setIsDefault(true);
        binding.setUpdatedAt(LocalDateTime.now());
        plateBindingMapper.updateById(binding);

        log.info("用户 {} 设置默认车牌 ID={}", userId, bindingId);
    }

    /**
     * 绑定手机号。
     *
     * @param request 绑定请求
     */
    @Transactional
    public void bindPhone(BindPhoneRequest request) {
        Long userId = getCurrentWxUserId();

        // 验证手机号格式
        String phone = request.getPhone();
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "手机号格式不正确");
        }

        // 检查手机号是否已被其他用户绑定
        WxUser existing = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUser>()
                        .eq(WxUser::getPhone, phone)
                        .ne(WxUser::getId, userId));
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该手机号已被其他账号绑定");
        }

        // 验证码校验
        if (request.getVerifyCode() == null || request.getVerifyCode().isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "验证码不能为空");
        }

        WxUser wxUser = wxUserMapper.selectById(userId);
        wxUser.setPhone(phone);
        wxUser.setPhoneVerified(true);
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.updateById(wxUser);

        log.info("用户 {} 绑定手机号 {}", userId, maskPhone(phone));
    }

    /**
     * 退出登录。
     */
    public void logout() {
        Long userId = TenantContext.getUserId();
        log.info("微信用户 ID={} 退出登录", userId);
        // JWT 无状态模式下服务端无需主动撤销，客户端清除 Token 即可
    }

    /**
     * 获取当前微信用户 ID。
     */
    public Long getCurrentWxUserId() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    /**
     * 检查绑定策略。
     */
    private void checkBindingPolicy(Long userId, String plate, BindingPolicy policy) {
        // 绑定上限：策略配置优先，否则从系统参数/默认值 3
        int maxBindings;
        if (policy != null && policy.getMaxBindingsPerUser() != null && policy.getMaxBindingsPerUser() > 0) {
            maxBindings = policy.getMaxBindingsPerUser();
        } else {
            maxBindings = paramResolver.getInt("vehicle.bind_limit_per_user", null, 3);
        }

        // 检查用户绑定数量上限
        long userBindingCount = plateBindingMapper.selectCount(
                new LambdaQueryWrapper<PlateBinding>().eq(PlateBinding::getWxUserId, userId));
        if (userBindingCount >= maxBindings) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "绑定车辆数量已达上限（" + maxBindings + "辆）");
        }

        // 检查车牌绑定用户数量上限
        String normalizedPlate = normalizePlate(plate);
        Vehicle vehicle = vehicleMapper.selectOne(
                new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getVehiclePlate, normalizedPlate));
        if (vehicle != null) {
            long plateUserCount = plateBindingMapper.selectCount(
                    new LambdaQueryWrapper<PlateBinding>()
                            .eq(PlateBinding::getVehicleId, vehicle.getId())
                            .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED));
            if (plateUserCount >= policy.getMaxUsersPerPlate()) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "该车牌绑定用户数已达上限");
            }
        }
    }

    /**
     * 获取绑定策略（按停车场 > 租户 > 平台顺序）。
     */
    private BindingPolicy getBindingPolicy(Long tenantId, Long parkingLotId) {
        // 先查停车场级
        if (parkingLotId != null) {
            BindingPolicy policy = bindingPolicyMapper.selectOne(
                    new LambdaQueryWrapper<BindingPolicy>()
                            .eq(BindingPolicy::getParkingLotId, parkingLotId)
                            .eq(BindingPolicy::getStatus, BindingPolicy.STATUS_ENABLED));
            if (policy != null) {
                return policy;
            }
        }
        // 再查租户级
        if (tenantId != null) {
            BindingPolicy policy = bindingPolicyMapper.selectOne(
                    new LambdaQueryWrapper<BindingPolicy>()
                            .eq(BindingPolicy::getTenantId, tenantId)
                            .isNull(BindingPolicy::getParkingLotId)
                            .eq(BindingPolicy::getStatus, BindingPolicy.STATUS_ENABLED));
            if (policy != null) {
                return policy;
            }
        }
        // 最后查平台默认
        return bindingPolicyMapper.selectOne(
                new LambdaQueryWrapper<BindingPolicy>()
                        .isNull(BindingPolicy::getTenantId)
                        .isNull(BindingPolicy::getParkingLotId)
                        .eq(BindingPolicy::getStatus, BindingPolicy.STATUS_ENABLED));
    }

    /**
     * 创建车辆记录。
     */
    private Vehicle createVehicle(BindPlateRequest request, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehiclePlate(plate);
        vehicle.setVehicleType(request.getVehicleType() != null ? request.getVehicleType() : Vehicle.TYPE_SMALL);
        vehicle.setBrand(request.getBrand() != null ? request.getBrand() : "");
        vehicle.setColor(request.getColor() != null ? request.getColor() : "");
        vehicle.setOwnerName(request.getOwnerName() != null ? request.getOwnerName() : "");
        vehicle.setStatus(Vehicle.STATUS_ACTIVE);
        vehicle.setCreatedAt(LocalDateTime.now());
        vehicle.setUpdatedAt(LocalDateTime.now());
        vehicleMapper.insert(vehicle);
        return vehicle;
    }

    /**
     * 标准化车牌号（大写、去除空格）。
     */
    private String normalizePlate(String plate) {
        if (plate == null) {
            return "";
        }
        return plate.toUpperCase().replaceAll("\\s+", "").trim();
    }

    /**
     * 转换绑定记录为 VO。
     */
    private PlateBindingVo toPlateBindingVo(PlateBinding binding, Vehicle vehicle) {
        PlateBindingVo vo = new PlateBindingVo();
        vo.setId(binding.getId());
        vo.setVehicleId(vehicle.getId());
        vo.setPlate(vehicle.getVehiclePlate());
        vo.setVehicleType(vehicle.getVehicleType());
        vo.setBrand(vehicle.getBrand());
        vo.setColor(vehicle.getColor());
        vo.setBindingType(binding.getBindingType());
        vo.setVerifyMethod(binding.getVerifyMethod());
        vo.setVerifyStatus(binding.getVerifyStatus());
        vo.setIsDefault(binding.getIsDefault());
        vo.setCreatedAt(binding.getCreatedAt());
        return vo;
    }

    /**
     * 脱敏手机号。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}