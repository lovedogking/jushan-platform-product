# EMQX MQTTS 安全运维配置指南

> **版本**: V1.0 | **日期**: 2026-07-18 | **关联**: 任务包 7-1

## 1. 概述

本文档描述 EMQX Broker 的安全配置要求，确保平台与设备之间的 MQTT 通信经过 TLS 加密和访问控制。

**目标拓扑**：
```
Device (Camera/Gate) ──MQTTS(:8883)──→ EMQX Broker ←──MQTTS── Adapter (127.0.0.1:8082)
```

平台不直接连接 EMQX。所有控制指令经由 Adapter 中转。

## 2. TLS 证书配置

### 2.1 端口配置

```hcl
# emqx.conf
listeners.ssl.default {
  bind = "0.0.0.0:8883"
  max_connections = 10000
  ssl_options {
    keyfile = "/etc/emqx/certs/server.key"
    certfile = "/etc/emqx/certs/server.crt"
    cacertfile = "/etc/emqx/certs/ca.crt"
    verify = verify_peer         # 双向 TLS：要求客户端证书
    fail_if_no_peer_cert = true
    versions = [tlsv1.2, tlsv1.3]
    ciphers = ["ECDHE-ECDSA-AES256-GCM-SHA384", "ECDHE-RSA-AES256-GCM-SHA384"]
  }
}
```

### 2.2 证书管理

| 证书类型 | 用途 | 签发周期 |
|----------|------|---------|
| CA 证书 | 签发所有设备端和 Adapter 端证书 | 5 年 |
| 服务端证书 | EMQX TLS 握手（CN=emqx.your-domain.com） | 1 年 |
| 设备证书 | 每台 Camera/Gate 持有（CN=device/{sn}） | 1 年 |
| Adapter 客户端证书 | Adapter 连接 EMQX（CN=adapter） | 1 年 |

## 3. 禁用明文 MQTT（1883）

```hcl
# 禁用或锁定为仅内网
listeners.tcp.default {
  bind = "127.0.0.1:1883"  # 仅本地回环，禁止外部访问
  max_connections = 10
}
```

防火墙规则：
```bash
# 仅允许 Adapter 的 IP 访问 8883 端口
iptables -A INPUT -p tcp --dport 8883 -s <ADAPTER_IP> -j ACCEPT
iptables -A INPUT -p tcp --dport 8883 -j DROP

# 完全禁止外部访问 1883
iptables -A INPUT -p tcp --dport 1883 -j DROP
```

## 4. 设备级 ACL

每台设备使用独立证书（CN=device/{sn}），根据 CN 进行细粒度权限控制：

```erlang
%% acl.conf
%% Adapter 拥有所有设备 topic 发布权限
{allow, {username, "adapter"}, publish, ["device/+/message/down/#"]}.
{allow, {username, "adapter"}, subscribe, ["device/+/message/up/#"]}.

%% 每台设备仅能发布自己的上行消息，订阅自己的下行消息
{allow, {username, {re, "^device/.+$"}}, publish, ["device/${username}/message/up/#"]}.
{allow, {username, {re, "^device/.+$"}}, subscribe, ["device/${username}/message/down/#"]}.
```

## 5. 用户名/密码认证

推荐使用 EMQX 内置数据库认证：

```hcl
# emqx.conf
authentication = [
  {
    mechanism = "password_based"
    backend = "built_in_database"
    user_id_type = "username"
  }
]
```

通过 EMQX Dashboard 或 HTTP API 创建用户：
```bash
# 创建 Adapter 用户
curl -X POST http://localhost:18083/api/v5/authentication/password_based:built_in_database/users \
  -u admin:public \
  -d '{"user_id":"adapter","password":"<STRONG_PASSWORD>"}'

# 创建设备用户（每台设备一个）
curl -X POST http://localhost:18083/api/v5/authentication/password_based:built_in_database/users \
  -u admin:public \
  -d '{"user_id":"device/CAM-SN-001","password":"<DEVICE_PASSWORD>"}'
```

## 6. MQTT 保留消息清理策略

```hcl
# 禁用保留消息（防止过期指令遗留）
mqtt.retry_interval = 30s
mqtt.max_retained_messages = 0

# 定期清理规则
retainer {
  enable = false
}
```

## 7. Adapter 客户端证书部署

```bash
# 在 Adapter 所在服务器
mkdir -p /etc/jushan-adapter/certs
cp adapter.crt adapter.key ca.crt /etc/jushan-adapter/certs/
chmod 600 /etc/jushan-adapter/certs/adapter.key
```

Adapter 配置中引用证书路径（具体参数名以 Adapter 代码为准）：
```yaml
mqtt:
  broker-url: ssl://emqx.your-domain.com:8883
  client-cert: /etc/jushan-adapter/certs/adapter.crt
  client-key: /etc/jushan-adapter/certs/adapter.key
  ca-cert: /etc/jushan-adapter/certs/ca.crt
```

## 8. 健康检查

```bash
# 验证 MQTTS 端口可达
openssl s_client -connect emqx.your-domain.com:8883 -CAfile ca.crt -cert adapter.crt -key adapter.key
```

预期：TLS 握手成功，EMQX 返回 CONNACK。
