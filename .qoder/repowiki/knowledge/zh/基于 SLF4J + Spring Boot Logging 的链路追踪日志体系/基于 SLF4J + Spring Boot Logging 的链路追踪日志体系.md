---
kind: logging_system
name: 基于 SLF4J + Spring Boot Logging 的链路追踪日志体系
category: logging_system
scope:
    - '**'
source_files:
    - parking-boot/src/main/resources/application.yml
    - parking-boot/src/main/resources/application-prod.yml
    - parking-boot/src/main/resources/application-docker.yml
    - parking-framework/src/main/java/com/jushan/framework/web/TraceIdFilter.java
    - parking-framework/src/main/java/com/jushan/framework/web/GlobalExceptionHandler.java
---

## 1. 系统概览
本项目采用 Spring Boot 内置的 SLF4J 作为统一日志门面，未引入第三方日志框架（无 logback-spring.xml、log4j2.xml），通过 `application.yml` 与多 Profile 配置文件集中管理日志级别与输出格式。全链路 TraceId 通过自定义 `TraceIdFilter` 写入 SLF4J MDC，并在控制台日志模式中通过 `%X{traceId}` 输出，实现请求级可观测性。

## 2. 核心组件与文件
- **日志门面与使用**：所有模块均通过 `org.slf4j.LoggerFactory.getLogger(...)` 获取 Logger 实例，未发现 Lombok `@Slf4j` 注解的使用。
- **全局异常处理**：`parking-framework/web/GlobalExceptionHandler.java` 在统一异常捕获处记录 warn/error 日志，并附带 URI、错误码、HTTP 状态等结构化字段。
- **TraceId 注入**：`parking-framework/web/TraceIdFilter.java` 在每个 HTTP 请求生命周期内将 traceId 放入 MDC，并在响应头 `X-Trace-Id` 中回传前端。
- **配置中心**：
  - `parking-boot/src/main/resources/application.yml`：定义根日志级别（root: INFO, com.jushan: DEBUG）与控制台输出模式。
  - `application-prod.yml`：生产环境收紧为 root: WARN, com.jushan: INFO，并注释建议由运维侧提供 logback-spring.xml。
  - `application-docker.yml`：开发 Docker 环境使用 DEBUG 级别便于调试。

## 3. 架构与约定
- **日志级别策略**：本地/开发环境对业务包 `com.jushan` 开启 DEBUG；生产环境仅 INFO/WARN，避免性能损耗。
- **结构化字段**：日志行固定包含时间戳、线程名、MDC 中的 traceId、日志级别、logger 名称与消息体；异常场景下附加 URI、错误码、HTTP 状态等上下文。
- **安全红线**：`GlobalExceptionHandler` 明确禁止将堆栈或内部错误详情返回前端，仅记录到服务端日志。
- **MDC 清理**：`TraceIdFilter` 在 finally 块中调用 `MDC.clear()`，防止线程池复用导致上下文泄漏。

## 4. 开发者规范
- 使用 `LoggerFactory.getLogger(当前类.class)` 获取 logger，不要直接使用 `System.out` 或 `printStackTrace`。
- 关键路径（参数校验失败、业务异常、认证授权失败、未知异常）必须通过 `GlobalExceptionHandler` 统一处理，不得自行吞掉异常。
- 需要关联请求时读取 `TraceIdFilter.MDC_KEY` 对应的 traceId，或通过响应头 `X-Trace-Id` 透传给下游服务。
- 新增日志应遵循现有 `warn`/`error` 语义：用户输入错误用 warn，系统故障用 error，并携带足够的上下文字段以便定位问题。
- 生产部署如需文件输出或异步追加器，应在运维侧提供 `logback-spring.xml` 覆盖默认行为（参见 `application-prod.yml` 注释）。