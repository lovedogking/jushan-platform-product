# 08 — Mapper 层迁移（50 个）

**Type:** task
**Status:** resolved
**Blocked by:** none

## 范围

将 `com.jushan.system.mapper.*` 下 50 个 MyBatis Mapper 接口迁至对应模块 `mapper/` 子包。

每个 Mapper 与已迁移的 Entity 一一对应，目标模块参照 Entity 映射：

| Mapper 数量 | 目标模块 |
|---|---|
| 16 | `modules/parking/mapper/` |
| 7 | `modules/vehicle/mapper/` |
| 5 | `modules/account/mapper/` |
| 5 | `modules/device/mapper/` |
| 3 | `modules/log/mapper/` |
| 3 | `modules/miniapp/mapper/` |
| 2 | `modules/tenant/mapper/` |
| 2 | `modules/common/mapper/` |
| 1 | `modules/booth/mapper/` |
| 1 | 保留（CompanyMapper → company 模块） |

## 关键风险

- 50 个 mapper 的 `BaseMapper<Entity>` 类型参数引用已迁移 Entity，import 需一次到位
- Mapper XML 文件（`resources/mapper/`）引用 `namespace`，不受 Java 包迁移影响
- 全局 import 替换会命中 ~200+ 处引用（Service、Controller、Test）
- `CompanyMapper` 对应 `Company` Entity（system/entity 唯一保留），迁至 `modules/company/mapper/`

## 步骤

1. 按 Entity 映射确定每个 Mapper 的目标模块
2. 批量 copy + 改 package + 全局替换 import
3. 删除 system/mapper 原文件
4. 编译 + 测试

## Comments
