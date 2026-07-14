---
kind: external_dependency
name: Nginx 反向代理服务器
slug: nginx
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### Nginx 反向代理与静态资源服务
- 本地开发映射端口 8088，避免与宿主机 80 端口冲突
- 反向代理 Spring Boot 后端 API 和 WebSocket 连接
- 静态资源托管 admin-web 和 booth-web 构建产物
- 支持 SockJS 降级（WebSocket 不可用时的浏览器兼容）
- 通过 `host.docker.internal:host-gateway` 允许容器访问宿主机服务