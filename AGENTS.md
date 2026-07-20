# AGENTS.md — 停车SaaS系统（一期 V1.4）

> **唯一需求基准**: 本文件 + 用户任务描述中的一期需求规格
> **旧 V1.2 文档已废弃删除**，以本文档为准

---

## 一、项目概要

多租户停车SaaS平台。统一 Web 应用（booth-web）按角色渲染三个区域：**平台管理区** / **车场运营区** / **岗亭工作区**，外加微信小程序端。通过适配器层（device-access）对接硬件设备，一期仅支持臻识 C5。

**一期目标**: 固定车白名单自动放行 + 岗亭人工放行 + 常开/常关闸机控制 + 最快接入 1 个真实停车场。

**一期不做**: 黑名单（代码保留不启用）、月卡（代码保留不启用）、临时车/无牌车（代码保留不启用）、优惠券/积分、访客预约、真实支付、电子发票、短信推送、岗亭离线、地感对接、视频预览、流媒体网关。

---

## 二、角色与权限矩阵

| 角色 | 可见范围 | 终端 | 创建方式 |
| :--- | :--- | :--- | :--- |
| 超级管理员 | 全平台 | 运营端（三区全可见） | 系统预设 |
| 租户管理员 | 被分配的车场 | 运营端（车场运营区） | 仅超管创建 |
| 岗亭管理员 | 被分配的车场（跨租户） | 岗亭端（岗亭工作区） | 仅超管创建并分配车场 |
| 小程序用户 | 本人车辆 | 小程序 | 微信授权+手机号绑定 |

- 岗亭管理员不隶属特定租户，可跨租户分配车场（不构成租户数据越权）
- 租户管理员远程开闸权限默认关闭
- 岗亭管理员费用减免权限需创建时勾选
- 一期：岗亭管理员不登录运营端；V1.5 起运营端角色（超管/租户管理员）也可进入岗亭工作区

### 权限矩阵

| 模块 | 超管 | 租户管理员 | 岗亭管理员 | 小程序用户 |
| :--- | :---: | :---: | :---: | :---: |
| 账号管理（AD） | 增删改查 | × | × | × |
| 系统参数 | 增删改查 | × | × | × |
| 角色权限配置 | 增删改查 | × | × | × |
| 车场管理（SA） | 增删改查 | 查看/修改 | × | × |
| 运营数据（SA） | 全部车场 | 被分配车场 | × | × |
| 固定车管理（OP） | 增删改查+导入 | 增删改查+导入 | × | × |
| 通行记录查询（OP/GB） | 全部数据 | 本租户数据 | 本车场数据 | × |
| 远程开闸 | 全部通道 | 需权限 | × | × |
| 岗亭监控面板（GB） | 操作 | 操作 | 操作 | × |
| 手动开闸/关闸（GB） | 操作 | 操作 | 操作 | × |
| 常开/常关控制（GB） | 操作 | 操作 | 操作 | × |
| 人工放行（GB） | 操作 | 操作 | 操作 | × |
| 现场收费 | 操作（减免需权限） | 操作（减免需权限） | 操作（减免需权限） | × |
| 停车缴费 | × | × | × | 本人车辆 |

> 后端所有接口按上表做角色鉴权，越权返回 403
> 前端路由守卫拦截越权页面
> 注：V1.5 起三角色（超管/租户管理员/岗亭管理员）均可进入并操作岗亭工作区

---

## 三、功能编号（一期专用）

| 前缀 | 区域 | 说明 |
| :--- | :--- | :--- |
| **AD-xx** | 平台管理区 | 超管专属：账号管理、系统参数、角色权限 |
| **SA-xx** | 车场运营区 | 超管+租户管理员：车场/车道/设备管理 |
| **OP-xx** | 车场运营区 | 固定车管理、通行记录查询 |
| **GB-xx** | 岗亭工作区 | 岗亭管理员：监控面板、手动开闸、人工放行、常开/常关 |
| **BR-xx** | 业务规则 | 跨模块行为约束 |
| **GAP-xx** | 一期差距 | 代码现状与一期目标的差距项 |
| **AC-xx** | 验收标准 | 一期自测验收项 |

### 具体功能清单

**平台管理区（AD）**:
- AD-01: 账号管理（创建账号、绑定角色、绑定归属停车场、岗亭员额外绑定归属车场管理员）
- AD-02: 系统参数配置
- AD-03: 角色权限配置

**车场运营区（SA）**:
- SA-01: 车场管理（统一页面：左侧车场列表 + 右侧三 Tab — 车场信息/车道配置/设备管理，支持嵌套创建设备含 IP/端口/子网掩码/网关配置）
- SA-04: 设备状态监控
- SA-05: 远程开闸
- SA-06: 运营数据分析（车流量统计、入场方式分布饼图、车流量趋势折线图、KPI 卡片，营收一期占位）

**车场运营区（OP）**:
- OP-01: 固定车管理（增删改查，t_vehicle_list 表，场内车牌唯一）
- OP-02: 固定车 Excel 批量导入（格式校验 + 重复校验 + 失败明细）
- OP-03: 固定车到期管理
- OP-04: 通行记录查询（查 parking_session，按触发方式、会话状态筛选）

**岗亭工作区（GB）**:
- GB-01: 实时监控面板（识别事件流 + 设备状态 + 在场车辆 + 告警）
- GB-02: 人工放行（手动开闸，含批量）
- GB-03: 手动关闸
- GB-04: 常开设置（二次确认 + 顶部常驻状态条）
- GB-05: 常关设置（二次确认 + 顶部常驻状态条）
- GB-06: 通行记录查询（复用 OP-04 同一组件）
- GB-07: 新事件提示音
- GB-08: 断线自动重连并恢复状态

---

## 四、核心业务规则（一期）

### 4.1 白名单自动放行（一期唯一自动放行场景）

```
白名单命中 → 自动开闸放行 → 写 parking_session (entry_trigger=whitelist_auto)
白名单未命中 → ENTRY_DENIED 不开闸 → 事件照常推送岗亭 → 等待人工放行
```

- VehicleTypeDecisionService 一期行为：白名单命中 → allowEntry=true, allowExit=true, needCharge=false
- 月卡/固定车位/黑名单分支保留代码但不启用
- decision 返回 DENY 时事件仍然推送到岗亭端展示

### 4.2 岗亭人工放行

```
岗亭管理员查看识别事件 → 确认放行 → 手动开闸
→ 写 parking_session (entry_trigger=manual_open, entry_operator=当前用户ID)
```

- GB-02 人工放行与 GAP-03 手动开闸补写会话是同一逻辑

### 4.3 车道闸机模式（gate_mode）

| 值 | 含义 | 行为 |
| :--- | :--- | :--- |
| `AUTO` | 自动（默认） | 白名单命中自动开闸，未命中需人工放行 |
| `ALWAYS_OPEN` | 常开 | 白名单命中不下发开闸（闸已常开），事件照常推送，可通过适配器 lockGate 锁定常开 |
| `ALWAYS_CLOSE` | 常关 | 白名单命中不下发开闸，事件照常推送，手动开闸仍可用 |

- 平台启动及设备重连后，按 gate_mode 重新同步：
  - ALWAYS_OPEN → 重发 lockGate
  - ALWAYS_CLOSE → unlockGate 取消常开后关闸
- 前端常开/常关操作需二次确认，顶部显示常驻状态条

### 4.4 重复识别去重

- **BR-08**: 同一车道同一车牌 30 秒内重复识别事件不重复建会话（事件仍推送展示）
- **BR-13**: 重复入场时旧 IN 会话标记 EXCEPTION（现有逻辑，保留不动）

### 4.5 超时会话自动关闭

- 每日定时任务：关闭超过 24 小时未出场的 IN 会话（status=EXCEPTION, remark="超时未出场自动关闭"）

---

## 五、数据结构（一期新增）

### 5.1 parking_lane 新增字段

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `gate_mode` | VARCHAR(20) | `AUTO` | 闸机模式：AUTO / ALWAYS_OPEN / ALWAYS_CLOSE |

### 5.2 parking_session 新增字段

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `entry_trigger` | VARCHAR(20) | NULL | 入场触发方式：whitelist_auto / manual_open / always_open_period / manual_entry |

> `entry_operator` 字段已存在（BIGINT），无需新增

---

## 六、GAP 差距项（逐项对照）

> ✅ 以下 GAP 均已实现并验收通过，详见 `docs/一期交付报告_V1.4_20260719.md`

### GAP-01: lockGate/unlockGate 同步落库 gate_mode
- lockGate/unlockGate 调用处同步更新 parking_lane.gate_mode
- ALWAYS_CLOSE 新增逻辑：车道为 ALWAYS_CLOSE 时白名单命中不下发开闸，事件照常推送，手动开闸仍可用

### GAP-02: 平台启动/设备重连后重新同步 gate_mode
- 平台启动时查询所有 gate_mode ≠ AUTO 的车道
- ALWAYS_OPEN → 重新下发 lockGate
- 设备重连（状态变为 ONLINE）时同样按 gate_mode 重同步

### GAP-03: 手动开闸补写 parking_session
- manualOpenGate 及 GB-02 人工放行 → 写 parking_session
- entry_trigger = `manual_open`
- entry_operator = TenantContext.userId()

### GAP-04: entry_trigger 字段
- T1 数据库迁移中已覆盖（parking_session 新增 entry_trigger）

### GAP-05: 设备状态轮询推送
- 新增 30 秒定时任务轮询适配器设备状态
- 状态变化时调用 BoothWebSocketPublisher.sendDeviceStatus
- 推送到 /topic/booth/{lotId}/device-status

### GAP-06: 超时会话自动关闭
- 新增定时任务，每日凌晨执行
- 关闭超过 24 小时未出场的 IN 会话
- status = EXCEPTION, remark = "超时未出场自动关闭"

### GAP-07: 删除 RabbitMQ 识别事件消费链路
- 物理删除 RecognitionEventConsumer 及仅被其使用的代码
- 删除相关 RabbitMQ 配置（queue、exchange、binding）
- 注意：Webhook 路径（DeviceWebhookController → RecognitionEventServiceImpl）是主链路，保留不动
- Git 可回溯，直接删

### GAP-08: 人工补录接口
- 新增接口：手动补录通行记录
- 写 parking_session（entry_trigger = `manual_entry`，记录操作人）

---

## 七、统一 Web 应用（booth-web 升级）

### 7.1 整体架构
- 一个 Web 应用，登录后按角色渲染三个区域
- 统一 Layout（UnifiedLayout.vue）：左侧菜单按角色过滤 + 顶栏（系统名/当前用户/退出）
- 路由：/admin（平台管理区）/ /operation（车场运营区）/ /booth（岗亭工作区）
- 路由守卫拦截越权，后端接口角色鉴权

**菜单结构（V1.5）**:

| 菜单 | 路由 | platform | tenant | booth |
| :--- | :--- | :---: | :---: | :---: |
| 运营数据 | /operation/analytics | ✅ | ✅ | — |
| 车场管理 › 车场信息 | /admin/parking | ✅ | ✅ | — |
| 车场管理 › 固定车管理 | /operation/vehicles | ✅ | ✅ | — |
| 账号管理 | /admin/accounts | ✅ | — | — |
| 记录查询 › 通行记录 | /operation/access-records | ✅ | ✅ | — |
| 岗亭工作区 | /booth/monitor | ✅ | ✅ | ✅ |

- /operation/dashboard（运营概览）不进菜单；路由保留注册
- booth 单角色时侧边栏隐藏，岗亭页全屏
- 租户管理员在车场信息页仅查看/编辑（新增/删除按钮仅超管可见）

### 7.2 岗亭工作区
- 代码分割懒加载
- 全屏常驻大按钮布局
- 新事件提示音
- 常开/常关二次确认弹窗 + 顶部常驻状态条（显示当前车道 gate_mode）
- 断线自动重连并恢复 WebSocket 订阅状态

### 7.3 账号体系
- 超管创建账号时：绑定角色、绑定归属停车场
- 岗亭管理员额外绑定归属车场管理员（归属关系）
- 登录 token 存 sessionStorage，关闭页面即失效，每次打开重新登录

---

## 八、一期数据库迁移（T1）

遵循现有 Flyway 命名规范 `V{YYYYMMDD}{NNN}__{描述}.sql`，当前最新为 V20260902014。

1. **parking_lane 新增 gate_mode**:
   ```sql
   ALTER TABLE parking_lane ADD COLUMN gate_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO'
   COMMENT '闸机模式: AUTO/ALWAYS_OPEN/ALWAYS_CLOSE';
   ```

2. **parking_session 新增 entry_trigger**:
   ```sql
   ALTER TABLE parking_session ADD COLUMN entry_trigger VARCHAR(20) NULL
   COMMENT '入场触发方式: whitelist_auto/manual_open/always_open_period/manual_entry';
   ```

---

## 九、验收标准（AC-01 ~ AC-22）

> ✅ 以下验收项全部通过，详见 `docs/一期交付报告_V1.4_20260719.md` 和 `docs/一期全量验收测试报告_20260719.md`

| 编号 | 验收项 | 验证方式 |
| :--- | :--- | :--- |
| AC-01 | 白名单车辆入场自动开闸，写会话 entry_trigger=whitelist_auto | Mock webhook |
| AC-02 | 非白名单车辆入场不开闸，事件推送岗亭 | Mock webhook |
| AC-03 | 岗亭人工放行开闸，写会话 entry_trigger=manual_open | 接口测试 |
| AC-04 | 常开模式：lockGate 调用成功，gate_mode 落库 | 接口测试 |
| AC-05 | 常关模式：白名单不自动开闸，手动开闸仍可用 | Mock webhook + 接口 |
| AC-06 | 平台启动重同步 gate_mode | 启动验证 |
| AC-07 | 30s 设备状态轮询 + WebSocket 推送 | 日志验证 |
| AC-08 | 超时 24h 会话自动关闭 | 定时任务触发验证 |
| AC-09 | RabbitMQ 消费链路已完全删除 | grep 验证 |
| AC-10 | 人工补录接口可用 | 接口测试 |
| AC-11 | 统一 Web 应用按角色渲染三区域 | 前端验证 |
| AC-12 | 前端路由守卫 + 后端角色鉴权 | 越权测试 |
| AC-13 | 固定车 Excel 批量导入（格式校验+重复校验+失败明细） | 功能测试 |
| AC-14 | `/admin/parking` 页面左侧车场列表，右侧三 Tab 可用 | 前端验证 |
| AC-15 | 创建车道时可同时配置入口/出口相机设备（含网络字段） | 功能测试 |
| AC-16 | 双向车道同时展示入口+出口两组相机表单 | 功能测试 |
| AC-17 | 设备表单包含 IP/端口/子网掩码/网关字段 | 功能测试 |
| AC-18 | 旧三页面路由（/admin/parking-lots/lanes/devices）已删除 | 前端验证 |
| AC-19 | 运营数据页车流量 KPI 卡片数据正确 | 接口测试 |
| AC-20 | 车流量折线图/入场方式饼图按时间筛选正确渲染 | 前端验证 |
| AC-21 | 超管可选全部车场，租户管理员仅可见被分配车场 | 权限测试 |
| AC-22 | 营收卡片显示「即将上线」 | 前端验证 |

---

## 十、硬性约束

1. **文档优先**: 本文档与代码冲突时以本文档为准
2. **一期范围**: 只做上述功能清单 + GAP-01~GAP-08；黑名单/月卡/临时车分支保留代码但不启用
3. **鑫路通（Xinlutong）代码保留不动**；parking_record / parking_order 表保留不删（仅停止写入）
4. **不修改构建脚本与 CI 配置**；不引入新的核心框架或中间件
5. **每个任务完成后工程必须可编译可启动**；commit message 标注对应 GAP/AC 编号
6. **device-access 适配器一期原则零改动**；GAP 均在平台侧；如需改动必须先说明原因并征得确认
7. **该删的删、该改的改**，但禁止过度设计和无关重构

---

## 十一、项目现状（代码盘点结论，可信赖）

### 关键文件
| 文件 | 路径 | 作用 |
| :--- | :--- | :--- |
| DeviceWebhookController | `parking-system/.../device/webhook/DeviceWebhookController.java` | 识别事件入口 POST /api/v1/device-webhook/events |
| RecognitionEventServiceImpl | `parking-system/.../booth/service/impl/RecognitionEventServiceImpl.java` | 识别事件处理（入场/出场/手动操作） |
| VehicleTypeDecisionServiceImpl | `parking-system/.../vehicle/service/impl/VehicleTypeDecisionServiceImpl.java` | 车辆类型决策链（白名单最高优先级） |
| ParkingSessionService | `parking-system/.../` | 停车会话管理（parking_session 表） |
| DeviceAccessClient | `parking-system/.../client/DeviceAccessClient.java` | 闸机 HTTP REST 客户端（openGate/closeGate/lockGate/unlockGate） |
| BoothWebSocketPublisher | `parking-system/.../ws/BoothWebSocketPublisher.java` | STOMP 推送 /topic/booth/{lotId}/* |
| DeviceController | `device-access/.../api/DeviceController.java` | 适配器 REST API（/api/v1/devices/{id}/gate/*） |

### 通信
- 平台 → 适配器: HTTP REST 内网回环（DeviceAccessClient）
- 适配器 → 平台: HTTP Webhook 内网回环（DeviceWebhookController，HMAC-SHA256 签名）
- 适配器 ↔ 设备: MQTT 3.1.1（EMQX 云端），臻识上行 device/{sn}/message/up/#
- 平台 → 岗亭端: WebSocket STOMP（BoothWebSocketPublisher）
- 臻识 keep_alive 心跳 90 秒超时判离线

### 适配器
- 下行: gpio_out 脉冲开闸、set_io_lock_status 常开锁定（lockGate=常开，unlockGate=取消常开）
- 一期仅支持臻识 C5

### 前端 booth-web
- Vue 3 + Ant Design Vue 4 + Pinia + @stomp/stompjs 7
- token 存 sessionStorage，路由守卫
- 目前包含 Monitor 单页（通行监控 + 车辆查询两子 Tab），无独立交接班页面
- 已有常开/常关按钮 + ManualReleaseModal + ChargePanel + 常驻状态条 + 提示音
- WebSocket 5 个 topic 已订阅

---

## 十二、技术栈与规范

- Java 21 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Flyway
- Vue 3 + Ant Design Vue + 微信小程序原生
- RabbitMQ + Redis
- 金额用 BigDecimal（DECIMAL 10,2），**禁止 float/double**
- 时间用 LocalDateTime，**禁止 java.util.Date**
- Controller 必须 @RequirePermission，敏感操作加 @BusinessLog
- 接口返回 {code, message, data, timestamp}
- 实体类含 tenant_id, created_at, updated_at, deleted_at
- Commit 格式: `[GAP-xx] feat/fix: 描述` 或 `[AC-xx] 类型: 描述`

## 构建命令
```bash
mvn clean compile -pl parking-system -am   # 编译
mvn test -pl parking-boot -am              # 测试
mvn clean package -pl parking-boot -am     # 打包
```

## 安全红线
- 禁止生产环境加载 InternalGateController（@Profile("dev")）
- 禁止 pyun.mock=false
- 禁止 Controller 中 SQL 拼接
- Webhook 请求必须 HMAC-SHA256 签名验证
- 登录: 5次失败锁定15分钟，首次登录强制改密
- JWT: 访问令牌 2h，刷新令牌 7d
- 禁止前端传入 deviceSn 直接操作设备，必须通过 laneId 查数据库
