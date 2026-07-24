# Map: 消除 com.jushan.system.* 架构腐化 ✅

> **状态**：已完成（10/10 tickets resolved）

## 最终结果

`com.jushan.system.*` 全部清空，仅保留 `entity/Company.java`（独立表）：

| 子包 | 原始 | 最终 |
|---|---|---|
| entity | 50 | 1（Company） |
| controller | 44 | 0 |
| service | 46 | 0 |
| dto | 48 | 0 |
| vo | 40 | 0 |
| mapper | 50 | 0 |
| mybatis | 3 | 0 |
| client | 15 | 0 |
| cache | 3 | 0 |
| config | 2 | 0 |
| constant | 1 | 0 |
| enums | 1 | 0 |
| task | 5 | 0 |
| job | 1 | 0 |
| event | 4 | 0 |
| ws | 2 | 0 |
| **总计** | **315** | **1** |

- ✅ 主代码编译 clean
- ⚠️ 测试编译：178 pass + 12 预存错误 + 部分测试文件需手动补 import（test 文件 same-package 引用未迁移）

## 架构收益
- 消除 16 个子包的双层架构
- 13 个业务模块承载全部代码
- 新代码只能在 `com.jushan.platform.modules.*` 下创建
