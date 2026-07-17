# 月卡独立实体设计规格说明书（任务包 3-1）

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| **文档名称** | 月卡独立实体设计规格说明书 |
| **版本号** | V1.0 |
| **编写日期** | 2026-07-17 |
| **对应任务包** | 3-1：月卡独立实体——全生命周期 + 月卡订单 |
| **需求依据** | 需求规格说明书 V1.2 §3.1.6（ADMIN-006）、§3.3.5（MINI-005）、§7.4-1、确认项 26/42 |
| **设计决策** | D4：月卡重建独立实体 |

---

## 1. 背景与动机

### 1.1 现状问题

当前"月卡"并非独立实体，而是 `sys_vehicle` 表上 `vehicleType=MONTHLY` 的标签化实现：

```
现状架构：
  MonthlyPassController ──→ MonthlyPassService ──→ sys_vehicle (vehicleType=MONTHLY)
                                                      │
                                                      └── valid_start_date / valid_end_date
  VehicleTypeDecisionService ──→ sys_vehicle.vehicleType=MONTHLY? ──→ 判断有效期 ──→ 是否放行
```

**问题**：
1. **无独立生命周期**：月卡办理/续期/注销/过期全部跟 `sys_vehicle` 的行状态耦合，与黑名单、VIP、免费车等混在同表
2. **无缴费登记**：运营端录入月卡后，无法记录缴费方式（现金/转账/模拟支付）和实收金额
3. **无月卡订单**：`parking_order` 仅有 `MONTH_RENEW`（续费订单），缺少首次办理订单类型
4. **无审核流**：V1.1 需求要求的小程序申请→审核→支付链路无法基于标签实现
5. **数据迁移风险**：`sys_vehicle` 承载了过多职责，字段越来越多但大量在特定类型下无意义

### 1.2 目标

新建 `monthly_pass` 独立表，管理月卡全生命周期，与 `sys_vehicle` 解耦。进出场判定链路改造为读取 `monthly_pass` 有效期，替代 `vehicleType=MONTHLY` 标签判断。

---

## 2. 设计决策汇总

| # | 决策点 | 结论 | 理由 |
|---|--------|------|------|
| 1 | 表间关系 | `monthly_pass` 完全独立，不依赖 `sys_vehicle` | 职责分离，旧标签判断下线 |
| 2 | 运营端创建流程 | 创建即生成已支付订单，一步完成 | 减少操作步骤，运营端已确认收款 |
| 3 | 审核流 | **完全去掉**，不设 `review_status` 和 `review_mode` | 简化；所有来源支付即生效 |
| 4 | 缴费方式 | `CASH` / `OFFLINE_TRANSFER` / `SIMULATED_PAY` / `OTHER` | 独立枚举，映射到订单 `payChannel` |
| 5 | 存量迁移 | Flyway 复制→`monthly_pass`，原记录→`FREE`+`EXPIRED` | 不丢数据，旧路径安全下线 |
| 6 | 进出场判定 | 仅改 `VehicleTypeDecisionServiceImpl.decide()`，在 sys_vehicle 查询之前先查 `monthly_pass` | 入口最小化，`EntryService` 无需改动 |
| 7 | 月卡订单类型 | 新增 `MONTHLY_PASS`（首次办理），区分 `MONTH_RENEW`（续费） | 语义清晰，报表可区分 |
| 8 | 唯一性校验 | 同车场+同车牌+`pass_status=ACTIVE` 时拒绝创建 | 应用层校验（数据库唯一索引兜底） |

---

## 3. 数据模型

### 3.1 新建表：`monthly_pass`

```sql
CREATE TABLE monthly_pass (
    id               BIGINT          NOT NULL PRIMARY KEY COMMENT '主键（Snowflake）',
    tenant_id        BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id   BIGINT          NOT NULL COMMENT '车场ID',
    plate_number     VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    plate_color      VARCHAR(10)     DEFAULT NULL COMMENT '车牌颜色',
    vehicle_type     VARCHAR(20)     DEFAULT NULL COMMENT '车辆类型（小型车/大型车等）',
    valid_start_date DATE            NOT NULL COMMENT '有效期起',
    valid_end_date   DATE            NOT NULL COMMENT '有效期止',
    amount_cents     INT             NOT NULL DEFAULT 0 COMMENT '费用（分）',
    paid_amount_cents INT            NOT NULL DEFAULT 0 COMMENT '实收金额（分）',
    pay_method       VARCHAR(20)     NOT NULL COMMENT '缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER',
    pass_status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '月卡状态：ACTIVE-生效中 / EXPIRED-已过期 / CANCELLED-已注销',
    applicant_id     BIGINT          DEFAULT NULL COMMENT '申请人ID（小程序用户ID；运营端录入为NULL）',
    source           VARCHAR(20)     NOT NULL COMMENT '来源：ADMIN-运营端 / MINIAPP-小程序端',
    owner_name       VARCHAR(30)     DEFAULT NULL COMMENT '车主姓名',
    owner_phone      VARCHAR(20)     DEFAULT NULL COMMENT '车主电话',
    remark           VARCHAR(200)    DEFAULT NULL COMMENT '备注',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at       DATETIME(3)     DEFAULT NULL COMMENT '软删除时间',
    
    INDEX idx_tenant     (tenant_id),
    INDEX idx_plate      (plate_number),
    INDEX idx_lot        (parking_lot_id),
    INDEX idx_status     (pass_status),
    INDEX idx_lot_plate  (parking_lot_id, plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月卡独立实体表';
```

### 3.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | BIGINT | ✓ | Snowflake 主键 |
| `tenant_id` | BIGINT | ✓ | 租户ID，MyBatis-Plus 拦截器自动过滤 |
| `parking_lot_id` | BIGINT | ✓ | 所属车场 |
| `plate_number` | VARCHAR(20) | ✓ | 标准化大写车牌号 |
| `plate_color` | VARCHAR(10) | | 车牌颜色（蓝/绿/黄等） |
| `vehicle_type` | VARCHAR(20) | | 车辆类型描述（小型车/大型车） |
| `valid_start_date` | DATE | ✓ | 有效期开始 |
| `valid_end_date` | DATE | ✓ | 有效期结束 |
| `amount_cents` | INT | ✓ | 费用（分），默认 0 |
| `paid_amount_cents` | INT | ✓ | 实收金额（分），默认 0 |
| `pay_method` | VARCHAR(20) | ✓ | CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER |
| `pass_status` | VARCHAR(20) | ✓ | ACTIVE / EXPIRED / CANCELLED |
| `applicant_id` | BIGINT | | 小程序用户ID（运营端录入为 NULL） |
| `source` | VARCHAR(20) | ✓ | ADMIN / MINIAPP |
| `owner_name` | VARCHAR(30) | | 车主姓名 |
| `owner_phone` | VARCHAR(20) | | 车主电话 |
| `remark` | VARCHAR(200) | | 备注 |

### 3.3 唯一性约束

数据库层：`uk_lot_plate` 索引（`parking_lot_id, plate_number`）提供兜底。

业务层：创建前执行查询校验：

```sql
SELECT COUNT(*) FROM monthly_pass
WHERE parking_lot_id = ? 
  AND plate_number = ? 
  AND pass_status = 'ACTIVE' 
  AND deleted_at IS NULL
```

> 不建立 `WHERE pass_status='ACTIVE'` 的条件唯一索引，因为 MySQL 不支持部分唯一索引（partial unique index）。
> 应用层保证同一车场+同一车牌仅允许一条生效中记录。

### 3.4 月卡状态机

```
         create
           │
           ▼
       ┌───────┐     定时任务(每日凌晨2点)      ┌────────┐
       │ ACTIVE │ ────────────────────────────→ │EXPIRED │
       └───┬───┘    valid_end_date < TODAY       └────────┘
           │
           │ 手动注销
           ▼
       ┌──────────┐
       │CANCELLED │
       └──────────┘
```

- 运营端 `POST /`：创建即 `ACTIVE`（生成已支付订单）
- 小程序端（任务包 5-2）：支付成功后 `ACTIVE`
- 定时任务：`valid_end_date < CURDATE()` → `EXPIRED`
- 手动注销：`PUT /{id}/cancel` → `CANCELLED`
- 过期/注销后再次办理：创建新记录，旧记录保持其最终状态

### 3.5 现有表改动

#### 3.5.1 `vehicle_renewal_log` 新增列

```sql
ALTER TABLE vehicle_renewal_log
    ADD COLUMN monthly_pass_id BIGINT DEFAULT NULL COMMENT '月卡ID（新体系）' AFTER vehicle_id;
```

`vehicle_id` 保留用于兼容历史数据。

#### 3.5.2 `parking_order` 新增常量

```java
// ParkingOrder.java
public static final String ORDER_TYPE_MONTHLY_PASS = "MONTHLY_PASS";
```

```sql
-- 无需 DDL 变更，orderType 已是 VARCHAR
```

---

## 4. API 设计

### 4.1 端点总览

所有接口位于 `@RequestMapping("/api/v1/monthly-passes")`，权限 `@RequirePermission("monthly:manage")`。

| 方法 | 路径 | 说明 | 事务 |
|:-----|:-----|:-----|:----:|
| `POST` | `/` | 录入月卡 + 生成已支付订单 | ✓ |
| `PUT` | `/{id}/renew` | 续期 + 生成续费订单 | ✓ |
| `PUT` | `/{id}/cancel` | 注销 | ✓ |
| `GET` | `/` | 分页列表（含筛选） | |
| `GET` | `/expiring` | 到期预警列表 | |
| `GET` | `/{id}` | 详情 | |

### 4.2 录入月卡

**`POST /api/v1/monthly-passes`**

请求体 `MonthlyPassCreateRequest`：

```java
public class MonthlyPassCreateRequest {
    @NotBlank  String plateNumber;       // 车牌号（必填）
               String plateColor;         // 车牌颜色
               String vehicleType;        // 车辆类型（选填）
    @NotNull   Long parkingLotId;         // 车场ID（必填）
    @NotNull   LocalDate validStartDate;  // 有效期起（必填）
    @NotNull   LocalDate validEndDate;    // 有效期止（必填）
               Integer amountCents;       // 费用（分）
    @NotNull   Integer paidAmountCents;   // 实收金额（分，必填）
    @NotBlank  String payMethod;          // 缴费方式（必填：CASH/OFFLINE_TRANSFER/OTHER）
               String ownerName;          // 车主姓名
               String ownerPhone;         // 车主电话
               String remark;             // 备注
}
```

处理流程（`@Transactional`）：

```
1. 标准化车牌号 → toUpperCase()
2. 校验唯一性：同车场+同车牌+pass_status=ACTIVE → 拒绝
3. 插入 monthly_pass 记录：
     - source = "ADMIN"
     - pass_status = "ACTIVE"
     - applicant_id = null
4. 生成 parking_order 订单：
     - orderType = "MONTHLY_PASS"
     - refId = monthly_pass.id
     - status = "PAID"
     - payChannel = mapPayMethod(payMethod)  // CASH→CASH, OFFLINE_TRANSFER→BALANCE, OTHER→BALANCE
     - amountCents / paidAmount = paidAmountCents
     - payTime = NOW()
5. 返回 MonthlyPassVO（含 orderId）
```

### 4.3 续期

**`PUT /api/v1/monthly-passes/{id}/renew`**

请求体 `MonthlyPassRenewRequest`：

```java
public class MonthlyPassRenewRequest {
    @Min(1) @NotNull Integer renewalMonths;  // 续费月数
    @NotNull Integer amountCents;            // 金额（分）
             String remark;                  // 备注
}
```

处理流程：

```
1. 查询 monthly_pass（校验存在+租户权限）
2. 计算新有效期：newValidEnd = validEndDate.plusMonths(renewalMonths)
   （若 validEndDate 已过期，从过期日起算）
3. 更新 monthly_pass.valid_end_date = newValidEnd
4. 创建 MONTH_RENEW 订单（status=PAID，立即生效）
5. 写入 vehicle_renewal_log（monthly_pass_id + 续费前后有效期）
6. 返回更新后的 MonthlyPassVO
```

### 4.4 注销

**`PUT /api/v1/monthly-passes/{id}/cancel`**

处理流程：

```
1. 查询 monthly_pass（校验存在+租户权限）
2. pass_status → CANCELLED
3. 返回更新后的 MonthlyPassVO
```

注销后该车牌下次 `VehicleTypeDecisionService.decide()` 查不到 ACTIVE 月卡 → 按临时车收费。

### 4.5 列表查询

**`GET /api/v1/monthly-passes`**

查询参数：

| 参数 | 类型 | 说明 |
|------|------|------|
| `plateNumber` | String | 车牌号（模糊匹配） |
| `parkingLotId` | Long | 车场ID |
| `passStatus` | String | 月卡状态（ACTIVE/EXPIRED/CANCELLED） |
| `validEndFrom` | LocalDate | 有效期起 |
| `validEndTo` | LocalDate | 有效期止 |
| `page` | int | 页码（默认1） |
| `size` | int | 每页大小（默认20） |

返回 `MonthlyPassVO` 列表，含车场名称（批量查询）。

### 4.6 到期预警

**`GET /api/v1/monthly-passes/expiring`**

```
SELECT * FROM monthly_pass
WHERE pass_status = 'ACTIVE'
  AND valid_end_date BETWEEN CURDATE() AND CURDATE() + INTERVAL ? DAY
ORDER BY valid_end_date ASC
```

`days` 参数从车场参数 `monthly_pass.expiry_reminder_days` 读取（`ParamResolver`，默认 7）。

### 4.7 `MonthlyPassVO` 结构

```java
public class MonthlyPassVO {
    Long id;
    String plateNumber;
    String plateColor;
    String vehicleType;
    Long parkingLotId;
    String parkingLotName;
    LocalDate validStartDate;
    LocalDate validEndDate;
    Integer amountCents;
    Integer paidAmountCents;
    String payMethod;
    String passStatus;        // ACTIVE / EXPIRED / CANCELLED
    String source;            // ADMIN / MINIAPP
    Long applicantId;
    Long orderId;             // 关联首次办理订单ID
    String ownerName;
    String ownerPhone;
    String remark;
    LocalDateTime createdAt;
}
```

---

## 5. 进出场判定改造

### 5.1 改造范围

在 `VehicleTypeDecisionServiceImpl.decide()` 方法外层，`sys_vehicle` 查询之前插入 `monthly_pass` 查询。同时从 `applyPriorityChain()` 中移除 `case SysVehicle.TYPE_MONTHLY` 分支。

### 5.2 改造后流程

```
VehicleTypeDecisionServiceImpl.decide(plateNumber, tenantId)
  │
  ├── 1. 标准化车牌
  ├── 2. ⭐ 新增：优先查询 monthly_pass（在 sys_vehicle 查询之前）
  │         SELECT * FROM monthly_pass
  │         WHERE tenant_id = ? AND plate_number = ?
  │           AND pass_status = 'ACTIVE'
  │           AND valid_start_date <= CURDATE()
  │           AND valid_end_date >= CURDATE()
  │           AND deleted_at IS NULL
  │
  ├── 3. ⭐ 若 monthly_pass 命中 → 直接返回 MONTHLY 判定：
  │         - vehicleType = "MONTHLY"
  │         - typeDescription = "月租车"
  │         - allowEntry = true
  │         - allowExit = true
  │         - needCharge = false
  │         - decisionReason = "月卡在有效期内，免费通行"
  │         （短路返回，不再走 sys_vehicle 查询）
  │
  ├── 4. monthly_pass 未命中 → 查询 sys_vehicle（main plate → multi-plate fallback）
  ├── 5. 若 sys_vehicle 未找到 → 直接返回 TEMP
  └── 6. sys_vehicle 找到 → 走原有优先级链条
             ⚠ 移除 case SysVehicle.TYPE_MONTHLY 分支
             ⚠ chain: BLACKLIST → SUPER → VIP → PREPAID → FIXED_SPACE → FREE → TEMP
```

### 5.3 注入依赖

`VehicleTypeDecisionServiceImpl` 新增 `MonthlyPassMapper` 依赖：

```java
private final MonthlyPassMapper monthlyPassMapper;

public VehicleTypeDecisionServiceImpl(SysVehicleMapper vehicleMapper,
        SysVehicleMultiPlateMapper multiPlateMapper,
        SysVehicleWalletMapper walletMapper,
        FixedSpaceService fixedSpaceService,
        MonthlyPassMapper monthlyPassMapper) {  // 新增
    // ...
    this.monthlyPassMapper = monthlyPassMapper;
}
```

### 5.4 无需改动的下游服务

- **`EntryService.createPreOrderIfChargeable()`**：读 `decision.needCharge`，月卡返回 `false` → 不生成预订单 ✓
- **`ExitService`**：出场判定读 `decision`，月卡返回 `allowExit=true` → 自动放行 ✓
- **`RecognitionEventServiceImpl`**：岗亭端判定读 `decision` ✓
- **`OverstayBlacklistService`**：超时黑名单处理，读取车辆类型过滤 ✓（需确认 MONTHLY 常量引用）

### 5.5 优先级语义

`monthly_pass` 查询在 `sys_vehicle` 查询**之前**执行，命中即短路返回——因此月卡判定优先级**高于所有** `sys_vehicle` 类型（包括黑名单）。

> **设计理由**：月卡车辆已付费获得通行权，即使历史有欠费或 sys_vehicle 中有 BLACKLIST 标记，仍然应放行。若需要额外的黑名单拦截逻辑，应在月卡办理时做前置校验（不允许黑名单车辆办理月卡），而非在出场链路拦截。

---

## 6. 到期定时任务

### 6.1 实现

```java
@Component
public class MonthlyPassExpiryJob {
    
    private final MonthlyPassMapper monthlyPassMapper;
    
    @Scheduled(cron = "0 0 2 * * ?")  // 每日凌晨 2:00
    @Transactional
    public void expireMonthlyPasses() {
        int updated = monthlyPassMapper.expireActivePasses(LocalDate.now());
        log.info("月卡到期处理完成: 更新 {} 条记录", updated);
    }
}
```

Mapper SQL：

```sql
UPDATE monthly_pass 
SET pass_status = 'EXPIRED', updated_at = NOW()
WHERE pass_status = 'ACTIVE' 
  AND valid_end_date < #{today}
```

### 6.2 过期后行为

过期操作仅改 `pass_status`。下次 `VehicleTypeDecisionService.decide()` 查询时：
- `pass_status='ACTIVE'` 条件不满足 → 月卡查询不命中
- 回退到 `sys_vehicle` 类型链 → 按该车辆原有类型（通常是 FREE）或 TEMP 处理
- 出场时由 `ExitService` 按临停计费

---

## 7. 存量数据迁移

### 7.1 Flyway 迁移脚本

文件名：`V{日期序号}__create_monthly_pass.sql`

```sql
-- =============================================================================
-- 任务包 3-1：月卡独立实体 — 建表 + 存量迁移
-- =============================================================================

-- Step 1: 创建 monthly_pass 表（DDL 见 §3.1）

-- Step 2: 存量数据迁移
INSERT INTO monthly_pass (
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    amount_cents, paid_amount_cents, pay_method,
    pass_status, applicant_id, source,
    owner_name, owner_phone, remark,
    created_at, updated_at
)
SELECT 
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    0, 0, 'OTHER',
    CASE 
        WHEN status = 'ACTIVE' AND valid_end_date >= CURDATE() THEN 'ACTIVE'
        ELSE 'EXPIRED'
    END,
    NULL, 'ADMIN',
    owner_name, owner_phone, remark,
    COALESCE(created_at, NOW()), NOW()
FROM sys_vehicle 
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 3: 改写原记录（旧判断口径下线）
UPDATE sys_vehicle 
SET vehicle_type = 'FREE', 
    status = 'EXPIRED', 
    updated_at = NOW()
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 4: vehicle_renewal_log 增加 monthly_pass_id
ALTER TABLE vehicle_renewal_log
    ADD COLUMN monthly_pass_id BIGINT DEFAULT NULL COMMENT '月卡ID（新体系）' AFTER vehicle_id;
```

### 7.2 迁移说明

- `pay_method = 'OTHER'`、`paid_amount_cents = 0`：历史数据不可考
- `pass_status`：根据当前有效期与状态判定
- 原 `sys_vehicle` 记录保留不删除（改为 FREE+EXPIRED），保证历史 `parking_record` 等关联引用不丢失
- 迁移脚本在 Flyway 事务中执行，失败整体回滚

---

## 8. 代码变更清单

### 8.1 新建文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `entity/MonthlyPass.java` | system | 实体类（extends BaseEntity） |
| `mapper/MonthlyPassMapper.java` | system | MyBatis-Plus Mapper |
| `job/MonthlyPassExpiryJob.java` | system | 到期定时任务 |
| `db/migration/V{seq}__create_monthly_pass.sql` | boot | Flyway 建表+迁移 |

### 8.2 修改文件

| 文件 | 改动内容 |
|------|----------|
| `MonthlyPassService.java` | 重写：基于 `monthly_pass` 表，新增创建+生成订单、列表查询改为查新表 |
| `MonthlyPassController.java` | 重写：接口实现改为调用新的 Service 方法，移除旧 facade 逻辑 |
| `MonthlyPassCreateRequest.java` | 新增：`payMethod`, `paidAmountCents`, `vehicleType` 字段 |
| `MonthlyPassVO.java` | 新增：`payMethod`, `paidAmountCents`, `source`, `applicantId`, `orderId`；移除无关字段 |
| `VehicleTypeDecisionServiceImpl.java` | 新增 `MonthlyPassMapper` 依赖；`applyPriorityChain()` 开头加 monthly_pass 查询；移除 `case TYPE_MONTHLY` 分支 |
| `ParkingOrder.java` | 新增常量 `ORDER_TYPE_MONTHLY_PASS = "MONTHLY_PASS"` |
| `VehicleRenewalLog.java` | 新增 `monthlyPassId` 字段 |

### 8.3 调整测试文件

| 文件 | 改动内容 |
|------|----------|
| `MonthlyPassServiceTest.java` | 重写：mock 改为 `MonthlyPassMapper`，不再依赖 `SysVehicleMapper` |
| `VehicleTypeDecisionServiceImplTest.java` | 新增：月卡判定用例（命中/未命中/过期）；移除或适配 MONTHLY 旧用例 |

---

## 9. 业务规则

### 9.1 唯一性

同一车场 + 同一车牌 + `pass_status=ACTIVE` 时，不允许创建新月卡。

### 9.2 续期

从当前 `valid_end_date` 向后顺延（即使已过期也从旧截止日顺延，保持周期一致）。

### 9.3 月卡订单

| 订单类型 | 触发场景 | orderType | refId |
|----------|----------|-----------|-------|
| 首次办理 | 运营端录入 / 小程序支付 | `MONTHLY_PASS` | `monthly_pass.id` |
| 续费 | 运营端续期 / 小程序续费 | `MONTH_RENEW` | `monthly_pass.id` |

### 9.4 月卡与固定车位

- 月卡不绑定车位号
- 月卡与固定车位过期管理分开（不同表、不同定时任务/视图）
- 进出场时两者独立判定：先查 `monthly_pass`（月卡），再查 `fixed_space`（固定车位绑定）

### 9.5 月卡与黑名单

月卡判定优先级高于 `sys_vehicle` 类型链（包括黑名单）。场景：用户欠费被加入黑名单，但已办理月卡，月卡应放行。

---

## 10. 本期不做（非范围）

| 项目 | 归属 |
|------|------|
| 小程序端月卡申请/支付页面 | 任务包 5-2 |
| 小程序端月卡续费 | 任务包 5-2 |
| 设备白名单同步（月卡信息同步至相机） | 任务包 5-2 |
| 支付成功通知推送 | 二期 |
| 月卡到期短信/微信推送 | 二期 |
| 月卡优惠券/折扣 | 二期 |

---

## 11. 验收标准

| # | 标准 | 验证方式 |
|---|------|----------|
| 1 | 运营端录入月卡：一次请求完成创建+生成已支付订单 | 接口测试 |
| 2 | 续期：有效期正确顺延，续费订单+日志完整 | 接口测试 |
| 3 | 注销：状态置为 CANCELLED，后续不再放行 | 接口测试 |
| 4 | 重复办理拦截：同车场+同车牌+生效中被拒绝 | 接口测试 |
| 5 | 生效月卡车辆入场识别自动放行，不生成预订单 | 集成测试 |
| 6 | 过期月卡车辆按临停计费 | 集成测试 |
| 7 | 定时任务每日扫描，过期月卡自动置 EXPIRED | 单元测试+验证 |
| 8 | 存量 MONTHLY 数据迁移完成，旧 MONTHLY 按 FREE 处理 | Flyway 验证 |
| 9 | 旧 `sys_vehicle` MONTHLY 路径不再生效 | 单元测试 |
| 10 | 单元测试覆盖率 ≥ 70%（新增代码） | JaCoCo |

---

## 12. 测试策略

### 12.1 单元测试

- `MonthlyPassServiceTest`：创建（正常+重复拦截）、续期、注销、列表查询、到期预警
- `VehicleTypeDecisionServiceImplTest`：月卡命中 → MONTHLY 判定；月卡过期 → 不命中 → 走类型链
- `MonthlyPassExpiryJobTest`：到期任务正确更新状态

### 12.2 集成测试

- 完整入场链路：识别事件 → 月卡判定 → 自动放行 → 无预订单
- 完整出场链路：过期月卡车辆 → 临停计费 → 生成待支付订单

### 12.3 已有测试适配

- `VehicleTypeDecisionServiceImplTest` 中 MONTHLY 用例：改用 `monthly_pass` 表数据
- `MonthlyPassServiceTest`：重写（原测试依赖 `SysVehicleMapper`）

---

## 附录 A：缴费方式映射

`monthly_pass.pay_method` → `parking_order.payChannel`：

| monthly_pass.pay_method | parking_order.payChannel |
|-------------------------|--------------------------|
| CASH | CASH |
| OFFLINE_TRANSFER | BALANCE（借道） |
| SIMULATED_PAY | PYUN（模拟支付） |
| OTHER | BALANCE（借道） |

---

## 附录 B：与任务包 5-2 的衔接预留

- `source = "MINIAPP"` + `applicant_id` 字段已就位
- `pay_method = "SIMULATED_PAY"` 值已定义
- 小程序支付回调写 `parking_order` 表时 `orderType = "MONTHLY_PASS"`、`refId = monthly_pass.id`
- 支付成功后将 `monthly_pass.pass_status` 置为 `ACTIVE`
