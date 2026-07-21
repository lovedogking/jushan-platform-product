package com.smartparking.deviceaccess.mqtt;

import com.smartparking.deviceaccess.common.dto.mqtt.MqttMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 网关 —— MQTT 模块对外暴露的唯一接口。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理与 EMQX Broker 的连接和自动重连</li>
 *   <li>订阅 Topic，将收到的原始 JSON 反序列化为 {@link MqttMessage} 后回调给 Listener</li>
 *   <li>发布消息到指定 Topic</li>
 *   <li>支持请求-响应模式：发布后等待设备回复</li>
 * </ul>
 * <p>
 * 不解析业务数据 —— 所有 payload 以原始形式透传给 adapter 模块。
 */
public interface MqttGateway {

    /**
     * 连接到 MQTT Broker，并订阅默认 Topic（所有设备上行消息、所有设备回复消息）。
     */
    void connect();

    /**
     * 断开 MQTT 连接。
     */
    void disconnect();

    /**
     * 当前连接是否已建立。
     */
    boolean isConnected();

    /**
     * 注册全局消息监听器。
     * <p>
     * 所有从 Broker 收到的消息都会回调此监听器（不区分 Topic）。
     * adapter 模块应在启动时注册，由 adapter 内部根据 message.name 做路由。
     *
     * @param listener 消息监听器
     */
    void registerListener(MqttMessageListener listener);

    /**
     * 注册原始 Map 消息监听器。
     * <p>
     * 用于不使用臻识 MqttMessage 信封格式的品牌（如信路通）。
     * 所有从 Broker 收到的消息会以 Map 形式回调此监听器。
     *
     * @param listener 原始消息监听器
     */
    void addRawListener(MqttRawMessageListener listener);

    /**
     * 发布消息到指定 Topic。
     *
     * @param topic   目标 Topic
     * @param message 消息内容
     */
    void publish(String topic, MqttMessage message);

    /**
     * 发布消息并等待设备回复。
     * <p>
     * 根据臻识协议，设备回复 Topic 固定为 {requestTopic}/reply，
     * 且回复消息的 id 字段与请求消息一致，通过 id 做请求-响应关联。
     *
     * @param topic    请求 Topic
     * @param message  请求消息
     * @param timeout  超时时间
     * @param unit     时间单位
     * @return 设备回复消息的 Future
     */
    CompletableFuture<MqttMessage> publishAndWait(String topic, MqttMessage message, long timeout, TimeUnit unit);

    /**
     * 发布原始 JSON 字符串到指定 Topic。
     * <p>
     * 用于不使用臻识 MqttMessage 信封格式的品牌（如信路通）。
     * 不做任何序列化或包装，直接发送原始 JSON。
     *
     * @param topic 目标 Topic
     * @param json  原始 JSON 字符串
     */
    void publishRaw(String topic, String json);

    /**
     * 动态订阅一个 Topic。
     * <p>
     * 用于主题不固定、由设备注册时上报的品牌（如芊熠）。
     * 订阅会被记录，连接断开后自动重连时会一并重新订阅。
     * 幂等：重复订阅同一 Topic 不会产生影响。
     *
     * @param topic 目标 Topic（可为具体 Topic 或通配符）
     */
    void subscribe(String topic);
}
