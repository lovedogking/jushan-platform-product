---
kind: dependency_management
name: 多语言依赖管理：Maven 聚合 + pnpm 工作区
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - parking-framework/pom.xml
    - parking-system/pom.xml
    - parking-boot/pom.xml
    - admin-web/package.json
    - booth-web/package.json
    - admin-web/.npmrc
    - booth-web/.npmrc
    - admin-web/pnpm-workspace.yaml
    - booth-web/pnpm-workspace.yaml
---

## 1. 使用的系统与工具

- 后端（Java）：基于 Spring Boot Parent 的 Maven 聚合工程，使用 dependencyManagement 集中声明第三方版本，子模块仅引入 artifactId 不写版本号。
- 前端（Vue3）：admin-web 与 booth-web 两个独立 pnpm 包，各自维护 package.json、pnpm-lock.yaml 与 .npmrc，通过 pnpm-workspace.yaml 限制允许本地构建的 native 依赖。
- 小程序：miniapp 为原生微信小程序工程，无外部包管理器。

## 2. 关键文件与位置

- 根 POM：pom.xml — 继承 spring-boot-starter-parent:3.5.16，定义 properties 统一版本、dependencyManagement 收敛 MyBatis-Plus / Sa-Token / Flyway / Hutool / MapStruct / Testcontainers 等版本，并通过 modules 声明 parking-common/framework/system/boot 四个子模块。
- 子模块 POM：
  - parking-framework/pom.xml：横切能力（Web/Redis/RabbitMQ/WebSocket/Sa-Token）依赖入口。
  - parking-system/pom.xml：业务域依赖，复用 framework 并显式引入 hutool、micrometer-core、mysql-connector-j。
  - parking-boot/pom.xml：应用启动装配，额外引入 actuator、prometheus registry、flyway-mysql、wiremock、testcontainers 测试栈。
- 前端包清单：
  - admin-web/package.json、booth-web/package.json：声明 Vue3、Ant Design Vue、axios、pinia、vite、typescript 等依赖及 devDependencies。
  - admin-web/.npmrc、booth-web/.npmrc：通过 onlyBuiltDependencies[] 白名单限定允许编译 native 的包。
  - admin-web/pnpm-workspace.yaml、booth-web/pnpm-workspace.yaml：将相同 four 个包列入 allowBuilds，禁止其余 native 构建。
- 锁文件：admin-web/pnpm-lock.yaml、booth-web/pnpm-lock.yaml（提交到仓库，保证可重现安装）。

## 3. 架构与约定

### Maven 侧
- 单点版本源：所有第三方库的版本集中在根 POM 的 properties 中，如 mybatis-plus.version=3.5.9、sa-token.version=1.42.0、hutool.version=5.8.35、mapstruct.version=1.6.3、testcontainers.version=1.20.6；新增依赖应优先在根 POM 的 dependencyManagement 中声明，再在子模块以 artifactId 引用。
- 内部模块分层：common → framework → system → boot 单向依赖，boot 作为唯一可执行 jar，负责组合装配。
- 插件与编译器：根 POM 的 pluginManagement 统一配置 maven-compiler-plugin（Lombok + MapStruct 注解处理器路径）、spring-boot-maven-plugin（排除 lombok）、maven-surefire-plugin（注入 DOCKER_HOST 等环境变量供 Testcontainers 使用）。
- 测试依赖隔离：WireMock、Testcontainers 相关依赖仅在 parking-boot 中以 scope=test 引入，避免污染生产包。

### pnpm 侧
- 每个前端工程独立维护 package.json 与 pnpm-lock.yaml，未采用顶层 workspace 聚合，而是通过各自的 pnpm-workspace.yaml 控制只允许构建的 native 依赖。
- .npmrc 中的 onlyBuiltDependencies[] 与 pnpm-workspace.yaml 的 allowBuilds 双保险，防止 CI 或 Docker 环境中因缺少系统头文件导致 node-gyp 失败。
- 未配置私有 npm 镜像或 .npmrc 中的 registry=，默认走官方 npm 源。

## 4. 开发者应遵循的规则

1. 新增 Java 依赖：先在根 pom.xml 的 properties 声明版本号，再在 dependencyManagement 中注册 artifact，最后在目标子模块 POM 中以 artifactId 引用，不要自行写 version。
2. 保持依赖方向：system 只能依赖 framework 和 common，不得反向；boot 是唯一的运行期装配层。
3. 测试依赖 scope：WireMock、Testcontainers、JUnit 等仅限 scope=test，避免进入最终产物。
4. 前端新增包：在对应工程的 package.json 中添加，并提交生成的 pnpm-lock.yaml；如需带 native 构建的包，同步更新该工程的 .npmrc 与 pnpm-workspace.yaml 白名单。
5. 版本升级流程：先改根 POM properties 中对应变量，再全仓 mvn dependency:tree 验证传递依赖冲突；前端则用 pnpm up --latest 后检查 lock 差异。
6. 构建环境：确保 CI/Docker 提供 Docker socket 与 DOCKER_HOST 环境变量，以便 Surefire 正确传递给 Testcontainers。