# GPIO 控制收敛 + 出站幂等重试 — 设计规格说明书（任务包 7-1）

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| **文档名称** | GPIO 控制收敛 + 出站幂等重试设计规格说明书 |
| **版本号** | V1.0 |
| **编写日期** | 2026-07-18 |
| **对应任务包** | 7-1：设备控制链路收敛 + 幂等重试 |
| **需求依据** | 架构约束 V1.2（平台不直连 EMQX）、订单状态机幂等保证 |
| **前置依赖** | Device Access v0.4 已实现开闸能力 |

---

## 1. Goal

消除当前开闸控制的两条并行路径（GATE→DA→Adapter→MQTTS→EMQX 与 CAMERA→GpioGateService→MQTT→EMQX），将所有 GPIO 控制收敛到单一通道 `Platform → DeviceAccessClient(HTTP) → Adapter(127.0.0.1:8082) → MQTTS → EMQX(:8883) → Device`；同时在 DeviceAccessClient 写命令路径引入 commandId 幂等标记和可配置的重试策略，使开闸/关闸在网络瞬断场景具备有限次数的自动恢复能力，最终失败通过监控告警链路上报。

——同时删除 `GpioGateService`（直连 MQTT）、移除 `org.eclipse.paho.client.mqttv3` 依赖，彻底消除平台侧对 EMQX 的直接引用；补充 mock 适配器和白名单同步 API 以保障本地联调和现场降级场景。

## 2. Non-Goals

| 项目 | 说明 |
| :--- | :--- |
| Adapter 侧 commandId 去重 | commandId 由平台生成并通过 `X-Command-Id` 头传递至 Adapter，Adapter 本期不实现基于 commandId 的去重逻辑，仅透传或记录。Adapter 侧去重为未来迭代内容 |
| 读命令重试 | 仅写命令（openGate/closeGate）启用重试；状态查询（getStatus）、校时（syncTime）、显示屏控制、语音播报等读/展示类命令不纳入重试范围 |
| 增量白名单同步 | 白名单同步 API 为全量导出（full snapshot），不做增量变动推送（delta/CDC） |
| 分布式锁去重 | commandId 的幂等依赖 Adapter 侧的接收方去重或设备侧自带的命令 ID 去重；平台侧不在 DeviceAccessClient 内实现分布式 Redis 锁去重 |
| 生产者确认（MQTT PUBACK） | 适配器通过 MQTTS 下发命令后不等待设备侧 ACK，平台不感知 MQTT QoS |
| EMQX 集群搭建 | 仅输出运维配置文档，不涉及 EMQX 实际部署 |
| MQTT Broker 迁移 | 当前 EMQX 地址 121.41.131.215 外部 IP 为历史遗留，改为通过 Adapter 中转后平台不关心具体 IP；迁移工作由 Ops 团队另行规划 |

## 3. Context

### 3.1 当前控制链路架构

当前平台存在两条独立的开闸路径：

```
路径1（GATE 设备）:  Platform → DeviceAccessClient(HTTP) → Adapter(8082) → MQTTS → EMQX → GATE Device
                     └─ DeviceService.openGate() → deviceAccessClient.openGate(deviceSn) → 命令审计

路径2（CAMERA 设备，GPIO）: Platform → GpioGateService(direct MQTT) → EMQX(tcp://121.41.131.215:1883) → CAMERA GPIO
                            └─ DeviceService.openGate() → gpioGateService.openGate(deviceSn) → 仅日志无审计
```

关键文件位置：

| 文件 | 角色 |
| :--- | :--- |
| `GpioGateService.java`（parking-system） | 直接 MQTT 连接 EMQX（明文 1883 端口），发布 gpio_out 命令 |
| `DeviceService.openGate()` 第 1052 行 | CAMERA 分支调用 `gpioGateService.openGate(deviceSn)` |
| `DeviceService.closeGate()` 第 1147 行 | CAMERA 分支调用 `gpioGateService.closeGate(deviceSn)` |
| `RecognitionEventServiceImpl.executeGateOpen()` 第 424–430 行 | 根据设备类型分发到 `executeCameraGpioOpen()` 或 `executeDaGateOpen()` |
| `RecognitionEventServiceImpl.executeCameraGpioOpen()` 第 477–499 行 | 直接调用 `gpioGateService.openGate(deviceSn)` |
| `RecognitionEventServiceImpl.executeDaGateOpen()` 第 444–468 行 | 调用 `deviceAccessClient.openGate(deviceSn)` |
| `InternalGateController` 第 78–94 行 | dev profile 下暴露 gpioOpen/gpioClose 直连测试端点 |
| `DeviceAccessClientImpl` | 已有 circuit breaker、Micrometer 指标、全部命令方法，无 commandId，无重试 |
| `DeviceCommandAudit` entity | 已定义 `commandId VARCHAR(64)` 字段（可为 null），当前 DeviceService.openGate() 创建审计时不填充 |

### 3.2 路径2的架构违规

- **违反 V1.2 架构约束**：平台不直连 EMQX，GPIO 必须统一经 Adapter 转发。
- **违反安全红线**：明文 MQTT（1883）暴露在公网上，`tcp://121.41.131.215:1883` 为外部 IP。
- **审计缺失**：GpioGateService 的 openGate/closeGate 调用不经过 `device_command_audit` 表，无操作追溯。
- **地址硬编码**：`@Value("${jushan.gpio-gate.broker-url:tcp://121.41.131.215:1883}")` 默认值为外部 IP，违背"适配器地址统一配置"原则。

### 3.3 Adapter 侧已有能力

- Adapter 已支持 `gate_direct_open` 和 `gpio_out` 两种设备协议，根据设备类型自动选择
- Adapter 监听 127.0.0.1:8082，接收 HTTP REST 请求后通过 MQTTS 转发至 EMQX
- 平台不必关心设备侧使用 GATE 还是 GPIO —— Adapter 自动处理协议差异

## 4. Proposed Architecture

### 4.1 目标控制链路

```
                        ┌──────────────┐
                        │   Platform   │
                        │  (SpringBoot)│
                        └──────┬───────┘
                               │ HTTP POST (X-Command-Id, X-API-Key)
                               │ 127.0.0.1:8082
                               ▼
                        ┌──────────────┐
                        │   Adapter    │
                        │  (127.0.0.1) │
                        │   :8082      │
                        └──────┬───────┘
                               │ MQTTS (TLS, port 8883)
                               │ Device-level ACL
                               ▼
                        ┌──────────────┐
                        │     EMQX     │
                        │   :8883      │
                        └──────┬───────┘
                               │ MQTT
                               ▼
                   ┌───────────────────┐
                   │ GATE / CAMERA     │
                   │ Device            │
                   │ (gate_direct_open │
                   │  or gpio_out)     │
                   └───────────────────┘
```

所有开闸/关闸操作统一经过 `DeviceAccessClientImpl` → Adapter，不再存在 Camera→GpioGateService 的旁路。

### 4.2 模块职责重新划分

| 组件 | 改造前 | 改造后 |
| :--- | :--- | :--- |
| `GpioGateService` | 直连 MQTT，gpio_out 命令 | **删除**，整个类及 MQTT 依赖移除 |
| `DeviceService.openGate()` | CAMERA 分支 → gpioGateService；GATE 分支 → deviceAccessClient | **统一** → `deviceAccessClient.openGate(deviceSn, commandId)`，不区分设备类型 |
| `DeviceService.closeGate()` | 同上 | **统一** → `deviceAccessClient.closeGate(deviceSn, commandId)` |
| `RecognitionEventServiceImpl.executeGateOpen()` | CAMERA → executeCameraGpioOpen()；GATE → executeDaGateOpen() | **统一** → `executeDaGateOpen()`（改名 `executeGateOpenInternal()`），同一条路径 |
| `RecognitionEventServiceImpl.executeCameraGpioOpen()` | gpioGateService.openGate() | **删除** |
| `InternalGateController` | 含 gpioOpen/gpioClose 端点 | **删除** gpioOpen/gpioClose 端点；移除 GpioGateService 注入 |
| `DeviceAccessClientImpl` | 无 commandId、无重试 | **新增** commandId 生成/传递、`executeWithRetry()`、X-Command-Id 头 |
| `DeviceAccessClient` 接口 | `openGate(String deviceSn)` | **新增** `openGate(String deviceSn, String commandId)` 重载 |

### 4.3 写命令幂等重试设计

#### 4.3.1 commandId 生成与传递

| 步骤 | 说明 |
| :--- | :--- |
| 生成位置 | `DeviceService.openGate()` / `closeGate()` 中，创建审计记录前生成：`UUID.randomUUID().toString()` |
| 写入审计 | `audit.setCommandId(commandId)`，随 `buildCommandAudit()` 构造 |
| 传递至 Client | 通过 `deviceAccessClient.openGate(deviceSn, commandId)` 新重载方法传入 |
| HTTP 传输 | `DeviceAccessClientImpl` 将 commandId 置入 `X-Command-Id` 请求头 |
| 跨重试一致性 | 同一 commandId 用于所有重试尝试，不会在重试间更换 |

#### 4.3.2 重试策略

```yaml
jushan:
  device-access:
    retry:
      max-attempts: 3          # 含首次尝试，共 3 次
      intervals: 1000,5000,30000  # 第1次重试间隔1s，第2次重试间隔5s（总计最长36s）
```

| 决策点 | 结论 | 理由 |
| :--- | :--- | :--- |
| 重试范围 | **仅 openGate / closeGate**（写命令） | 读命令（getStatus/syncTime等）失败不应重试，由调用方自行决定重查或触发告警 |
| 重试触发条件 | **仅 `ResourceAccessException`**（网络超时/连接拒绝/读取超时） | HTTP 4xx/5xx 业务错误已明确指示失败，重试无意义且可能对设备执行重复操作 |
| 非触发异常 | `BusinessException`（DA 返回非200）、`RestClientException`（序列化/反序列化失败等）不重试 | 这些错误非网络瞬时异常，重试不会自动恢复 |
| 重试间隔 | 1s → 5s → 30s（可配置） | 递增退避避免瞬时过载；30s 作为最后一次重试给予足够恢复窗口 |
| 最终失败处理 | 抛出 `BusinessException` → `DeviceService` 捕获 → `MonitorAlertService.createGateAlert()` (DEVICE_UNCERTAIN) | 利用现有告警链路，岗亭端实时可见 |

#### 4.3.3 executeWithRetry() 实现概要

在 `DeviceAccessClientImpl` 内部新增私有方法：

```
executeWithRetry(pathTemplate, deviceSn, commandId):
    lastException = null
    for attempt from 0 to maxAttempts-1:
        try:
            if attempt > 0:
                sleep(retryIntervals[attempt - 1])  // 0-indexed: 1s, 5s, 30s
            result = doHttpCall(pathTemplate, deviceSn, commandId)
            return result  // 成功立即返回
        catch ResourceAccessException e:
            lastException = e
            log.warn("retry attempt {}/{}", attempt + 1, maxAttempts)
            // 记录 metrics: retry.count
        catch BusinessException | RestClientException e:
            // 非重试异常，直接抛出
            throw e
    // 全部重试耗尽
    throw new BusinessException(lastException)
```

每个重试使用相同 commandId 发出 HTTP 请求，Adapter 侧基于 commandId 可识别为重复指令。

#### 4.3.4 审计状态流转

```
DeviceService.openGate():
  1. 生成 commandId = UUID
  2. 创建 DeviceCommandAudit(status=PENDING, commandId=commandId) → INSERT
  3. try:
       result = deviceAccessClient.openGate(deviceSn, commandId)
       // --- 内部可能经历 2 次重试，但对调用方透明 ---
       audit.setStatus(SUCCESS/FAILED)
     catch BusinessException:
       audit.setStatus(UNCERTAIN) → 触发 DEVICE_UNCERTAIN 告警
  4. audit.setCompletedAt(now) → UPDATE
```

中间重试尝试的日志和指标在 `DeviceAccessClientImpl` 内部记录，不修改审计行（审计仅在最终结果落地时更新状态）。

### 4.4 Mock 适配器

| 决策点 | 结论 | 理由 |
| :--- | :--- | :--- |
| 实现方式 | `MockDeviceAccessClient` implements `DeviceAccessClient`，`@ConditionalOnProperty(name="jushan.device-access.mock.enabled", havingValue="true")` | 与 Spring Boot 条件 Bean 机制一致，不污染生产 ClassPath |
| 默认启用环境 | `application-local.yml` 和 `application-test.yml` 中 `jushan.device-access.mock.enabled=true` | 本地开发和集成测试无需真实 Adapter |
| 生产禁用 | `application.yml` 和 `application-prod.yml` 中 `jushan.device-access.mock.enabled=false`（或不配置，默认 false） | Mock 永远不能在生产环境意外激活 |
| Mock 行为 | 所有方法返回默认成功响应（code=0, message="[MOCK] ..."），不执行任何网络调用 | 最小实现，覆盖全部接口方法确保编译通过和运行时无 NPE |
| DeviceAccessClientImpl 共存 | Mock Bean 注册后，`DeviceAccessClientImpl` 不再加载（两者互斥） | 避免 Spring 容器中出现两个 `DeviceAccessClient` Bean |

Mock 返回值示例：

```json
{"code":0,"message":"success","data":{"success":true,"deviceCode":200,"message":"[MOCK] 开闸成功"}}
```

### 4.5 白名单同步 API

#### 4.5.1 接口定义

```
GET /api/v1/internal/whitelist/sync?parkingLotId={lotId}
Authorization: Bearer <adapter-api-key>
```

#### 4.5.2 数据来源

查询当前停车场下所有"自动放行且不计费"的有效车辆，合并三类数据源：

| 数据源 | 表 | 查询条件 | 返回 type |
| :--- | :--- | :--- | :--- |
| 生效中的月卡 | `monthly_pass` | `pass_status = 'ACTIVE'` AND `parking_lot_id = :lotId` | `MONTHLY_PASS` |
| 生效中的固定车位绑定 | `fixed_space_binding` | `status = 1` (STATUS_ACTIVE) AND `parking_lot_id = :lotId` | `FIXED_SPACE` |
| 生效中的白名单 | `vehicle_list` | `list_type = 'WHITE'` AND `status = 'ACTIVE'` AND `parking_lot_id = :lotId` | `WHITELIST` |

#### 4.5.3 响应格式

```json
{
  "code": 0,
  "data": {
    "parkingLotId": 1,
    "generatedAt": "2026-07-18T15:30:00",
    "totalCount": 152,
    "entries": [
      {
        "plateNumber": "京A12345",
        "type": "MONTHLY_PASS",
        "expireAt": "2026-08-01T00:00:00"
      },
      {
        "plateNumber": "京B67890",
        "type": "FIXED_SPACE",
        "spotCode": "A001",
        "expireAt": "2026-12-31T00:00:00"
      },
      {
        "plateNumber": "京C11111",
        "type": "WHITELIST",
        "expireAt": null
      }
    ]
  }
}
```

#### 4.5.4 鉴权

| 决策 | 说明 |
| :--- | :--- |
| Profile 限制 | `@Profile("dev")` 或在配置中通过 `jushan.device-access.whitelist-sync.enabled` 控制 |
| API Key 校验 | 请求头 `Authorization: Bearer <api-key>` 与 `DeviceAccessProperties.whitelistSyncApiKey` 比对 |
| 租户上下文 | 此接口为 Adapter→Platform 内部通信，不经过租户拦截器。Controller 需显式设置或绕过 `TenantContext` |

#### 4.5.5 实现方式

- Controller: `InternalWhitelistController`（`parking-system/.../controller/`）
- Service: 新增 `WhitelistSyncService`，分别查询三种数据源后合并去重（同一车牌号可能出现在多个数据源，按优先级 MONTHLY_PASS > FIXED_SPACE > WHITELIST 选择首次出现的类型）
- 无需分页——白名单车辆规模预计在数千以内，一次性全量返回

### 4.6 EMQX MQTTS 安全运维配置（文档输出）

不涉及代码。输出文档 `docs/设备接入/emqx-mqtts-ops-config.md`，内容覆盖：

- TLS 证书配置（端口 8883，双向/TLS server auth 模式）
- 设备级 ACL（每台 Camera：`device/{sn}/#` publish/subscribe 权限）
- 用户名/密码认证（EMQX 内置数据库或 HTTP Auth 回调）
- 禁用明文 1883 或锁定仅内网可访问
- MQTT 保留消息清理策略
- Adapter 客户端证书管理

### 4.7 PDNS 降级方案（文档输出）

不涉及代码。在 `docs/设备接入/现场降级方案.md` 中补充一个章节，描述 DNS 不可用场景下 Adapter 如何通过静态 IP 或 hosts 文件回退连接 EMQX。

---

## 5. Files To Change

### 5.1 删除

| 文件 | 说明 |
| :--- | :--- |
| `parking-system/src/main/java/com/jushan/system/service/GpioGateService.java` | **删除**整个类 |
| `parking-boot/pom.xml` 或 `parking-system/pom.xml` 中 `org.eclipse.paho.client.mqttv3` 依赖 | **删除**（仅当无其他模块使用） |

### 5.2 新建

| 文件 | 说明 |
| :--- | :--- |
| `parking-system/src/main/java/com/jushan/system/client/MockDeviceAccessClient.java` | Mock 适配器实现，`@ConditionalOnProperty` |
| `parking-system/src/main/java/com/jushan/system/controller/InternalWhitelistController.java` | 白名单同步 API |
| `parking-system/src/main/java/com/jushan/system/service/WhitelistSyncService.java` | 白名单数据合并查询逻辑 |
| `parking-system/src/main/java/com/jushan/system/dto/WhitelistSyncResponse.java` | DTO 响应数据结构 |
| `parking-system/src/main/java/com/jushan/system/dto/WhitelistEntry.java` | DTO 单条白名单条目 |
| `docs/设备接入/emqx-mqtts-ops-config.md` | EMQX MQTTS 安全运维配置文档 |
| `docs/设备接入/现场降级方案.md` | 现场降级方案（或补充至已有文档） |

### 5.3 修改（后端）

| 文件 | 改动内容 |
| :--- | :--- |
| `parking-framework/src/main/java/com/jushan/framework/config/DeviceAccessProperties.java` | 新增 `Retry retry` 嵌套配置类 + `Mock mock` 嵌套配置类 + `String whitelistSyncApiKey` 属性 |
| `parking-system/src/main/java/com/jushan/system/client/DeviceAccessClient.java` | 新增 `openGate(String deviceSn, String commandId)` 和 `closeGate(String deviceSn, String commandId)` 重载方法 |
| `parking-system/src/main/java/com/jushan/system/client/DeviceAccessClientImpl.java` | ① `openGate(deviceSn, commandId)` / `closeGate(deviceSn, commandId)` 实现；② 新增 `executeWithRetry()` 私有方法；③ `execute()` 方法支持传入 `commandId` 并设置 `X-Command-Id` 请求头；④ 新增 `retry.count` Micrometer 指标 |
| `parking-system/src/main/java/com/jushan/system/service/DeviceService.java` | ① 构造函数移除 `GpioGateService` 参数；② `openGate()` 中 CAMERA 分支移除，统一为 `deviceAccessClient.openGate(deviceSn, commandId)`；③ `closeGate()` 同理；④ `hasOpenGateCapability()` 方法保留但改为仅日志记录（Adapter 侧自行判断）；⑤ 审计记录增加 `commandId` 填充 |
| `parking-system/src/main/java/com/jushan/platform/modules/booth/service/impl/RecognitionEventServiceImpl.java` | ① 构造函数移除 `GpioGateService` 参数；② `executeGateOpen()` 中移除 CAMERA vs GATE 分支，统一调用 `executeDaGateOpen()`（改名）；③ 删除 `executeCameraGpioOpen()` 方法 |
| `parking-system/src/main/java/com/jushan/system/controller/InternalGateController.java` | ① 移除 `GpioGateService` 注入；② 删除 `gpioOpen()` 和 `gpioClose()` 端点 |

### 5.4 修改（配置）

| 文件 | 改动内容 |
| :--- | :--- |
| `parking-boot/src/main/resources/application.yml` | ① `jushan.device-access.base-url=127.0.0.1:8082`；② 新增 `jushan.device-access.retry` 配置段（max-attempts: 3, intervals: 1000,5000,30000）；③ 新增 `jushan.device-access.mock.enabled=false`；④ 新增 `jushan.device-access.whitelist-sync-api-key` |
| `parking-boot/src/main/resources/application-local.yml` | ① 新增 `jushan.device-access.mock.enabled=true`；② 本地开发 whitelist-sync-api-key 配置 |
| `parking-boot/src/main/resources/application-test.yml` | ① `jushan.device-access.mock.enabled=true`（或用 WireMock 替代，取决于测试策略）；② 配置测试用 API key |
| `parking-boot/src/main/resources/application-prod.yml` | ① `jushan.device-access.mock.enabled=false`（显式关闭）；② 移除任何 `gpio-gate` 相关配置 |

### 5.5 修改（测试）

| 文件 | 改动内容 |
| :--- | :--- |
| `parking-system/src/test/java/com/jushan/platform/modules/booth/service/impl/RecognitionEventServiceImplTest.java` | ① 移除 `GpioGateService` mock；② 更新 `executeGateOpen` 相关测试用例（删除 Camera GPIO 路径测试）；③ 新增统一 DA 路径验证 |
| `parking-boot/src/test/java/com/jushan/boot/client/DeviceAccessClientRobustnessTest.java` | 新增重试场景：① ResourceAccessException 触发 3 次重试后抛异常；② HTTP 500 不触发重试；③ commandId 在重试间保持一致 |
| `parking-boot/src/test/java/com/jushan/boot/client/DeviceAccessClientTest.java` | 新增 MockDeviceAccessClient 的 WireMock stub 或独立测试类 |

---

## 6. Testing Strategy

### 6.1 单元测试

| 测试类 | 测试点 |
| :--- | :--- |
| `DeviceAccessClientImplTest`（新建或扩展已有） | ① `openGate(deviceSn, commandId)` 正确设置 `X-Command-Id` 请求头（通过 RestTemplate interceptor 或 WireMock request matching 验证）；② `executeWithRetry()` 在 ResourceAccessException 时正确重试（1 → 5 → 30 秒间隔）；③ 重试 3 次耗尽后抛 `BusinessException`；④ HTTP 500 不触发重试（直接抛 BusinessException）；⑤ 每次重试的 `X-Command-Id` 头值相同；⑥ Micrometer `retry.count` 指标正确递增 |
| `MockDeviceAccessClientTest`（新建） | ① 每个接口方法返回非 null 响应；② `openGate/closeGate` 返回 `success=true`；③ `getStatus` 返回 `online=true`；④ Bean 仅在 `mock.enabled=true` 时加载 |
| `WhitelistSyncServiceTest`（新建） | ① 三表合并查询结果正确；② 同车牌号以优先级合并（MONTHLY_PASS > FIXED_SPACE > WHITELIST）；③ 空车场返回空列表；④ `expireAt` 正确映射（月卡/固定车位有值，白名单为 null） |

### 6.2 集成测试

| 场景 | 验证点 |
| :--- | :--- |
| DeviceService 开闸（GATE 设备） | `deviceAccessClient.openGate(deviceSn, commandId)` 被调用，`DeviceCommandAudit.commandId = commandId`，状态流转 PENDING → SUCCESS/FAILED |
| DeviceService 开闸（CAMERA 设备，原 GPIO 路径） | 不再调用 `GpioGateService`；统一通过 `deviceAccessClient.openGate(deviceSn, commandId)`；审计记录完整 |
| DeviceService 关闸（CAMERA 设备） | 同上；`closeGate` 统一路径 |
| RecognitionEventServiceImpl 自动开闸 | 入场/出场自动开闸不再经过 `executeCameraGpioOpen()`；全部走 `executeDaGateOpen()` 内部 |
| Mock 适配器启用 | `jushan.device-access.mock.enabled=true` 时，`DeviceAccessClientImpl` 不加载，`MockDeviceAccessClient` 替代；开闸操作返回 mock 成功 |
| 白名单同步 API | `GET /api/v1/internal/whitelist/sync?parkingLotId=1` 返回正确车牌列表；API Key 错误返回 401 |

### 6.3 验收标准

1. **grep 零结果**：`grep -r "emqx" parking-system/src/main/java/`、`grep -r "mqtt.*121\.41" parking-system/src/main/java/`、`grep -r "paho.*mqtt" parking-system/src/main/java/` 均返回空
2. **GpioGateService 不存在**：`find parking-system/ -name "GpioGateService.java"` 返回空
3. **commandId 传递验证**：发送开闸请求后，WireMock 记录到的 `X-Command-Id` 头为合法 UUID 格式
4. **重试验证**：模拟 2 次 `ResourceAccessException` + 第 3 次成功，WireMock 记录到 3 次 HTTP 请求，`X-Command-Id` 相同
5. **最终失败告警**：3 次重试全部失败 → `MonitorAlertService.createGateAlert()` 被调用（Mockito verify）
6. **Mock 适配器可用**：`local` profile 运行，完成入场→自动开闸流程，无异常

### 6.4 已有测试适配

- `RecognitionEventServiceImplTest`：移除 GpioGateService mock，所有开闸验证统一为 `verify(deviceAccessClient).openGate(anyString())`
- `DeviceAccessClientRobustnessTest`：现有 circuit breaker 测试与新增 retry 逻辑兼容——重试发生在 circuit breaker 检查之内（breaker 在每次 HTTP 尝试前检查）

---

## 7. Risks And Mitigations

### 风险 1：Adapter 不支持 GPIO 协议（gpio_out），导致 CAMERA 设备开闸失败

- **严重程度**：高
- **影响**：Camera GPIO 控制路径移除后，若 Adapter 未实现 gpio_out 命令，现场所有 CAMERA 设备的闸机将无法操控
- **根因**：Adapter 当前可能只为 GATE 设备实现了 `gate_direct_open`，未实现 `gpio_out`
- **缓解措施**：
  - 在实施前，确认 Adapter 已支持 gpio_out 协议（通过 DeviceAccess API 文档或 Adapter 代码库确认）
  - 若 Adapter 未支持，需先在 Adapter 侧补齐 gpio_out 实现（Adapter 作为独立微服务，可先行发布）
  - 保留 `hasOpenGateCapability()` 方法但改为日志警告而非 GPIO 旁路调用——CAMERA 设备将通过 adapter 路径下发，adapter 发现设备不支持时返回业务错误码，平台根据错误码生成 DEVICE_CONFIG_MISSING 告警
  - 在 Adapter 未就绪前，暂不合并本任务包到主分支

### 风险 2：重试导致实际开闸多次执行（闸杆反复抬起/落下）

- **严重程度**：中
- **影响**：网络超时不能确认命令是否已被设备执行（UNCERTAIN 场景）。如果初次 HTTP 调用实际到达 Adapter 并转发到设备成功，但 HTTP 响应在网络层丢失，平台侧感知为 ResourceAccessException，触发重试，导致同一命令被执行 2 次或更多
- **根因**：HTTP 请求-响应模型的"至多一次"语义在无应用层幂等机制时退化为"至少一次"
- **缓解措施**：
  - commandId 作为应用层幂等键——Adapter（未来版本）根据 commandId 判断是否为重复请求，若已成功执行则直接返回缓存的成功响应而不重复下发
  - 本期（Adapter 未实现 commandId 去重时），平台侧接受"命令可能被重复执行"的风险：开闸重复执行（两次脉冲信号）在道闸硬件侧通常表现为"首次抬起、二次无效果（已在抬起状态）"，实际影响有限
  - 关闸重复执行同理：首次落下后二次无效果
  - 重试间隔（1s → 5s → 30s）递增，降低了短时间内重复脉冲的概率
  - 操作日志和审计记录中标记 commandId，事后可追溯重试链

### 风险 3：删除 GpioGateService 后现有现场依赖断裂

- **严重程度**：中
- **影响**：若现场环境使用了 `InternalGateController` 的 gpio-open/gpio-close 端点进行直接调试，删除后将无法调试
- **缓解措施**：
  - `InternalGateController` 保留 openGate/closeGate 端点（通过 DeviceService → DeviceAccessClient 路径），删除仅 gpioOpen/gpioClose 直连 MQTT 端点
  - Mock 适配器提供与真实 Adapter 行为一致的本地调试能力，替代 gpio-open 的直接 MQTT 需求
  - 文档说明调试方式变更

### 风险 4：白名单同步 API 高并发下性能瓶颈

- **严重程度**：低
- **影响**：若停车场白名单车辆数极大（>10,000 辆），全量导出每次请求需扫描三张表并合并去重，可能造成数据库压力
- **缓解措施**：
  - 白名单同步为定时任务（Adapter 侧定时拉取），频率通常为数分钟一次，非高 QPS 场景
  - 三表查询均为单停车场过滤 + 索引友好字段（parking_lot_id + status），查询计划使用索引
  - 如未来出现性能问题，可引入 Redis 缓存：缓存全量白名单快照，由月卡/固定车位/白名单变更时主动失效
  - 本期不实现缓存，保持简单直接

---

## 8. Decision Summary

- **控制链路收敛**：删除 `GpioGateService` 及 `org.eclipse.paho.client.mqttv3` 依赖，所有开闸/关闸统一经 `DeviceAccessClient → Adapter → MQTTS → EMQX → Device`，不区分 GATE/CAMERA 设备类型
- **Adapter URL**：统一为 `http://127.0.0.1:8082`（与现有 `application.yml` 默认值一致）
- **commandId 生成**：`DeviceService.openGate()` / `closeGate()` 中生成 `UUID.randomUUID().toString()`，写入 `DeviceCommandAudit.commandId` 并通过 `X-Command-Id` HTTP 头传递
- **重试范围**：仅写命令 `openGate` / `closeGate`，不包含读命令和显示屏/语音控制命令
- **重试策略**：最多 3 次（含首次），第 1 次重试间隔 1s，第 2 次重试间隔 5s（配置通过 `jushan.device-access.retry` 段）
- **重试触发条件**：仅 `ResourceAccessException`（网络超时/连接拒绝/IO 异常）；HTTP 4xx/5xx 和序列化异常不重试
- **最终失败告警**：重试耗尽后调用 `MonitorAlertService.createGateAlert()`（DEVICE_UNCERTAIN），利用现有告警链路推送至岗亭端
- **同一 commandId 跨重试**：所有重试尝试使用同一个 UUID，不更换
- **Mock 适配器**：`MockDeviceAccessClient` implements `DeviceAccessClient`，`jushan.device-access.mock.enabled=true` 时通过 `@ConditionalOnProperty` 加载，local/test profile 默认启用
- **白名单同步 API**：`GET /api/v1/internal/whitelist/sync?parkingLotId={lotId}`，全量导出生效中月卡 + 固定车位 + 白名单车辆，Bearer Token 鉴权
- **白名单优先级**：同车牌号合并时 MONTHLY_PASS > FIXED_SPACE > WHITELIST
- **EMQX 安全**：文档输出运维配置（TLS 8883、设备 ACL、禁用明文 1883），不涉及代码
- **PDNS 降级**：文档输出现场降级方案，不涉及代码
- **Adapter 侧不变更**：本期 Adapter 不修改，不支持 commandId 去重（未来版本补齐）
- **InternalGateController**：保留 openGate/closeGate 端点（经统一路径），删除 gpioOpen/gpioClose 端点
