package com.jushan.platform.modules.miniapp.service.impl;

import com.jushan.platform.modules.miniapp.entity.MiniMessage;
import com.jushan.platform.modules.miniapp.mapper.MiniMessageMapper;
import com.jushan.platform.modules.miniapp.service.MiniMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 小程序消息通知服务实现（Phase 3 E3）。
 * <p>
 * 当前版本实现消息存储（mini_message 表）。
 * 微信订阅消息推送暂为 Mock 实现，记录日志代替真实 API 调用。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class MiniMessageServiceImpl implements MiniMessageService {

    private static final Logger log = LoggerFactory.getLogger(MiniMessageServiceImpl.class);

    private final MiniMessageMapper miniMessageMapper;

    public MiniMessageServiceImpl(MiniMessageMapper miniMessageMapper) {
        this.miniMessageMapper = miniMessageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createPaySuccessMessage(Long wxUserId, Long tenantId, Long orderId,
                                                String plateNumber, Integer amountCents) {
        // 1. 创建消息记录
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_PAY_SUCCESS);
        message.setIsRead(false);
        message.setRelatedOrderId(orderId);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = String.format("%.2f", (amountCents != null ? amountCents : 0) / 100.0);
        message.setTitle("支付成功通知");
        message.setContent("您的爱车 " + plateNumber + " 已完成支付 ¥" + amountYuan + "，请及时离场。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());

        miniMessageMapper.insert(message);
        log.info("支付成功消息已创建: wxUserId={} orderId={} plate={} amount={}",
                wxUserId, orderId, plateNumber, amountCents);

        // 2. 推送微信订阅消息（Mock 实现）
        sendSubscribeMessage(wxUserId, plateNumber, amountYuan);

        return message;
    }

    /**
     * 推送微信订阅消息（Mock 实现）。
     * <p>
     * 真实场景调用微信 {@code POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send}。
     * 当前记录日志代替，后续接入真实微信 API 时需提供 AppId/Secret。
     *
     * @param wxUserId   微信用户ID
     * @param plate      车牌号
     * @param amountYuan 金额（元字符串）
     */
    private void sendSubscribeMessage(Long wxUserId, String plate, String amountYuan) {
        log.info("[Mock] 微信订阅消息推送: wxUserId={} templateId=PAY_SUCCESS_NOTICE " +
                        "plate={} amount=¥{} message=支付成功，请及时离场",
                wxUserId, plate, amountYuan);

        // 真实实现：
        // 1. 从 wx_user 表查询 openid
        // 2. 调用微信 API:
        //    POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=TOKEN
        //    {
        //        "touser": "OPENID",
        //        "template_id": "TEMPLATE_ID",
        //        "data": {
        //            "thing1": { "value": plate },
        //            "amount2": { "value": amountYuan },
        //            "thing3": { "value": "支付成功" }
        //        }
        //    }
        // 3. 错误码 43101（用户拒收）或 41030（模板不存在）仅记录日志，不抛异常
    }
}
