---
kind: external_dependency
name: MySQL 8.4 关系数据库
slug: mysql
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### MySQL 8.4 主数据库
- 字符集 utf8mb4，排序规则 utf8mb4_unicode_ci，时区 Asia/Shanghai (+08:00)
- HikariCP 连接池：最大连接数 10，最小空闲 2，连接超时 10s
- Flyway 数据库迁移工具，脚本位于 `classpath:db/migration`
- 本地开发映射非标准端口 3307，避免与宿主机冲突
- 数据持久化卷 `jushan-mysql-data`，支持健康检查和自动初始化 SQL