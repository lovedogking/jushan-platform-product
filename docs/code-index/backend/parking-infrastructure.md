# 模块：parking-infrastructure（基础设施）

> **包路径**：`parking-infrastructure/src/main/java/com/jushan/platform/infra/`
> **职责**：安全、日志、MyBatis 插件、Web 配置等底层基础设施。
> **最近更新**：2026-07-24（mybatis 子包新增 TenantMetaObjectHandler；TenantIgnore 从 system/mybatis 迁入）

---

| 子包 | 关键类 | 职责 |
|---|---|---|
| `security/` | `RequirePermission` / `SysPermissionProvider` / `SecurityConfig` | 权限注解 + 提供者 + Spring Security 配置 |
| `log/` | `BusinessLog`（注解 + AOP） | 业务日志自动记录（异步入库到 `SysBusinessLog`） |
| `mybatis/` | `JushanTenantLineHandler` / `JushanTenantLineInnerInterceptor` / `TenantMetaObjectHandler` | 多租户行级隔离拦截器 + 字段自动填充（tenantId/createdAt/updatedAt） |
| `web/` | Web 配置 | CORS / 过滤器等 |
| `config/` | 基础设施配置 | |

> 完整类列表（19 个）见源码 `parking-infrastructure/src/main/java/com/jushan/platform/infra/`。
