package com.jushan.platform.modules.miniapp.service;

import com.jushan.platform.modules.miniapp.entity.MiniMessage;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小程序消息通知服务（Phase 3 E3）。
 * <p>
 * 负责消息的创建、查询、已读标记，以及微信订阅消息推送。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface MiniMessageService {

    /**
     * 创建支付成功消息并推送订阅通知。
     *
     * @param wxUserId    微信用户ID
     * @param tenantId    租户ID
     * @param orderId     订单ID
     * @param plateNumber 车牌号
     * @param amountCents 支付金额（分）
     * @return 创建的消息记录
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createPaySuccessMessage(Long wxUserId, Long tenantId, Long orderId,
                                        String plateNumber, Integer amountCents);

    /**
     * 创建欠费放行通知消息。
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createArrearsReleasedMessage(Long wxUserId, Long tenantId, Long orderId,
                                              String plateNumber, Integer amountCents);

    /**
     * 创建欠费提醒通知消息。
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createArrearsRemindMessage(Long wxUserId, Long tenantId, String plateNumber,
                                            Integer amountCents);

    /**
     * 创建欠费补缴成功通知消息。
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createArrearsPaidMessage(Long wxUserId, Long tenantId, Long orderId,
                                          String plateNumber, Integer amountCents);
}
