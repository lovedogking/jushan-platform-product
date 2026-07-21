# 模块：log（业务日志）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/log/`
> **所属**：`parking-system` · `com.jushan.platform.modules.log`
> **职责**：业务操作日志（`@BusinessLog` 注解）的异步存储。
> **最近更新**：2026-07-21

---

## 关键类

| 类 | 路径 | 作用 |
|---|---|---|
| SysBusinessLog | `entity/SysBusinessLog.java` | 业务操作日志实体（模块/操作/操作人/时间/详情） |
| SysBusinessLogMapper | `mapper/SysBusinessLogMapper.java` | 日志 Mapper |
| SysBusinessLogStorage | `service/SysBusinessLogStorage.java` | 日志存储服务（供 `@BusinessLog` AOP 异步入库） |

> 注：`@BusinessLog` 注解定义在 `parking-infrastructure` 的 `com.jushan.platform.infra.log.BusinessLog`。
