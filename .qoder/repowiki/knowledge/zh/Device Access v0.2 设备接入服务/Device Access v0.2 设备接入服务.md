---
kind: external_dependency
name: Device Access v0.2 设备接入服务
slug: device-access
category: external_dependency
category_hints:
    - sdk_real_api
    - client_constraint
scope:
    - '**'
---

### Device Access 独立设备管理服务
- 外部独立服务，负责设备通信、协议适配和事件转换
- 当前能力：7个HTTP端点（设备CRUD、时间同步、状态查询）
- 当前缺失：开闸控制、车牌识别事件、RabbitMQ集成、HMAC签名、commandId幂等
- 通过共享契约协作：OpenAPI/AsyncAPI/JSON Schema 定义接口规范
- 联合评审决策状态：B01-B08已ACCEPTED，V01-V04待真机验证
- 客户端配置：`jushan.device-access.base-url` 指向 http://localhost:8081