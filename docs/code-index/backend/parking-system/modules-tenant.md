# 模块：tenant（租户）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/tenant/`
> **所属**：`parking-system` · `com.jushan.platform.modules.tenant`
> **职责**：租户主表实体与 Mapper。（注：租户的 CRUD/审核在 `com.jushan.system` 中实现；`ParkingLotService`/`EmployeeService` 的租户存在性与启停校验已统一走 `sys_tenant`）
> **最近更新**：2026-07-24

---

## 关键类

| 类 | 路径 | 作用 |
|---|---|---|
| SysTenant | `entity/SysTenant.java` | 租户主表实体（名称/状态：1正常 0禁用） |
| SysTenantMapper | `mapper/SysTenantMapper.java` | 租户主表 Mapper（系统表，不受租户拦截器过滤） |

> **管理入口**：租户注册、审核、启停在 `com.jushan.system.controller.TenantController`，见 `system-controller.md`（⏳）。
