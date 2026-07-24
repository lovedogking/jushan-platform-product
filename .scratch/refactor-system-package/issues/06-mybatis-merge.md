# 06 — MyBatis 租户配置合并

**Type:** task
**Status:** resolved
**Blocked by:** none

## 范围

将 `com.jushan.system.mybatis.*` 下的租户相关配置合并到框架层：

- `TenantIgnore` 注解 → `parking-common` (`com.jushan.common.mybatis`)
- `TenantMetaObjectHandler` → `parking-infrastructure` (`com.jushan.platform.infra.mybatis`)
- `TenantIgnoreAspect` → `parking-system` (`com.jushan.platform.modules.common.aspect`)

## 关键风险

- `TenantIgnore` 注解被 ~30 个 Service/Mapper 引用
- 合并后需全局替换 import
- 确认 `JushanTenantLineHandler` 等已存在的 infra 类是否冲突

## Comments
