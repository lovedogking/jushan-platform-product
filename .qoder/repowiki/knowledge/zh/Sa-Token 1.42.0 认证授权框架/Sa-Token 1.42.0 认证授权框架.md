---
kind: external_dependency
name: Sa-Token 1.42.0 认证授权框架
slug: sa-token
category: external_dependency
category_hints:
    - auth_protocol
    - client_constraint
scope:
    - '**'
---

### Sa-Token 认证鉴权
- 使用 `Authorization` Header + `Bearer` 前缀传递 Token（P0安全：仅从Header读取，关闭Cookie/Body来源）
- 会话存储默认 Redis（`sa-token-redis-jackson`），Redis不可用时自动回退内存存储
- 全局拦截器模式（非Filter），异常可被 `@RestControllerAdvice` 统一处理
- 支持多端登录、并发登录、共享Session等特性
- 公开路径白名单：`/actuator/**`、`/auth/login`、`/wx/login`、`/register/**`、`/demo/**`、`/api/v1/internal/mock/**`