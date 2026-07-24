# 04 — 杂项 Service 迁移（Vehicle/Account/Tenant/Company/Common/Device）

**Type:** task
**Status:** resolved
**Blocked by:** 01

## 范围

将剩余 13 个 Service 按域迁入对应模块：

| Service | 目标模块 |
|---|---|
| `VehicleListService` | vehicle |
| `WhitelistSyncService` | vehicle |
| `EmployeeService` | account |
| `PermissionService` | account |
| `TenantService` | tenant |
| `CompanyService` | company |
| `ArchiveService` | common |
| `AuditService` | common |
| `SystemParamService` | common |
| `ParamResolver` | common |
| `DeviceService` | device |
| `MonitorAlertService` | device |

## 关键风险

- `DeviceService` 是最大的 Service 文件之一，引用大量 Entity 和 Mapper
- `TenantService` 引用了已迁移的 `Tenant`/`TenantAuditLog` Entity
- `CompanyService` 引用保留在 system/entity 的 `Company` Entity

## Comments
