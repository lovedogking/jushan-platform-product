# 模块：parking-framework（框架层）

> **包路径**：`parking-framework/src/main/java/com/jushan/framework/`
> **职责**：Web 层、异常处理、拦截器等横切通用能力。
> **最近更新**：2026-07-21

---

| 子包 | 关键类 | 职责 |
|---|---|---|
| `web/` | `GlobalExceptionHandler` | 全局异常处理器（BusinessException → R.fail） |
| | `TraceIdFilter` / `RequestWrapper` | TraceId 链路追踪 |
| `security/` | `RequirePermission` 注解 / `PermissionAspect` | 权限校验 AOP |
| `config/` | 各种 `@Configuration` | 框架层自动装配 |
| 其它 | — | util/annotation 等 |

> 完整类列表（27 个）见源码 `parking-framework/src/main/java/com/jushan/framework/`。
