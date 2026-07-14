---
kind: external_dependency
name: Redis 7 缓存与会话存储
slug: redis
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### Redis 7 缓存与会话存储
- 用途：Sa-Token 会话存储、分布式锁、热点数据缓存、限流
- Lettuce 客户端连接池：最大活跃 8，最大空闲 8，最小空闲 0
- 本地开发映射非标准端口 6378，AOF 持久化开启
- 内存策略 allkeys-lru，最大内存 256MB
- 健康检查通过 redis-cli ping 验证