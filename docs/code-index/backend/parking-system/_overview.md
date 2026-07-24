# parking-system 包结构（重构后）

> **最近更新**：2026-07-24（全量迁移完成：Entity/Controller/Service/DTO/VO/MyBatis）

`parking-system` 内含两套包，但重构后仅 `modules.*` 活跃：

| 包 | 定位 | 状态 |
|---|---|---|
| `com.jushan.platform.modules.*` | **主力业务模块** | ✅ 100%（Entity/Controller/Service/DTO/VO 全部在此） |
| `com.jushan.system.*` | **已清空** | ⚠️ 仅剩 `entity/Company.java`（独立表，与 `modules.company.entity.SysCompany` 不同表） |

**迁移完成统计（2026-07-24）**：

| 层 | 原始数量 | 迁移后剩余 |
|---|---|---|
| Entity（`system/entity`） | 50 | 1（Company） |
| Controller（`system/controller`） | 44 | 0 |
| Service（`system/service`） | 46 | 0 |
| DTO（`system/dto`） | 48 | 0 |
| VO（`system/vo`） | 40 | 0 |
| MyBatis（`system/mybatis`） | 3 | 0 |

**13 个业务模块**：
`account` `auth` `booth` `common` `company` `department` `device` `log` `miniapp` `parking` `tenant` `vehicle` + 跨切面 `common/aspect`

**索引方式**：
每个模块一个独立索引文件（如 `modules-parking.md`）。
