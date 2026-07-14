---
kind: configuration_system
name: Spring Boot 多环境配置与生产安全校验体系
category: configuration_system
scope:
    - '**'
source_files:
    - parking-boot/src/main/resources/application.yml
    - parking-boot/src/main/resources/application-local.yml
    - parking-boot/src/main/resources/application-test.yml
    - parking-boot/src/main/resources/application-prod.yml
    - parking-boot/src/main/java/com/jushan/boot/config/AppConfig.java
    - parking-boot/src/main/java/com/jushan/boot/config/AppProperties.java
    - parking-boot/src/main/java/com/jushan/boot/config/ProdConfigValidator.java
    - parking-framework/src/main/java/com/jushan/framework/config/DeviceAccessProperties.java
    - parking-framework/src/main/java/com/jushan/framework/config/DeviceAccessConfig.java
    - .env.example
---

## 系统概述
本仓库采用 Spring Boot 原生 `application.yml` + Profile 机制实现多环境配置，结合 `@ConfigurationProperties` 类型绑定、环境变量注入以及 `prod` Profile 下的运行时校验器，形成“默认值 → 环境覆盖 → 启动前强校验”的三层配置治理体系。

## 核心文件与包
- 应用入口装配：`parking-boot/src/main/java/com/jushan/boot/config/AppConfig.java`
- 应用级属性类：`parking-boot/src/main/java/com/jushan/boot/config/AppProperties.java`（prefix=`app`）
- 生产环境关键配置校验器：`parking-boot/src/main/java/com/jushan/boot/config/ProdConfigValidator.java`（仅 `@Profile("prod")` 生效）
- 框架层 Device Access 客户端配置：
  - `parking-framework/src/main/java/com/jushan/framework/config/DeviceAccessProperties.java`（prefix=`jushan.device-access`）
  - `parking-framework/src/main/java/com/jushan/framework/config/DeviceAccessConfig.java`（创建专用 RestTemplate Bean）
- 配置文件：
  - `parking-boot/src/main/resources/application.yml`（通用基线，所有 Profile 共享）
  - `parking-boot/src/main/resources/application-local.yml`（本地开发，默认激活）
  - `parking-boot/src/main/resources/application-test.yml`（测试，禁用 RabbitMQ 自动装配）
  - `parking-boot/src/main/resources/application-prod.yml`（生产骨架，全部敏感项走 `${VAR}` 占位）
- 环境变量清单：`.env.example`（Docker Compose 与部署时参考）

## 架构与约定
1. **Profile 分层**
   - `application.yml`：存放所有环境共享的基础配置（数据源默认 local、Flyway 开启、Sa-Token 基础参数、Actuator 暴露 health/info、日志级别等）。
   - `application-{local,test,prod}.yml`：按环境覆盖具体连接地址、开关与日志级别；`local` 默认激活，`test` 排除 RabbitAutoConfiguration，`prod` 全部使用 `${ENV_VAR:default}` 形式。
2. **配置属性绑定**
   - 业务模块通过 `@ConfigurationProperties(prefix = "xxx")` + `@Validated` 声明强类型属性类，由 `@EnableConfigurationProperties` 在对应 `@Configuration` 中启用（如 `AppConfig`、`DeviceAccessConfig`）。
   - 属性类内部使用 `@NotBlank`、`@Min/@Max` 等 JSR-303 注解进行加载期校验。
3. **环境变量优先**
   - 生产配置文件中所有敏感值（数据库、Redis、RabbitMQ、Sa-Token、Device Access 等）均以 `${VAR}` 形式引用，缺失时直接启动失败，杜绝硬编码密钥。
   - `.env.example` 集中列出所有可注入的环境变量及 Docker Compose 端口映射建议。
4. **生产启动前强校验**
   - `ProdConfigValidator` 仅在 `prod` Profile 下运行，逐项检查 `spring.datasource.*`、`spring.data.redis.host`、`sa-token.*`、`app.initial-admin-password`、`jushan.device-access.*` 等关键项是否为空或仍为开发默认值，任一缺失即抛 `IllegalStateException` 终止启动。
5. **跨模块配置隔离**
   - 平台通用配置集中在 `parking-boot`；设备接入 HTTP 客户端配置下沉到 `parking-framework`，以独立 prefix `jushan.device-access` 管理，避免被业务模块误改。
6. **前端工程配置**
   - `admin-web/.env.development`、`admin-web/.env.production`、`booth-web/.env.development`、`booth-web/.env.production` 分别定义 Vite 构建期环境变量（API 基址、代理等），与后端配置解耦。

## 开发者应遵循的规则
- **禁止在代码中硬编码任何敏感值**（密码、密钥、回调地址等），一律通过环境变量或对应 profile 覆盖。
- **新增配置属性**：新建 `@ConfigurationProperties` 类并加上 `@Validated` 约束，在对应模块的 `@Configuration` 中用 `@EnableConfigurationProperties` 启用；若属于平台级配置，放在 `parking-boot`，否则放入相应子模块。
- **生产环境新增必填项**：同步更新 `application-prod.yml` 中的 `${VAR}` 占位，并在 `ProdConfigValidator` 中补充 `checkRequired` / `checkRequiredRange` 校验逻辑，确保缺失时启动失败。
- **不要修改 `application.yml` 中的默认值用于生产**：生产差异必须体现在 `application-prod.yml` 或通过环境变量覆盖。
- **Device Access 客户端**：不得自行添加重试拦截器；超时与错误处理统一由 `DeviceAccessConfig` 提供的 RestTemplate 负责，调用方只读取响应体错误码。
- **前端环境变量**：Vite 的 `.env.*` 文件仅影响构建期常量，不随容器运行注入，需与后端 API 基址保持一致并通过 Nginx 反向代理统一出口。