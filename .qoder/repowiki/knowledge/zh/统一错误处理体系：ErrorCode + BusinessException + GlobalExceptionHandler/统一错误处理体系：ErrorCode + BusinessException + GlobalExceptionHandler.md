---
kind: error_handling
name: 统一错误处理体系：ErrorCode + BusinessException + GlobalExceptionHandler
category: error_handling
scope:
    - '**'
source_files:
    - parking-common/src/main/java/com/jushan/common/ErrorCode.java
    - parking-common/src/main/java/com/jushan/common/CommonErrorCode.java
    - parking-common/src/main/java/com/jushan/common/BusinessException.java
    - parking-common/src/main/java/com/jushan/common/R.java
    - parking-framework/src/main/java/com/jushan/framework/web/GlobalExceptionHandler.java
    - admin-web/src/utils/request.ts
    - booth-web/src/utils/request.ts
---

## 错误处理架构概览

该智慧停车 SaaS 平台采用分层统一的错误处理体系，通过 parking-common 模块定义错误码规范、parking-framework 模块提供全局异常处理器、业务层使用 BusinessException 抛出结构化错误，前端通过 Axios 拦截器统一处理响应。

## 核心组件与约定

### 1. 错误码体系（parking-common）
- ErrorCode 接口：所有业务错误码枚举必须实现此接口，确保统一的 getCode() 和 getMessage() 结构
- CommonErrorCode 枚举：定义跨模块通用错误码，包括 SUCCESS(0)、PARAM_ERROR(400)、UNAUTHORIZED(401)、FORBIDDEN(403)、NOT_FOUND(404)、METHOD_NOT_ALLOWED(405)、BUSINESS_ERROR(1000)、CONFLICT(1001)、UNSUPPORTED_OPERATION(1002)、INTERNAL_ERROR(9999)
- BusinessException 异常类：业务层抛出的标准异常，携带 ErrorCode 和可选的格式化参数

### 2. 全局异常处理器（parking-framework）
GlobalExceptionHandler 使用 @RestControllerAdvice 集中处理四类异常：
- 参数校验失败：MethodArgumentNotValidException、ConstraintViolationException → PARAM_ERROR + 字段级错误详情
- 业务异常：BusinessException → 透传 errorCode，根据协议语义映射 HTTP 状态码
- 认证授权异常：Sa-Token 的 NotLoginException、NotPermissionException、NotRoleException → 对应 UNAUTHORIZED/FORBIDDEN
- Spring 内置异常：404/405 等 → NOT_FOUND/METHOD_NOT_ALLOWED
- 兜底异常：Exception → INTERNAL_ERROR（不泄露堆栈）

### 3. HTTP 状态码映射策略
采用协议级错误码映射机制：只有明确的协议语义错误码（400/401/403/404/409/429）才映射为对应 HTTP 状态码，其余普通业务错误码保持 HTTP 200 + code 非 0。

### 4. 统一响应体（R<T>）
所有 Controller 返回 R<T> 类型，包含：
- code：业务状态码（0 表示成功）
- message：用户友好的提示信息
- data：响应数据
- traceId：请求追踪 ID（始终序列化）
- errors：字段校验错误详情（仅校验失败时序列化）

### 5. 前端错误处理
两个前端应用（admin-web、booth-web）均实现统一的 Axios 拦截器：
- 请求拦截器：自动添加 Authorization 头（登录请求除外）
- 响应拦截器：统一处理业务错误码、HTTP 状态码、401 自动跳转登录
- ApiError 类：封装错误信息，保留 code、status、traceId 供上层使用
- 防重复跳转：防止并发 401 导致的重复登录跳转

## 设计决策与约束

### P0 红线原则
- 禁止将堆栈 trace 或内部错误详情返回前端
- 所有异常在日志中记录完整堆栈，但响应体只包含用户友好信息

### 错误码管理策略
- 通用错误码集中在 CommonErrorCode，业务特定错误码应在各自模块定义独立枚举
- 错误码按语义分组：4xx 客户端错误、1xxx 业务错误、9999 系统错误

### 异常传播模式
- 业务层通过抛出 BusinessException 表达可预期的业务异常
- 框架层通过 GlobalExceptionHandler 统一转换为 R 响应
- 避免在业务层捕获并吞掉异常，让异常自然向上冒泡

### 链路追踪集成
- 所有错误响应自动附带 traceId，便于问题定位
- 全局异常处理器从 MDC 中获取 TraceIdFilter 设置的追踪 ID

## 开发者规范

1. 优先使用预定义错误码：尽量使用 CommonErrorCode 或自定义 ErrorCode 枚举，避免硬编码错误码
2. 业务异常标准化：业务层遇到可预期异常时抛出 BusinessException，而非返回特殊值
3. 不要手动构造 R 对象：Controller 中直接抛出异常，由全局处理器统一转换
4. 前端错误处理：利用 Axios 拦截器的统一处理，业务代码只需处理 ApiError
5. 日志级别规范：业务异常用 warn 级别，系统异常用 error 级别，参数错误用 warn 级别