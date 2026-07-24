# 05 — DTO/VO 迁移

**Type:** task
**Status:** resolved
**Blocked by:** none

## 范围

将 `com.jushan.system.dto.*` 和 `com.jushan.system.vo.*` 按功能域迁入对应模块包：

- Device 相关 → `modules/device/dto/`、`modules/device/vo/`
- Parking 相关 → `modules/parking/dto/`、`modules/parking/vo/`
- Booth 相关 → `modules/booth/dto/`、`modules/booth/vo/`
- Account 相关 → `modules/account/dto/`、`modules/account/vo/`
- 等等

## 关键风险

- DTO/VO 被 Controller 和 Service 大量引用
- 迁移后需全局替换 import 路径
- 部分 DTO 可能有 Lombok 注解（`@Data`），跨包搬迁不影响

## 步骤

1. 统计 system/dto/ 和 system/vo/ 下所有文件并归类
2. 按类别复制到对应 modules 子包
3. 全局替换 import
4. 删除原文件
5. 测试

## Comments
