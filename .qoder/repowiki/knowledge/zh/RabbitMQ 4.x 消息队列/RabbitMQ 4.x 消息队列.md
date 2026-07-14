---
kind: external_dependency
name: RabbitMQ 4.x 消息队列
slug: rabbitmq
category: external_dependency
category_hints:
    - framework_behavior
    - client_constraint
scope:
    - '**'
---

### RabbitMQ 内部消息中间件
- 平台内部异步事件总线，采用 TopicExchange + DirectExchange 混合模式
- 死信队列（DLX）机制：消费失败消息转入死信队列，保留原始 messageId 支持人工重放
- 识别事件专用队列 `recognitionEventQueue`，绑定到 `platformInternalExchange`
- P0安全红线：不声明 Device Access 的任何 Exchange/Queue/RoutingKey，不配置 MQTT 桥接
- 优雅降级：ConnectionFactory 不可用时 RabbitTemplate 和监听容器降级为空操作对象
- 本地开发通过 Docker Compose 提供，Management 控制台端口 15672