package com.jushan.framework.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 平台内部基础配置。
 * <p>
 * 仅声明平台内部 Exchange、DLX、死信队列和消息转换器。
 * 各业务模块的 Queue 和 Binding 由模块自身声明，不在此处集中管理。
 * <p>
 * <strong>死信策略</strong>：
 * <ul>
 *   <li>消费失败（抛出异常）的消息转入 DLX</li>
 *   <li>DLX 队列保留原始 messageId，支持人工重放</li>
 *   <li>TTL 和 max-length 由业务模块声明 Queue 时自主配置</li>
 * </ul>
 * <p>
 * <strong>P0 安全红线</strong>：
 * <ul>
 *   <li>不声明 Device Access 的任何 Exchange / Queue / RoutingKey</li>
 *   <li>不配置与 Device Access 的 MQTT 桥接</li>
 *   <li>不假设车牌识别事件的能力</li>
 * </ul>
 * <p>
 * RabbitMQ 不可用时（{@code ConnectionFactory} 不存在），Exchange/Queue/Binding
 * 元数据 Bean 仍正常创建，{@code RabbitTemplate} 和 {@code rabbitListenerContainerFactory}
 * 降级为空操作对象，不影响非 MQ 场景的应用启动。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@EnableRabbit
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(name = "jushan.mq.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    // ==================== Exchange ====================

    @Bean
    public TopicExchange platformInternalExchange() {
        return new TopicExchange(MqConstants.EXCHANGE_INTERNAL, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_DLX, true, false);
    }

    // ==================== DLX Queue ====================

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(MqConstants.QUEUE_DLX, true);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(MqConstants.ROUTING_KEY_DLX);
    }

    // ==================== 识别事件队列（T28 引入，T29 添加消费者） ====================

    @Bean
    public Queue recognitionEventQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_RECOGNITION_EVENT)
                .withArguments(deadLetterArgs())
                .build();
    }

    @Bean
    public Binding recognitionEventBinding() {
        return BindingBuilder.bind(recognitionEventQueue())
                .to(platformInternalExchange())
                .with(MqConstants.ROUTING_KEY_RECOGNITION_EVENT);
    }

    // ==================== 消息序列化 ====================

    @Bean
    public MessageConverter jsonMessageConverter() {
        // 信任所有包（*）：平台内部消息均由可信服务投递
        return new Jackson2JsonMessageConverter("*");
    }

    // ==================== RabbitTemplate ====================

    /**
     * RabbitTemplate — 使用 ObjectProvider 注入 ConnectionFactory。
     * 当 RabbitMQ 未配置时优雅降级，不阻塞应用启动。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ObjectProvider<ConnectionFactory> cfProvider,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate();
        ConnectionFactory cf = cfProvider.getIfAvailable();
        if (cf != null) {
            template.setConnectionFactory(cf);
        } else {
            log.warn("ConnectionFactory 不可用，RabbitTemplate 已降级（非 MQ 场景）");
        }
        template.setMessageConverter(messageConverter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                log.warn("消息发送未确认: id={} cause={}", correlationData.getId(), cause);
            }
        });
        template.setReturnsCallback(returned -> {
            log.warn("消息无法路由: exchange={} routingKey={} replyCode={} replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });
        return template;
    }

    // ==================== Listener Container Factory ====================

    /**
     * 默认 JSON 消费监听器工厂。
     * 当 ConnectionFactory 不可用时降级（不会创建消费者容器）。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ObjectProvider<ConnectionFactory> cfProvider,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        ConnectionFactory cf = cfProvider.getIfAvailable();
        if (cf != null) {
            factory.setConnectionFactory(cf);
        }
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }

    // ==================== 工具方法 ====================

    /**
     * 构建业务队列所需的死信参数 Map。
     */
    public static Map<String, Object> deadLetterArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", MqConstants.EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", MqConstants.ROUTING_KEY_DLX);
        return args;
    }
}
