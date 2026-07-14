# Sprint 1 集成测试运行说明

本目录包含 Sprint 1 的完整集成测试套件，基于 `@SpringBootTest + Testcontainers(MySQL + Redis) + REST Assured` 编写。

## 测试覆盖范围

- **租户隔离** (`TenantIsolationTest`)：二级/三级管理员只能看到本租户/本车场数据。
- **角色权限** (`RolePermissionTest`)：`@RequirePermission` 生效、权限矩阵 Redis 缓存。
- **账号安全** (`AdminAccountSecurityTest`)：登录失败锁定、重置密码、BCrypt 加密。
- **授权码生命周期** (`AuthCodeLifecycleTest`)：生成、激活、禁用、过期/用完校验。
- **业务日志** (`BusinessLogTest`)：`@BusinessLog` 异步写入、敏感字段脱敏、接口不阻塞。
- **跨租户拒绝** (`CrossTenantRejectionTest`)：越权访问返回 403，参数篡改无效。

## 前置条件

- Docker Desktop / Docker Engine 已启动
- JDK 21
- Maven 3.9+
- 约 4 GB 可用内存（同时运行 MySQL 8.4 与 Redis 7 容器）

## 运行全部 Sprint 1 测试

```bash
cd /Users/zengbohan/Documents/project/jushan-platform
mvn -pl parking-boot test -Dtest="com.jushan.boot.sprint1.*Test"
```

## 运行单个测试类

```bash
mvn -pl parking-boot test -Dtest="com.jushan.boot.sprint1.TenantIsolationTest"
```

## 运行原理

1. 激活 `test` profile，使用 `application-test.yml` 中的占位配置。
2. `TestcontainersBaseTest` 启动 MySQL 8.4 容器，`Sprint1IntegrationTest` 追加 Redis 7 容器。
3. Flyway 自动执行所有迁移并初始化种子数据（`super_admin/admin123`、`DEFAULT_TENANT`、基础角色权限）。
4. 每个测试类在 `@BeforeAll` 中登录 `super_admin` 并创建自身所需数据。
5. 每个测试类在 `@AfterAll` 中通过 `JdbcTemplate` 显式清理数据，保留种子数据。

## 数据清理保证

- 不删除 `sys_admin_account.id=1` 的 `super_admin` 账号。
- 不删除 `sys_tenant.id=1` 的默认租户。
- 不删除种子角色与权限数据。
- 测试产生的公司、账号、角色、授权码、业务日志及临时租户会在 `@AfterAll` 中移除。

## 目录结构

```
parking-boot/src/test/java/com/jushan/boot/sprint1/
├── Sprint1IntegrationTest.java      # 基类
├── TenantIsolationTest.java
├── RolePermissionTest.java
├── AdminAccountSecurityTest.java
├── AuthCodeLifecycleTest.java
├── BusinessLogTest.java
├── BusinessLogTestConfig.java       # 测试专用 @BusinessLog 控制器
└── CrossTenantRejectionTest.java

parking-boot/src/test/resources/
├── sprint1-postman-collection.json  # Postman 集合
└── TEST_README.md                   # 本文件
```

## Postman 集合使用

1. 导入 `sprint1-postman-collection.json`。
2. 先执行 **Auth / 登录**，集合会自动将返回的 `token` 写入集合变量。
3. 其他请求会自动使用 `Authorization: Bearer {{token}}`。
4. 创建公司/账号/角色/授权码后，对应 ID 会自动写入集合变量，供后续请求使用。

## 常见问题

### Docker 不可用

确保 Docker 守护进程已启动。Testcontainers 会尝试连接 `/var/run/docker.sock`。

### Flyway checksum 校验失败

如果迁移文件被本地修改过，可执行：

```bash
mvn -pl parking-boot flyway:repair
```

或在 MySQL 中删除测试 schema 后重新运行（测试容器每次都会重新创建数据库）。

### 端口冲突

测试使用 `WebEnvironment.RANDOM_PORT`，由 Spring Boot 自动分配可用端口，通常不会冲突。如遇冲突，请检查是否有其他进程占用了预期端口范围。

### Redis 连接失败

`Sprint1IntegrationTest` 已声明 Redis Testcontainers 容器，`@ServiceConnection` 会自动注入 host/port，无需手动配置。

## 注意事项

- 测试默认不会自动提交代码或修改 Git 历史。
- 请勿在生产数据库上运行这些测试。
- 业务日志读接口 (`/api/v1/business-logs`) 在 Sprint 1 尚未实现，当前通过直接查询 `sys_business_log` 表进行验证。
