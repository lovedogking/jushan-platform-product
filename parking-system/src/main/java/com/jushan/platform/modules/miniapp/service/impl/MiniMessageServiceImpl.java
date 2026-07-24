package com.jushan.platform.modules.miniapp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.platform.modules.miniapp.entity.MiniMessage;
import com.jushan.platform.modules.miniapp.mapper.MiniMessageMapper;
import com.jushan.platform.modules.miniapp.service.MiniMessageService;
import com.jushan.platform.modules.miniapp.client.WeChatApiClient;
import com.jushan.platform.modules.miniapp.config.WxMiniappProperties;
import com.jushan.platform.modules.vehicle.entity.PlateBinding;
import com.jushan.platform.modules.vehicle.entity.Vehicle;
import com.jushan.platform.modules.miniapp.entity.WxUser;
import com.jushan.platform.modules.vehicle.mapper.PlateBindingMapper;
import com.jushan.platform.modules.vehicle.mapper.VehicleMapper;
import com.jushan.platform.modules.miniapp.mapper.WxUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序消息通知服务实现（Phase 3 E3）。
 * <p>
 * 实现消息存储（mini_message 表）和微信订阅消息推送。
 * 微信订阅消息通过 {@link WeChatApiClient} 真实发送。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class MiniMessageServiceImpl implements MiniMessageService {

    private static final Logger log = LoggerFactory.getLogger(MiniMessageServiceImpl.class);

    private final MiniMessageMapper miniMessageMapper;
    private final WeChatApiClient weChatApiClient;
    private final WxMiniappProperties wxMiniappProperties;
    private final WxUserMapper wxUserMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final VehicleMapper vehicleMapper;

    public MiniMessageServiceImpl(MiniMessageMapper miniMessageMapper,
                                   WeChatApiClient weChatApiClient,
                                   WxMiniappProperties wxMiniappProperties,
                                   WxUserMapper wxUserMapper,
                                   PlateBindingMapper plateBindingMapper,
                                   VehicleMapper vehicleMapper) {
        this.miniMessageMapper = miniMessageMapper;
        this.weChatApiClient = weChatApiClient;
        this.wxMiniappProperties = wxMiniappProperties;
        this.wxUserMapper = wxUserMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createPaySuccessMessage(Long wxUserId, Long tenantId, Long orderId,
                                                String plateNumber, Integer amountCents) {
        // 1. 创建消息记录
        MiniMessage message = buildPaySuccessMessage(wxUserId, tenantId, orderId, plateNumber, amountCents);
        miniMessageMapper.insert(message);
        log.info("支付成功消息已创建: wxUserId={} orderId={} plate={} amount={}",
                wxUserId, orderId, plateNumber, amountCents);

        // 2. 推送微信订阅消息
        String amountYuan = formatYuan(amountCents);
        pushSubscribeMessage(wxUserId, plateNumber, amountYuan);

        return message;
    }

    /**
     * 创建代缴支付成功消息，同时推送给代缴人和车主双方。
     *
     * @param payerUserId  代缴人用户ID
     * @param ownerUserId  车主用户ID（可为null）
     * @param tenantId     租户ID
     * @param orderId      订单ID
     * @param plateNumber  车牌号
     * @param amountCents  支付金额（分）
     */
    @Transactional(rollbackFor = Exception.class)
    public void createProxyPaySuccessMessages(Long payerUserId, Long ownerUserId, Long tenantId,
                                               Long orderId, String plateNumber, Integer amountCents) {
        // 1. 给代缴人创建消息并推送
        MiniMessage payerMsg = buildPaySuccessMessage(payerUserId, tenantId, orderId, plateNumber, amountCents);
        payerMsg.setTitle("代缴成功通知");
        String amountYuan = formatYuan(amountCents);
        payerMsg.setContent("您已成功为 " + plateNumber + " 代缴停车费 ¥" + amountYuan + "。");
        miniMessageMapper.insert(payerMsg);

        // 2. 给车主创建消息并推送（如果车主存在且与代缴人不同）
        if (ownerUserId != null && !ownerUserId.equals(payerUserId)) {
            MiniMessage ownerMsg = buildPaySuccessMessage(ownerUserId, tenantId, orderId, plateNumber, amountCents);
            ownerMsg.setTitle("代缴到账通知");
            ownerMsg.setContent("您的爱车 " + plateNumber + " 停车费已由他人代缴完成（¥" + amountYuan + "），请及时离场。");
            miniMessageMapper.insert(ownerMsg);

            // 推送到车主
            pushSubscribeMessage(ownerUserId, plateNumber, amountYuan);
        }

        // 推送到代缴人
        pushSubscribeMessage(payerUserId, plateNumber, amountYuan);

        log.info("代缴消息已创建并推送: payerId={} ownerId={} orderId={} plate={}",
                payerUserId, ownerUserId, orderId, plateNumber);
    }

    /**
     * 查找车牌的所有者 wxUserId（通过 plate_binding）。
     */
    public Long findOwnerByPlate(String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return null;
        }
        String normalized = plateNumber.toUpperCase().replaceAll("\\s+", "");
        Vehicle vehicle = vehicleMapper.selectOne(
                new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getVehiclePlate, normalized));
        if (vehicle == null) {
            return null;
        }
        List<PlateBinding> bindings = plateBindingMapper.selectList(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getVehicleId, vehicle.getId())
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED)
                        .eq(PlateBinding::getBindingType, PlateBinding.BINDING_TYPE_OWNER)
                        .orderByDesc(PlateBinding::getCreatedAt)
                        .last("LIMIT 1"));
        if (bindings.isEmpty()) {
            return null;
        }
        return bindings.get(0).getWxUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createArrearsReleasedMessage(Long wxUserId, Long tenantId, Long orderId,
                                                     String plateNumber, Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_ARREARS_RELEASED);
        message.setIsRead(false);
        message.setRelatedOrderId(orderId);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = formatYuan(amountCents);
        message.setTitle("欠费记录通知");
        message.setContent("您的停车费用 ¥" + amountYuan + " 已转为欠费记录，请在方便时补缴。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.insert(message);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createArrearsRemindMessage(Long wxUserId, Long tenantId, String plateNumber,
                                                   Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_ARREARS_REMIND);
        message.setIsRead(false);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = formatYuan(amountCents);
        message.setTitle("欠费提醒");
        message.setContent("您有欠费订单未支付（¥" + amountYuan + "），请及时补缴以免影响后续通行。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.insert(message);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createArrearsPaidMessage(Long wxUserId, Long tenantId, Long orderId,
                                                 String plateNumber, Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_ARREARS_PAID);
        message.setIsRead(false);
        message.setRelatedOrderId(orderId);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = formatYuan(amountCents);
        message.setTitle("欠费补缴成功");
        message.setContent("您的欠费 ¥" + amountYuan + " 已补缴成功，感谢您的配合。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.insert(message);
        return message;
    }

    // ==================== 私有方法 ====================

    /**
     * 构建支付成功消息实体（不插入数据库）。
     */
    private MiniMessage buildPaySuccessMessage(Long wxUserId, Long tenantId, Long orderId,
                                                String plateNumber, Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_PAY_SUCCESS);
        message.setIsRead(false);
        message.setRelatedOrderId(orderId);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = formatYuan(amountCents);
        message.setTitle("支付成功通知");
        message.setContent("您的爱车 " + plateNumber + " 已完成支付 ¥" + amountYuan + "，请及时离场。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        return message;
    }

    /**
     * 推送微信订阅消息（通过真实微信 API）。
     *
     * @param wxUserId   微信用户ID
     * @param plate      车牌号
     * @param amountYuan 金额（元字符串）
     */
    private void pushSubscribeMessage(Long wxUserId, String plate, String amountYuan) {
        String templateId = wxMiniappProperties.getSubscribeTemplateId();
        if (templateId == null || templateId.isBlank()) {
            log.info("[Skip] 未配置订阅消息模板ID，跳过推送: wxUserId={} plate={}", wxUserId, plate);
            return;
        }

        // 查询用户 openid
        WxUser wxUser = wxUserMapper.selectById(wxUserId);
        if (wxUser == null || wxUser.getOpenid() == null || wxUser.getOpenid().isBlank()) {
            log.warn("用户 openid 缺失，跳过推送: wxUserId={}", wxUserId);
            return;
        }

        // 构建模板数据
        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("thing1", Map.of("value", plate != null ? plate : ""));
        templateData.put("amount2", Map.of("value", amountYuan != null ? amountYuan : "0.00"));
        templateData.put("thing3", Map.of("value", "支付成功"));

        // 通过微信 API 发送
        boolean sent = weChatApiClient.sendSubscribeMessage(
                wxUser.getOpenid(), templateId, templateData);

        if (sent) {
            log.info("订阅消息推送成功: wxUserId={} plate={}", wxUserId, plate);
        } else {
            log.warn("订阅消息推送失败（已降级到站内消息）: wxUserId={} plate={}", wxUserId, plate);
        }
    }

    private String formatYuan(Integer amountCents) {
        if (amountCents == null) {
            return "0.00";
        }
        return String.format("%.2f", amountCents / 100.0);
    }
}
