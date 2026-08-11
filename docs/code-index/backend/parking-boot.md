# 模块：parking-boot（应用启动）

> **包路径**：`parking-boot/src/main/java/com/jushan/`
> **职责**：Spring Boot 启动入口、组件扫描、MyBatis-Plus 分页装配、Flyway 迁移、Actuator 探针、多环境配置。
> **最近更新**：2026-08-11（登记 TomcatUtf8Config）

---

| 类 | 职责 |
|---|---|
| `ParkingApplication` | Spring Boot 主启动类 |
| `config/TomcatUtf8Config` | 强制 Tomcat/Servlet 请求使用 UTF-8 编码（解决 Windows 本地开发中文 GET/POST 参数乱码） |
| 其余（7 个） | 全局配置、MyBatis 分页插件注册等 |

> 配置分散在 `application.yml` / `application-{profile}.yml`（如 `docker` profile）。
