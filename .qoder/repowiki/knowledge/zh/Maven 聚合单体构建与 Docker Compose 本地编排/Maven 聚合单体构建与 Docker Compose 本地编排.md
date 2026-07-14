---
kind: build_system
name: Maven 聚合单体构建与 Docker Compose 本地编排
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - parking-boot/pom.xml
    - docker-compose.yml
    - docker/nginx/nginx.conf
    - docker/nginx/conf.d/default.conf
    - admin-web/package.json
    - booth-web/package.json
---

## 1. 构建系统概览

本项目采用 **Maven 多模块聚合 + Spring Boot Maven Plugin** 作为后端构建核心，前端使用 **Vite + pnpm** 独立构建，整体通过 **Docker Compose** 编排 MySQL、Redis、RabbitMQ、Nginx 等运行时依赖，形成“后端在宿主机运行 + 基础设施容器化”的本地开发体验。仓库中未发现 CI/CD 流水线（如 GitHub Actions、Jenkinsfile）或独立的 Dockerfile，发布产物以 `spring-boot:build-image` / `jar` 为主。

## 2. 关键文件与位置

- 根 POM：`pom.xml` — 聚合模块声明、统一版本管理、插件配置
- 启动模块：`parking-boot/pom.xml` — 应用打包入口，引入 spring-boot-maven-plugin
- 前端工程：`admin-web/package.json`、`booth-web/package.json` — Vite 脚本与依赖
- 本地编排：`docker-compose.yml` — MySQL/Redis/RabbitMQ/Nginx 服务定义
- Nginx 配置：`docker/nginx/nginx.conf`、`docker/nginx/conf.d/default.conf` — API/WS/静态资源反向代理
- 环境变量模板：`.env.example`（由 compose 引用）

## 3. 架构与约定

### 3.1 Maven 多模块结构

```
jushan-platform (packaging=pom)
├── parking-common       # 通用类型（R、ErrorCode、BusinessException）
├── parking-framework    # 横切能力（Sa-Token、TraceId、Redis、MQ、WebSocket）
├── parking-system       # 业务子域（租户/设备/计费/微信等）
└── parking-boot         # Spring Boot 启动入口 & 组件装配
```

- 根 POM 继承 `spring-boot-starter-parent:3.5.16`，Java 21 为唯一编译目标。
- 所有第三方依赖版本集中在 `<properties>` 与 `<dependencyManagement>` 中，子模块仅声明 groupId/artifactId，不写 version。
- 内部模块版本通过 `${jushan.version}` 统一控制，当前为 `1.0.0-SNAPSHOT`。
- 编译期注解处理器统一在根 POM 的 `maven-compiler-plugin` 中配置 Lombok + MapStruct 兼容路径。

### 3.2 测试与外部依赖

- 集成测试基于 **Testcontainers**（MySQL、RabbitMQ），通过 Surefire 传递 `DOCKER_HOST` 与 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 环境变量。
- 单元测试使用 JUnit Jupiter；HTTP Mock 使用 WireMock（test scope）。

### 3.3 前端构建

- admin-web 与 booth-web 各自维护 `package.json`，使用 `pnpm` 工作区（含 `pnpm-workspace.yaml`）。
- 构建脚本：`dev` → `vite`，`build` → `vue-tsc -b && vite build`，`preview` → `vite preview`。
- 未启用 ESLint/Prettier 的 CI 阶段命令（仅本地可用）。

### 3.4 本地运行时编排

`docker-compose.yml` 提供：
- MySQL 8.4（端口映射到 `${DB_PORT:-3307}`，默认 3307）
- Redis 7（默认 6378）
- RabbitMQ 4 Management（AMQP 5672 + UI 15672）
- Nginx Alpine（默认 8088），将 `/api/*` 重写后转发到宿主机 `host.docker.internal:8080`，并透传 `X-Trace-Id` 头；`/ws/*` 支持 STOMP WebSocket 长连接；`/actuator/*` 暴露健康检查。

Nginx 站点配置预留了 `/admin`、`/booth` 静态资源 location，注释说明生产构建后可取消注释挂载 `dist` 目录。

### 3.5 数据库迁移

- 使用 Flyway，SQL 脚本位于 `parking-boot/src/main/resources/db/migration/V<YYYYMMDDNNN>__*.sql`，按时间戳命名，由 Spring Boot 启动时自动执行。

## 4. 开发者应遵循的规则

1. **新增后端模块**
   - 在根 `pom.xml` 的 `<modules>` 中注册，并在 `<dependencyManagement>` 中声明其坐标与版本。
   - 如需新依赖，优先放入根 POM 的 `<properties>` + `<dependencyManagement>`，子模块只引用不指定版本。

2. **修改公共依赖版本**
   - 统一在根 POM 的 `<properties>` 中调整，避免各模块散落版本号。

3. **添加新的数据库表/字段**
   - 新建 `db/migration/V<YYYYMMDDNNN>__描述.sql`，保持幂等（DROP IF EXISTS / CREATE TABLE IF NOT EXISTS），确保 Flyway 可重复执行。

4. **编写集成测试**
   - 使用 Testcontainers 启动 MySQL/RabbitMQ，并通过 `@SpringBootTest` + `@Testcontainers` 组合；确保本地 Docker 环境可用。

5. **前端新增页面/功能**
   - 在对应 `admin-web` 或 `booth-web` 下新增 Vue 组件，按需更新 `src/router/index.ts` 与 `src/api/*.ts`。
   - 构建产物输出到各自 `dist/`，后续可通过 Nginx 挂载到 `/usr/share/nginx/html/admin|booth`。

6. **本地调试链路**
   - 先 `docker compose up -d` 启动基础设施，再分别 `pnpm dev` 启动前后端，最后访问 `http://localhost:8088` 经 Nginx 路由到后端。

7. **发布与镜像**
   - 当前仓库无 Dockerfile，建议通过 `mvn spring-boot:build-image` 生成 OCI 镜像，或在 CI 中补充 Dockerfile 以完成镜像构建与推送。

## 5. 缺失项说明

- 未发现 `Dockerfile`、CI 配置文件（`.github/workflows`、`Jenkinsfile`、`.gitlab-ci.yml` 等）、Makefile 或 `build.sh` 等自动化脚本。
- 前端 lint/format 仅在本地脚本中定义，未见纳入统一的构建流程。
