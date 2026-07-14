package com.jushan.framework.mq;

/**
 * 平台内部 RabbitMQ 常量。
 * <p>
 * <strong>P0 红线</strong>：本类中的所有 Exchange、Queue、RoutingKey
 * 仅用于平台内部业务。不得：
 * <ul>
 *   <li>创建或声称使用 Device Access 的 Exchange、Routing Key 或车牌事件</li>
 *   <li>将平台内部常量标注为 Device Access 契约</li>
 * </ul>
 * <p>
 * <strong>命名规范</strong>：
 * <ul>
 *   <li>Exchange: {@code jushan.platform.internal}（topic 类型）</li>
 *   <li>DLX: {@code jushan.platform.dlx}</li>
 *   <li>Queue: {@code jushan.{module}.{purpose}}</li>
 *   <li>RoutingKey: {@code jushan.{module}.{event}}</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class MqConstants {

    private MqConstants() {}

    // ==================== Exchange ====================

    /** 平台内部 Topic Exchange */
    public static final String EXCHANGE_INTERNAL = "jushan.platform.internal";

    /** 死信 Exchange */
    public static final String EXCHANGE_DLX = "jushan.platform.dlx";

    // ==================== 死信 ====================

    /** 死信队列 */
    public static final String QUEUE_DLX = "jushan.platform.dlx.queue";

    /** 死信 RoutingKey */
    public static final String ROUTING_KEY_DLX = "jushan.platform.dlx";

    // ==================== 模块前缀（供子模块拼接使用） ====================

    /** 设备模块 */
    public static final String MODULE_DEVICE = "device";

    /** 停车记录模块 */
    public static final String MODULE_RECORD = "record";

    /** 订单模块 */
    public static final String MODULE_ORDER = "order";

    /** 支付模块 */
    public static final String MODULE_PAYMENT = "payment";

    /** 通知模块 */
    public static final String MODULE_NOTICE = "notice";

    /** 租户/账号模块 */
    public static final String MODULE_TENANT = "tenant";

    // ==================== 识别事件（T28） ====================

    /** 识别事件 Queue */
    public static final String QUEUE_RECOGNITION_EVENT = "jushan.record.recognition.event";

    /** 识别事件 RoutingKey */
    public static final String ROUTING_KEY_RECOGNITION_EVENT = "jushan.record.recognition.event";

    /** 识别事件消息类型 */
    public static final String TYPE_RECOGNITION_EVENT = "record.recognition.event";
}
