# 黑白名单独立实体 — 设计方案

> 任务包 3-3 | 需求依据：V1.1 3.1.8（ADMIN-008）、7.4-3/4、确认项 11/32

## 1. 背景与现状

### 1.1 当前实现
- **黑名单**：散装在 `AccessPolicy` 键值策略（`policy_type=BLACKLIST`）+ `sys_vehicle.vehicle_type=BLACKLIST`
- **白名单**：≈ `sys_vehicle.vehicle_type=VIP/SUPER/FREE`
- **无互斥**：同一车牌可同时存在于黑白名单两种口径中
- **无类型字典**：黑名单没有触发类型（欠费类/管理类/其他类）区分
- **无有效期**：名单永久生效，无过期机制
- **OverstayBlacklistScheduler**：超纲功能，自动拉黑超时停放车辆（配置默认开启）

### 1.2 改造目标
1. 新建 `vehicle_list` 独立实体，统一管理黑白名单
2. 互斥校验（同车场同车牌不可同时存在于黑白名单）
3. 黑名单触发类型字典（全局参数维护）
4. 进出场生效逻辑（白名单放行、黑名单按触发模式处理）
5. 存量数据迁移
6. 自动拉黑开关默认关闭，代码保留标注二期

## 2. 数据库设计

### 2.1 vehicle_list 表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `tenant_id` | BIGINT NOT NULL | 租户ID（多租户隔离） |
| `plate_number` | VARCHAR(20) NOT NULL | 车牌号（标准化大写） |
| `list_type` | VARCHAR(16) NOT NULL | 名单类型：BLACK / WHITE |
| `parking_lot_id` | BIGINT NOT NULL | 生效车场ID |
| `start_date` | DATE NULL | 有效期开始（NULL=立即生效） |
| `end_date` | DATE NULL | 有效期结束（NULL=永久） |
| `trigger_type` | VARCHAR(32) NULL | 黑名单触发类型：ARREARS/MANAGEMENT/OTHER（白名单为空） |
| `status` | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 状态：ACTIVE/EXPIRED/DISABLED |
| `remark` | VARCHAR(255) NULL | 备注 |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `updated_at` | DATETIME NOT NULL | 更新时间 |
| `deleted_at` | DATETIME NULL | 软删除 |

### 2.2 索引
- `uk_lot_plate_type`: UNIQUE (`parking_lot_id`, `plate_number`, `list_type`) — 同车场同车牌同名单类型唯一
- `idx_plate_number`: 普通索引，加速车牌查询（缓存回填）
- `idx_parking_lot_id`: 普通索引
- `idx_list_type_status`: 复合索引 (`list_type`, `status`)，定时到期扫描用

### 2.3 互斥规则
同车场同车牌不可同时存在于黑名单和白名单。互斥校验在应用层（Service）实现，保存时拦截并提示"该车牌已存在于X名单中，无法同时添加为Y名单"。

### 2.4 全局参数（sys_config）
- `blacklist.trigger_mode`：黑名单触发模式
  - `DENY_ENTRY` — 禁止入场（默认）
  - `ALLOW_WITH_ALERT` — 允许入场但告警
  - `BY_TYPE` — 按类型区分
- `blacklist.trigger_types`：触发类型字典（JSON），维护下拉选项
  - `[{"code":"ARREARS","label":"欠费类"},{"code":"MANAGEMENT","label":"管理类"},{"code":"OTHER","label":"其他类"}]`

## 3. 服务层设计

### 3.1 VehicleListService
- **create(VehicleListCreateCmd)**: 互斥校验 → 标准化车牌 → 插入
- **update(id, cmd)**: 互斥校验 → 更新 → 缓存失效
- **delete(id)**: 软删除 → 缓存失效
- **pageList(page, lotId, listType, plateNumber)**: 分页查询，供运营端名单页
- **isBlacklisted(lotId, plateNumber)**: 缓存查询，识别链路专用（< 50ms）
- **isWhitelisted(lotId, plateNumber)**: 缓存查询，识别链路专用
- **resolveBlacklistRule(lotId, plateNumber)**: 返回完整黑名单信息（含 triggerType），供黑名单处理

### 3.2 定时任务
每天凌晨 2:00 扫描 `end_date < now()` 且 `status = ACTIVE` 的记录，置为 `EXPIRED`，并失效对应缓存。

### 3.3 缓存策略
- 缓存 Key: `vehicle_list:{lotId}:{plateNumber}`
- 缓存 Value: JSON `{listType, triggerType, endDate, status}`
- 写入/更新/删除时精准失效对应 Key
- 定时到期任务批量失效
- 缓存未命中时查 DB 并回填（TTL 建议 30 分钟）
- 互斥校验不依赖缓存，直接查 DB 保证准确

## 4. VehicleTypeDecisionService 改造

### 4.1 新优先级链

```
1. vehicle_list 白名单
   → allowEntry=true, needCharge=false, autoRelease（仅通行记录，不生成订单）
   → 命中即返回，不继续往下走

2. vehicle_list 黑名单
   → 读取 blacklist.trigger_mode:
     - DENY_ENTRY       → allowEntry=false（禁止入场）
     - ALLOW_WITH_ALERT → allowEntry=true + 触发 MonitorAlert（TYPE_BLACKLIST）+ WS 推送
     - BY_TYPE          → 欠费类(ARREARS): allowEntry=false
                           管理类(MANAGEMENT): allowEntry=true + 告警
                           其他类(OTHER): 按 DENY_ENTRY 处理（保守默认）
   → 命中即返回

3. MonthlyPass（月卡有效期）→ allowEntry=true, needCharge=false
4. SysVehicle（SUPER / VIP / PREPAID / FREE）...（保留但不作为白/黑名单主口径）
5. FIXED_SPACE 固定车位 → allowEntry=true, needCharge=false
6. TEMP 临时车（默认）
```

### 4.2 旧口径下线
- `SysVehicle.TYPE_BLACKLIST` 和 `TYPE_VIP` 常量保留不删除（存量数据可能有残留引用）
- 判定逻辑中不再读取 `vehicle_type=BLACKLIST/VIP` 作为黑白名单依据
- `AccessPolicy` 中 `policy_type=BLACKLIST` / `VIP` 策略不再参与名单判定

## 5. EntryService / ExitService 联动

### 5.1 入场链路
- `handleEntry()` 中调用 `VehicleTypeDecisionService.decide()`
- 白名单：创建通行记录 + 开闸，不生成预订单
- 黑名单 DENY_ENTRY：拒绝入场，记录异常（ExceptionRecord）
- 黑名单 ALLOW_WITH_ALERT / BY_TYPE 管理类：允许入场 + MonitorAlert + WS 推送

### 5.2 出场链路
- `handleExit()` 中调用 `VehicleTypeDecisionService.decide()`
- 白名单：自动放行不计费
- 黑名单：同入场逻辑（按 trigger_mode 决定拦截或告警）

### 5.3 告警复用
- 告警类型：新增 `MonitorAlert.TYPE_BLACKLIST_ENTRY = "BLACKLIST_ENTRY"`
- 告警内容：`"黑名单车辆入场: 车牌XX, 触发类型: 欠费类"`
- 通过 BoothWebSocketPublisher 推送到岗亭端

## 6. 存量数据迁移

### 6.1 Flyway 脚本 V{YYYYMMDD}__migrate_vehicle_list.sql

```sql
-- 1. 迁移 AccessPolicy BLACKLIST 策略 → vehicle_list
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT tenant_id, policy_key, 'BLACK', parking_lot_id,
    NULL, NULL, 'OTHER', 'ACTIVE',
    CONCAT('迁移自access_policy: ', COALESCE(description, '')),
    NOW(), NOW()
FROM access_policy
WHERE policy_type = 'BLACKLIST' AND status = 'ACTIVE' AND deleted_at IS NULL;

-- 2. 迁移 sys_vehicle BLACKLIST → vehicle_list
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT tenant_id, plate_number, 'BLACK', parking_lot_id,
    valid_start_date, valid_end_date, 'OTHER', 'ACTIVE',
    CONCAT('迁移自sys_vehicle BLACKLIST: ', COALESCE(remark, '')),
    created_at, NOW()
FROM sys_vehicle
WHERE vehicle_type = 'BLACKLIST' AND status = 'ACTIVE' AND deleted_at IS NULL;

-- 3. 迁移 sys_vehicle VIP/SUPER/FREE（白名单） → vehicle_list
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT tenant_id, plate_number, 'WHITE', parking_lot_id,
    valid_start_date, valid_end_date, NULL, 'ACTIVE',
    CONCAT('迁移自sys_vehicle ', vehicle_type, ': ', COALESCE(remark, '')),
    created_at, NOW()
FROM sys_vehicle
WHERE vehicle_type IN ('VIP', 'SUPER', 'FREE') AND status = 'ACTIVE' AND deleted_at IS NULL;
```

## 7. OverstayBlacklistScheduler 处理

- `jushan.overstay-blacklist.enabled` 默认值从 `true` 改为 `false`
- 代码保留不变，添加注释标注二期评估
- Scheduler 类添加 `@ConditionalOnProperty(name = "jushan.overstay-blacklist.enabled", havingValue = "true")`

## 8. 运营端名单页改造

### 8.1 后端接口
- `POST /api/v1/admin/vehicle-list` — 创建名单
- `PUT /api/v1/admin/vehicle-list/{id}` — 编辑名单
- `DELETE /api/v1/admin/vehicle-list/{id}` — 删除名单
- `GET /api/v1/admin/vehicle-list/page` — 分页查询
- `GET /api/v1/admin/vehicle-list/{id}` — 详情

### 8.2 前端改造（admin-web）
- 替换旧的 AccessPolicy 策略页（或新增 Tab），对接新接口
- 表单字段：
  - 车牌号（文本，自动大写）
  - 名单类型（下拉：黑名单 / 白名单）
  - 生效车场（下拉，租户授权车场范围）
  - 有效期范围（DatePicker：开始日期 / 结束日期，结束日期留空=永久）
  - 黑名单触发类型（下拉，仅 list_type=BLACK 时显示，读 sys_config 字典）
  - 备注（文本域）
- 保存时后端返回互斥错误 → 前端 toast 提示具体冲突

## 9. 验收标准

1. **互斥校验**：同车场同车牌在黑名单→再添加白名单被拦截，提示明确
2. **三种触发模式**：
   - DENY_ENTRY：黑名单车辆入场被拒
   - ALLOW_WITH_ALERT：黑名单车辆允许入场，岗亭收到告警
   - BY_TYPE：欠费类禁止入场，管理类允许入场+告警
3. **白名单自动放行**：不生成订单，仅创建通行记录
4. **缓存命中率**：识别主链路名单查询 < 50ms
5. **存量数据迁移**：Flyway 脚本执行后数据完整
6. **自动拉黑开关**：默认关闭，配置可切换
7. **有效期到期**：到期后自动置 EXPIRED，车辆按临时车处理
