# 索引：com.jushan.system Entity + Mapper（已清空）

> **包路径**：`parking-system/src/main/java/com/jushan/system/entity/` `mapper/`
> **最近更新**：2026-07-24（全量迁移至 modules，仅剩 Company）

**状态**：所有实体已迁移至 `com.jushan.platform.modules.*.entity`，旧包仅保留：

| 类名 | 原因 |
|---|---|
| `Company` | 对应表 `company`（与 `modules.company.entity.SysCompany` 的 `sys_company` 不同表），暂时保留 |
| `package-info.java` | 包描述 |

**已迁移映射见各模块索引文件**（`modules-parking.md`、`modules-device.md` 等）。
