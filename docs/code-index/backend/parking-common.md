# 模块：parking-common（通用基础类型）

> **包路径**：`parking-common/src/main/java/com/jushan/common/`
> **依赖**：无 Spring 容器依赖，纯 Java 类库。
> **最近更新**：2026-07-21

---

| 类 | 路径 | 职责 |
|---|---|---|
| `R<T>` | `R.java` | 统一响应体（code/message/data/traceId/errors）；静态工厂 `R.ok()` / `R.fail()` |
| `ErrorCode` | `ErrorCode.java` | 错误码接口（int getCode() / String getMessage()） |
| `CommonErrorCode` | `CommonErrorCode.java` | 通用错误码枚举（SUCCESS=0 / PARAM_ERROR / BUSINESS_ERROR / NOT_FOUND / FORBIDDEN 等） |
| `BusinessException` | `BusinessException.java` | 业务异常（RuntimeException 子类，携带 ErrorCode） |
| `BaseEntity` | `BaseEntity.java` | 实体基类（id/tenantId/createdAt/updatedAt/deletedAt 软删除） |
| `TenantContext` | `auth/TenantContext.java` | 租户上下文 ThreadLocal（requireTenantId / isPlatformUser） |
| `TenantIgnoreHolder` | `mybatis/TenantIgnoreHolder.java` | MyBatis TenantIgnore 标记持有者 |
| `package-info` | `package-info.java` | 包说明 |

> 本项目所有 Controller 统一返回 `R<T>`，Service 层抛 `BusinessException`，全局异常处理器将其转为 `R.fail`。
