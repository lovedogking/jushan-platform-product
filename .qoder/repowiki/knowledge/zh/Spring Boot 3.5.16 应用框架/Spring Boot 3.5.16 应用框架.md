---
kind: external_dependency
name: Spring Boot 3.5.16 应用框架
slug: spring-boot
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

- 作为根模块 `spring-boot-starter-parent` 版本，统一管理所有子模块的 Spring Boot 依赖
- Java 21 LTS 运行时，Maven 多模块聚合构建
- Actuator 健康检查端点 `/actuator/{health,info}` 暴露 readiness/liveness probes
- 生产环境通过环境变量注入敏感配置，禁止写入配置文件