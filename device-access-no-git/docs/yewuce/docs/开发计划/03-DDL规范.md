# 3. 数据库 DDL

## 3.1 设计总则

本章节给出停车 SaaS 多租户系统（MySQL 8.0）的完整 DDL 定义，所有业务表遵循以下共享规范：

1. **纯 SaaS 多租户**：所有业务表必须包含 `tenant_id BIGINT NOT NULL`，所有查询必须带 `tenant_id` 过滤；平台级表（如平台账号、平台级角色）可设 `tenant_id = 0` 或保留 `tenant_id` 但允许 `NULL`，具体见各表说明。
2. **软删除**：每张业务表必须包含 `deleted_at DATETIME(3) DEFAULT NULL`；删除时更新为当前时间，查询时过滤 `deleted_at IS NULL`。
3. **金额字段**：全部使用 `DECIMAL(18,2)` 或 `DECIMAL(18,4)`（积分/费率视情况），禁止使用 `FLOAT/DOUBLE`。
4. **车牌存储**：车牌号码统一以大写存储，业务层写入前转大写；查询时忽略大小写（业务层统一转大写后查询）。
5. **操作日志**：所有配置变更类操作日志必须记录 `operator_id`、`operator_ip`、`operation_time`，以及 `before_value`/`after_value` JSON 字段。
6. **并发控制**：车位余量、优惠券库存、积分扣减等关键资源必须使用 Redis 分布式锁 + 数据库乐观锁（`version INT NOT NULL DEFAULT 0`）。
7. **时间字段**：统一使用 `DATETIME(3)` 或 `DATETIME`；创建/更新时间统一为 `created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` 和 `updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)`。
8. **预留接口**：标注【预留】的字段/表当前返回 mock 或空实现，但字段定义必须完整，便于后续扩展。
9. **逻辑外键**：外键关系在注释中说明，不强制写 `FOREIGN KEY`，由业务层保证数据一致性。
10. **字符集**：统一使用 `utf8mb4` 字符集 + `utf8mb4_unicode_ci` 排序规则。
11. **主键策略**：所有表主键使用 Snowflake 分布式ID（`BIGINT UNSIGNED NOT NULL`），禁止 `AUTO_INCREMENT`；代码中使用 `IdType.ASSIGN_ID`。

---

## 3.2 核心表 DDL

### 3.2.1 company（公司/集团档案表）

> 辅助表，用于支撑停车场三级架构（集团-公司-车场）。

```sql
CREATE TABLE company (
    id              BIGINT UNSIGNED NOT NULL COMMENT '公司ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    parent_id       BIGINT UNSIGNED     DEFAULT NULL COMMENT '上级公司ID（逻辑外键：company.id，顶级为NULL）',
    name            VARCHAR(128)        NOT NULL COMMENT '公司名称',
    level           TINYINT             NOT NULL DEFAULT 1 COMMENT '公司级别：1集团 2子公司 3分公司',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1正常 2暂停 3注销',
    sort_order      INT                 NOT NULL DEFAULT 0 COMMENT '排序',
    lot_home_image  VARCHAR(512)        DEFAULT NULL COMMENT '车场首页图片URL',
    group_home_image VARCHAR(512)       DEFAULT NULL COMMENT '集团首页图片URL',
    contact_name    VARCHAR(64)         DEFAULT NULL COMMENT '联系人',
    contact_phone   VARCHAR(32)         DEFAULT NULL COMMENT '联系电话',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_parent_id (parent_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司/集团档案表';
```

**设计要点**：
- 支持多级公司树，通过 `parent_id` 自关联；
- `tenant_id` 保证租户间公司数据隔离；
- 删除前需校验下级公司和关联车场已处理。

---

### 3.2.2 parking_lot（停车场档案表）

```sql
CREATE TABLE parking_lot (
    id                BIGINT UNSIGNED NOT NULL COMMENT '车场ID（Snowflake）',
    tenant_id         BIGINT            NOT NULL COMMENT '租户ID',
    company_id        BIGINT UNSIGNED   NOT NULL COMMENT '所属公司ID（逻辑外键：company.id）',
    group_id          BIGINT UNSIGNED   DEFAULT NULL COMMENT '所属集团ID（逻辑外键：company.id，冗余）',
    name              VARCHAR(128)      NOT NULL COMMENT '车场名称',
    region_type       TINYINT           NOT NULL DEFAULT 1 COMMENT '区域类型：1商场 2写字楼 3住宅小区 4医院 5景区 6交通枢纽',
    province          VARCHAR(64)       DEFAULT NULL COMMENT '省份',
    city              VARCHAR(64)       DEFAULT NULL COMMENT '城市',
    district          VARCHAR(64)       DEFAULT NULL COMMENT '区县',
    address           VARCHAR(256)      DEFAULT NULL COMMENT '详细地址',
    longitude         DECIMAL(10, 7)    DEFAULT NULL COMMENT '经度',
    latitude          DECIMAL(10, 7)    DEFAULT NULL COMMENT '纬度',
    contact_name      VARCHAR(64)       DEFAULT NULL COMMENT '联系人',
    contact_phone     VARCHAR(32)       DEFAULT NULL COMMENT '联系电话',
    status            TINYINT           NOT NULL DEFAULT 1 COMMENT '状态：1营业中 2暂停营业 3装修升级',
    business_hours    VARCHAR(32)       DEFAULT '00:00-24:00' COMMENT '营业时间',
    total_spaces      INT               NOT NULL DEFAULT 0 COMMENT '总车位数（自动汇总区域）',
    images            JSON              DEFAULT NULL COMMENT '车场图片URL数组',
    auth_code_id      BIGINT UNSIGNED   DEFAULT NULL COMMENT '授权码ID（逻辑外键：auth_code.id）【预留】',
    service_fee_rate  DECIMAL(5, 4)     DEFAULT 0.0500 COMMENT '平台服务费率（如0.05=5%）',
    version           INT               NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at        DATETIME(3)       DEFAULT NULL COMMENT '软删除时间',
    created_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_company_id (company_id),
    KEY idx_status (status),
    KEY idx_region_type (region_type),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场档案表';
```

**设计要点**：
- `total_spaces` 由区域汇总计算，不可手动修改；
- `service_fee_rate` 支持车场级服务费率覆盖（默认继承平台阶梯费率）；
- 删除车场为软删除，误删可通过“车场恢复”功能还原。

---

### 3.2.3 parking_zone（区域管理表）

```sql
CREATE TABLE parking_zone (
    id              BIGINT UNSIGNED NOT NULL COMMENT '区域ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    name            VARCHAR(128)        NOT NULL COMMENT '区域名称',
    tag             VARCHAR(64)         NOT NULL DEFAULT 'NORMAL' COMMENT '区域标签：NORMAL普通 VIP员工 LOADING装卸 CHARGE充电 支持自定义',
    level           TINYINT             NOT NULL DEFAULT 1 COMMENT '区域等级：1普通 2VIP 3员工',
    fee_rule_id     BIGINT UNSIGNED     NOT NULL COMMENT '收费标准ID（逻辑外键：fee_rule.id）',
    total_spaces    INT                 NOT NULL DEFAULT 0 COMMENT '车位总数',
    fixed_spaces    INT                 NOT NULL DEFAULT 0 COMMENT '固定车位数',
    temp_spaces     INT                 NOT NULL DEFAULT 0 COMMENT '临停车位数（= total_spaces - fixed_spaces）',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    manager_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '区域负责人ID（逻辑外键：admin_account.id）',
    remark          VARCHAR(512)        DEFAULT NULL COMMENT '备注',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_fee_rule_id (fee_rule_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域管理表';
```

**设计要点**：
- 一个停车场至少包含 1 个区域；
- `temp_spaces` 建议由业务层维护，避免与 `total_spaces/fixed_spaces` 不一致；
- 禁用区域后，该区域关联通道自动暂停使用。

---

### 3.2.4 parking_lane（通道管理表）

```sql
CREATE TABLE parking_lane (
    id              BIGINT UNSIGNED NOT NULL COMMENT '通道ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id         BIGINT UNSIGNED     NOT NULL COMMENT '所属区域ID（逻辑外键：parking_zone.id）',
    lane_no         VARCHAR(32)         NOT NULL COMMENT '通道编号，如 A1、17',
    name            VARCHAR(128)        NOT NULL COMMENT '通道名称，如东大门',
    type            TINYINT             NOT NULL DEFAULT 1 COMMENT '通道类型：1入口 2出口 3双向',
    entry_camera_id BIGINT UNSIGNED     DEFAULT NULL COMMENT '入口相机ID（逻辑外键：device_camera.id）',
    exit_camera_id  BIGINT UNSIGNED     DEFAULT NULL COMMENT '出口相机ID（逻辑外键：device_camera.id）',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用 3维护中',
    tide_mode       TINYINT             DEFAULT 0 COMMENT '潮汐模式：0关闭 1早高峰入口 2晚高峰出口',
    camera_mode     TINYINT             DEFAULT 1 COMMENT '相机配置模式：1单相机 2双相机 3主从相机',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_lane_no (tenant_id, lot_id, lane_no, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_zone_id (zone_id),
    KEY idx_entry_camera_id (entry_camera_id),
    KEY idx_exit_camera_id (exit_camera_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通道管理表';
```

**设计要点**：
- 双向通道通过 `entry_camera_id` 和 `exit_camera_id` 分别绑定入口侧/出口侧相机；
- `camera_mode` 支持单相机/双相机/主从相机三种模式（Q6 确认）；
- 平台只对接相机，显示屏和道闸作为相机外设不单独建档（Q7 确认）。

---

### 3.2.5 fee_rule（收费规则表）

```sql
CREATE TABLE fee_rule (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '规则ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '适用区域ID（逻辑外键：parking_zone.id，NULL表示车场通用）',
    name                VARCHAR(128)        NOT NULL COMMENT '规则名称',
    billing_mode        TINYINT             NOT NULL DEFAULT 1 COMMENT '计费模式：1按时 2按次 3阶梯 4分时段',
    free_minutes        INT                 NOT NULL DEFAULT 0 COMMENT '免费时长（分钟）',
    unit_minutes        INT                 NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
    first_period_price  DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '首时段价格',
    subsequent_price    DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '后续单价',
    daily_cap           DECIMAL(18,2)       DEFAULT NULL COMMENT '24小时封顶金额',
    night_cap           DECIMAL(18,2)       DEFAULT NULL COMMENT '夜间封顶金额',
    priority            INT                 NOT NULL DEFAULT 0 COMMENT '优先级，数字越大优先级越高',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    effective_start     DATETIME(3)         DEFAULT NULL COMMENT '生效开始时间',
    effective_end       DATETIME(3)         DEFAULT NULL COMMENT '生效结束时间',
    holiday_rules       JSON                DEFAULT NULL COMMENT '节假日特殊规则JSON',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_zone_id (zone_id),
    KEY idx_billing_mode (billing_mode),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则表';
```

**设计要点**：
- 收费规则按“区域特殊规则 > 车场通用规则 > 平台默认规则”优先级生效；
- `billing_mode=4` 分时段计费时，配合 `fee_rule_segment` 表使用；
- `holiday_rules` 存储节假日特殊计费配置，便于灵活扩展。

---

### 3.2.6 fee_rule_segment（收费规则时段表）【辅助表】

```sql
CREATE TABLE fee_rule_segment (
    id              BIGINT UNSIGNED NOT NULL COMMENT '时段ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    fee_rule_id     BIGINT UNSIGNED     NOT NULL COMMENT '收费规则ID（逻辑外键：fee_rule.id）',
    segment_name    VARCHAR(64)         NOT NULL COMMENT '时段名称，如白天/夜间',
    start_time      TIME                NOT NULL COMMENT '时段开始时间',
    end_time        TIME                NOT NULL COMMENT '时段结束时间',
    unit_minutes    INT                 NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
    unit_price      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '时段单价',
    cap_amount      DECIMAL(18,2)       DEFAULT NULL COMMENT '时段封顶金额',
    sort_order      INT                 NOT NULL DEFAULT 0 COMMENT '排序',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_fee_rule_id (fee_rule_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则时段表';
```

**设计要点**：
- 仅分时段计费模式下使用；
- 同一规则下时段不重叠，由业务层校验。

---

### 3.2.7 vehicle_type_config（车辆类型配置表）【辅助表】

```sql
CREATE TABLE vehicle_type_config (
    id                                          BIGINT UNSIGNED NOT NULL COMMENT '配置ID（Snowflake）',
    tenant_id                                   BIGINT              NOT NULL COMMENT '租户ID',
    lot_id                                      BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    type_code                                   VARCHAR(32)         NOT NULL COMMENT '类型代码：TEMP/MONTH/WALLET/FREE/VIP',
    type_name                                   VARCHAR(64)         NOT NULL COMMENT '类型名称',
    allow_renewal                               TINYINT             NOT NULL DEFAULT 1 COMMENT '是否允许车主续费：0否 1是',
    renewal_in_valid_period_mode                TINYINT             NOT NULL DEFAULT 1 COMMENT '有效期内续费模式：1到期日顺延 2从续费当天开始',
    renewal_after_expiry_entry_mode             TINYINT             NOT NULL DEFAULT 1 COMMENT '过期后入场续费模式：1过期变临时后续费重新算 2续费后需出场再入场',
    renewal_after_expiry_in_parking_mode        TINYINT             NOT NULL DEFAULT 2 COMMENT '场内过期续费模式：1需补临停费 2直接续费不补费',
    renewal_after_expiry_not_in_parking_mode    TINYINT             NOT NULL DEFAULT 1 COMMENT '未入场过期后续费模式：1立即恢复 2等待X小时生效',
    occupy_space_flag                           TINYINT             NOT NULL DEFAULT 1 COMMENT '是否统计车位：0否 1是',
    version                                     INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at                                  DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at                                  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                                  DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_type (tenant_id, lot_id, type_code, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆类型配置表';
```

**设计要点**：
- 按车场配置车辆类型策略，支持“标准模式”和“高级模式”；
- `type_code` 与 `vehicle.vehicle_type` 字段对应。

---

### 3.2.8 access_policy（进出策略配置表）【辅助表】

```sql
CREATE TABLE access_policy (
    id              BIGINT UNSIGNED NOT NULL COMMENT '策略ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    policy_type     TINYINT             NOT NULL COMMENT '策略类型：1入场策略 2出场策略 3车位满 4余位计算 5分时段管控 6一位多车 7车牌颜色',
    policy_key      VARCHAR(64)         NOT NULL COMMENT '策略项KEY',
    policy_value    VARCHAR(256)        NOT NULL COMMENT '策略项VALUE',
    extra           JSON                DEFAULT NULL COMMENT '扩展配置',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_type_key (tenant_id, lot_id, policy_type, policy_key, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_policy_type (policy_type),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='进出策略配置表';
```

**设计要点**：
- 采用键值对方式存储车场级各类策略，避免策略表过度膨胀；
- `policy_key` 如 `expired_month_entry`、`duplicate_in_parking`、`full_lot_action` 等；
- `extra` 用于存储白名单、时段等复杂配置。

---

### 3.2.9 department（部门表）

```sql
CREATE TABLE department (
    id              BIGINT UNSIGNED NOT NULL COMMENT '部门ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    parent_id       BIGINT UNSIGNED     DEFAULT NULL COMMENT '上级部门ID（逻辑外键：department.id）',
    name            VARCHAR(128)        NOT NULL COMMENT '部门名称',
    manager_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '部门负责人ID（逻辑外键：admin_account.id）',
    contact_phone   VARCHAR(32)         DEFAULT NULL COMMENT '联系电话',
    sort_order      INT                 NOT NULL DEFAULT 0 COMMENT '排序',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    remark          VARCHAR(512)        DEFAULT NULL COMMENT '备注',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_parent_id (parent_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表（组织架构）';
```

**设计要点**：
- 支持多级部门树，三级管理员仅可操作本车场部门；
- 删除部门前需校验部门下无车辆。

---

### 3.2.10 vehicle（车辆主表）

```sql
CREATE TABLE vehicle (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '车辆ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    department_id       BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属部门ID（逻辑外键：department.id）',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码（大写存储）',
    plate_color         TINYINT             NOT NULL DEFAULT 1 COMMENT '车牌颜色：1蓝 2黄 3绿 4白 5黑 6渐变绿',
    plate_type          TINYINT             DEFAULT 1 COMMENT '车牌类型：1普通 2新能源 3军警 4外籍',
    vehicle_type        VARCHAR(32)         NOT NULL DEFAULT 'TEMP' COMMENT '车辆类型：TEMP/MONTH/WALLET/FREE/VIP/SUPER/BLACKLIST',
    owner_name          VARCHAR(64)         DEFAULT NULL COMMENT '车主/联系人姓名',
    owner_phone         VARCHAR(32)         DEFAULT NULL COMMENT '车主手机号（AES加密存储）',
    owner_no            VARCHAR(64)         DEFAULT NULL COMMENT '人员编号',
    address             VARCHAR(256)        DEFAULT NULL COMMENT '家庭住址',
    parking_space_no    VARCHAR(64)         DEFAULT NULL COMMENT '绑定车位编号',
    fee_rule_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '绑定收费规则ID（逻辑外键：fee_rule.id，月卡/储值车必填）',
    vip_level           TINYINT             DEFAULT NULL COMMENT 'VIP等级：1一级 2二级 3三级',
    vip_benefits        JSON                DEFAULT NULL COMMENT 'VIP权益配置JSON',
    super_permissions   JSON                DEFAULT NULL COMMENT '超级车牌权限JSON',
    valid_start         DATETIME(3)         DEFAULT NULL COMMENT '有效期开始',
    valid_end           DATETIME(3)         DEFAULT NULL COMMENT '有效期结束',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1正常 2已过期 3已禁用 4暂停',
    occupy_space_flag   TINYINT             NOT NULL DEFAULT 1 COMMENT '是否统计车位：0否 1是',
    is_multi_plate      TINYINT             NOT NULL DEFAULT 0 COMMENT '是否一位多车：0否 1是',
    max_bind_count      INT                 DEFAULT 2 COMMENT '一位多车最大绑定数（1-10）',
    source              TINYINT             NOT NULL DEFAULT 1 COMMENT '来源：1后台登记 2小程序 3导入',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_plate (tenant_id, lot_id, plate_number, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_department_id (department_id),
    KEY idx_plate_number (plate_number),
    KEY idx_vehicle_type (vehicle_type),
    KEY idx_status (status),
    KEY idx_valid_end (valid_end),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆主表';
```

**设计要点**：
- `plate_number` 强制大写存储，唯一索引包含 `tenant_id + lot_id + plate_number + deleted_at`；
- `owner_phone` 等敏感字段建议 AES 加密存储；
- 一位多车通过 `is_multi_plate` 和 `vehicle_multi_plate` 表实现；
- 黑名单车辆也在本表登记，`vehicle_type='BLACKLIST'`，黑名单明细见 `blacklist` 表。

---

### 3.2.11 vehicle_multi_plate（一位多车绑定表）

```sql
CREATE TABLE vehicle_multi_plate (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '绑定ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    main_vehicle_id     BIGINT UNSIGNED     NOT NULL COMMENT '主车辆ID（逻辑外键：vehicle.id）',
    bind_vehicle_id     BIGINT UNSIGNED     NOT NULL COMMENT '绑定车辆ID（逻辑外键：vehicle.id）',
    parking_space_no    VARCHAR(64)         NOT NULL COMMENT '共享车位编号',
    priority            INT                 NOT NULL DEFAULT 0 COMMENT '优先级，数字越小优先级越高',
    current_active      TINYINT             NOT NULL DEFAULT 0 COMMENT '当前是否享受固定车待遇：0否 1是',
    sync_strategy       TINYINT             NOT NULL DEFAULT 1 COMMENT '同步策略：1同步续期 2独立续期',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_main_bind (tenant_id, main_vehicle_id, bind_vehicle_id, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_main_vehicle_id (main_vehicle_id),
    KEY idx_bind_vehicle_id (bind_vehicle_id),
    KEY idx_parking_space_no (parking_space_no),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一位多车绑定表';
```

**设计要点**：
- 主车牌与绑定车牌共享一个车位，但同时仅一辆车享受固定车/VIP 待遇；
- `current_active` 标记当前哪辆车在场并享受固定车待遇；
- 固定车出场后，按配置将 `current_active` 切换给第二辆车。

---

### 3.2.12 vehicle_wallet（储值车账户表）

```sql
CREATE TABLE vehicle_wallet (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '钱包ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    vehicle_id          BIGINT UNSIGNED     NOT NULL COMMENT '车辆ID（逻辑外键：vehicle.id）',
    balance             DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '当前余额',
    total_recharge      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '累计充值',
    total_consumption   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '累计消费',
    low_balance_threshold DECIMAL(18,2)     DEFAULT 0.00 COMMENT '低余额提醒阈值',
    auto_recharge       TINYINT             DEFAULT 0 COMMENT '是否开启自动充值：0否 1是【预留】',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1正常 2冻结 3注销',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（余额扣减用）',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_vehicle (tenant_id, vehicle_id, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_vehicle_id (vehicle_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值车账户表';
```

**设计要点**：
- 余额扣减必须使用 Redis 分布式锁 + `version` 乐观锁，防止并发超扣；
- `balance` 禁止为负，扣减时业务层校验；
- 所有余额变动必须写入 `vehicle_wallet_log` 流水表。

---

### 3.2.13 vehicle_wallet_log（储值车流水表）

```sql
CREATE TABLE vehicle_wallet_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '流水ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    vehicle_id          BIGINT UNSIGNED     NOT NULL COMMENT '车辆ID（逻辑外键：vehicle.id）',
    wallet_id           BIGINT UNSIGNED     NOT NULL COMMENT '钱包ID（逻辑外键：vehicle_wallet.id）',
    change_type         TINYINT             NOT NULL COMMENT '变动类型：1充值 2消费 3退款 4调账 5赠送',
    change_amount       DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '变动金额（正数增加，负数减少）',
    balance_before      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '变动前余额',
    balance_after       DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '变动后余额',
    order_id            BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联订单ID（逻辑外键：parking_order.id）',
    reason              VARCHAR(256)        DEFAULT NULL COMMENT '变动原因',
    operator_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '操作人ID',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_vehicle_id (vehicle_id),
    KEY idx_wallet_id (wallet_id),
    KEY idx_order_id (order_id),
    KEY idx_change_type (change_type),
    KEY idx_operation_time (operation_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值车流水表';
```

**设计要点**：
- 流水表为不可变记录，不允许物理删除和修改；
- `before_value`/`after_value` 记录余额变更前后快照；
- 调账/大额操作需记录操作日志并触发审批。

---

### 3.2.14 lane_permission（通道权限表）

```sql
CREATE TABLE lane_permission (
    id              BIGINT UNSIGNED NOT NULL COMMENT '权限ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    target_type     TINYINT             NOT NULL COMMENT '权限对象类型：1车辆 2部门',
    target_id       BIGINT UNSIGNED     NOT NULL COMMENT '对象ID（vehicle.id 或 department.id）',
    lane_id         BIGINT UNSIGNED     NOT NULL COMMENT '通道ID（逻辑外键：parking_lane.id）',
    permission_type TINYINT             NOT NULL DEFAULT 3 COMMENT '权限类型：1仅入场 2仅出场 3双向 4禁止',
    effective_start TIME                DEFAULT NULL COMMENT '生效时段开始',
    effective_end   TIME                DEFAULT NULL COMMENT '生效时段结束',
    effective_days  VARCHAR(16)         DEFAULT NULL COMMENT '生效周期：1,2,3,4,5,6,7 表示周一至周日',
    zone_ids        JSON                DEFAULT NULL COMMENT '生效区域ID列表',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_target_lane (tenant_id, target_type, target_id, lane_id, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_target_id (target_id),
    KEY idx_lane_id (lane_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通道权限表';
```

**设计要点**：
- 支持按车辆和按部门两种维度配置通道权限；
- 未配置时默认所有通道可用；
- 生效时段/周期支持潮汐通道等分时段管控。

---

### 3.2.15 blacklist（黑名单表）

```sql
CREATE TABLE blacklist (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '黑名单ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    vehicle_id          BIGINT UNSIGNED     DEFAULT NULL COMMENT '车辆ID（逻辑外键：vehicle.id，模糊匹配时可空）',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码（大写存储，支持模糊如 京A*）',
    plate_color         TINYINT             DEFAULT NULL COMMENT '车牌颜色',
    reason_type         TINYINT             NOT NULL DEFAULT 5 COMMENT '拉黑原因：1欠费未缴 2超时停放 3违规停车 4手动添加 5其他',
    source              TINYINT             NOT NULL DEFAULT 1 COMMENT '拉黑来源：1手动 2系统超时 3系统联动欠费',
    effective_type      TINYINT             NOT NULL DEFAULT 1 COMMENT '有效期类型：1永久 2指定日期 3触发条件解除',
    valid_end           DATETIME(3)         DEFAULT NULL COMMENT '有效期结束',
    release_condition   VARCHAR(256)        DEFAULT NULL COMMENT '解除条件说明',
    related_order_ids   JSON                DEFAULT NULL COMMENT '关联欠费订单ID列表',
    black_by            BIGINT UNSIGNED     NOT NULL COMMENT '拉黑操作人ID',
    black_time          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '拉黑时间',
    released_by         BIGINT UNSIGNED     DEFAULT NULL COMMENT '解除操作人ID',
    release_time        DATETIME(3)         DEFAULT NULL COMMENT '解除时间',
    release_reason      VARCHAR(256)        DEFAULT NULL COMMENT '解除原因',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1生效中 2已解除',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_vehicle_id (vehicle_id),
    KEY idx_plate_number (plate_number),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单表';
```

**设计要点**：
- 黑名单拦截入场，出场时按策略处理；
- 支持手动添加和系统自动添加（超时停放、欠费）；
- 解除操作必须记录原因和操作人。

---

### 3.2.16 vehicle_audit（车牌审核表）【辅助表】

```sql
CREATE TABLE vehicle_audit (
    id              BIGINT UNSIGNED NOT NULL COMMENT '审核ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    apply_type      TINYINT             NOT NULL COMMENT '申请类型：1新登记 2续期 3变更车牌 4变更车位 5注销',
    applicant_id    BIGINT UNSIGNED     NOT NULL COMMENT '申请人小程序用户ID',
    applicant_name  VARCHAR(64)         DEFAULT NULL COMMENT '申请人姓名',
    applicant_phone VARCHAR(32)         DEFAULT NULL COMMENT '申请人手机号',
    plate_number    VARCHAR(16)         NOT NULL COMMENT '车牌号码（大写存储）',
    vehicle_type    VARCHAR(32)         NOT NULL COMMENT '目标车辆类型',
    materials       JSON                DEFAULT NULL COMMENT '申请材料图片URL列表',
    audit_status    TINYINT             NOT NULL DEFAULT 1 COMMENT '审核状态：1待审核 2审核中 3已通过 4已驳回 5待补充',
    auditor_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '审核人ID',
    audit_time      DATETIME(3)         DEFAULT NULL COMMENT '审核时间',
    audit_remark    VARCHAR(512)        DEFAULT NULL COMMENT '审核备注',
    vehicle_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '审核通过后生成的车辆ID',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_applicant_id (applicant_id),
    KEY idx_plate_number (plate_number),
    KEY idx_audit_status (audit_status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车牌审核表';
```

**设计要点**：
- 车主小程序自助提交的车辆登记/续期/变更申请需经审核；
- 审核记录保留 3 年（按数据保留策略归档）。

---

### 3.2.17 coupon（优惠券定义表）

```sql
CREATE TABLE coupon (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '券规则ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    name                VARCHAR(128)        NOT NULL COMMENT '券名称',
    coupon_type         TINYINT             NOT NULL DEFAULT 1 COMMENT '券类型：1满减 2折扣 3免费时长 4全免 5立减',
    discount_type       TINYINT             NOT NULL DEFAULT 1 COMMENT '优惠内容：1减免金额 2减免时长 3折扣比例 4全免',
    discount_value      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '优惠值（金额/时长小时/折扣比例）',
    min_consumption     DECIMAL(18,2)       DEFAULT 0.00 COMMENT '使用门槛金额',
    max_discount        DECIMAL(18,2)       DEFAULT NULL COMMENT '最高抵扣金额',
    applicable_lot_ids  JSON                DEFAULT NULL COMMENT '适用车场ID列表，NULL表示全平台',
    effective_start     DATETIME(3)         DEFAULT NULL COMMENT '有效期开始',
    effective_end       DATETIME(3)         DEFAULT NULL COMMENT '有效期结束',
    valid_after_receive_hours INT           DEFAULT NULL COMMENT '领取后X小时有效',
    valid_type          TINYINT             NOT NULL DEFAULT 1 COMMENT '有效期类型：1固定时间 2领取后X小时 3当日有效',
    max_per_user        INT                 DEFAULT 1 COMMENT '每人限领数量',
    receive_channels    JSON                DEFAULT NULL COMMENT '发放渠道：1扫码领券 2车牌绑定 3消费满额 4手动发放',
    stack_with_merchant TINYINT             DEFAULT 0 COMMENT '是否可与商家优惠叠加：0否 1是',
    stack_with_points   TINYINT             DEFAULT 1 COMMENT '是否可与积分叠加：0否 1是',
    refund_strategy     TINYINT             DEFAULT 1 COMMENT '退款退回策略：0不退回 1退回',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    total_issue_count   INT                 NOT NULL DEFAULT 0 COMMENT '总发放数量',
    total_used_count    INT                 NOT NULL DEFAULT 0 COMMENT '已使用数量',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（库存用）',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_coupon_type (coupon_type),
    KEY idx_status (status),
    KEY idx_effective_time (effective_start, effective_end),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券定义表';
```

**设计要点**：
- 优惠券库存/发放量需用 Redis 分布式锁 + `version` 乐观锁控制并发；
- `refund_strategy` 支持按券类型配置退款是否退回；
- 适用车场通过 JSON 数组存储，便于多车场筛选。

---

### 3.2.18 user_coupon（用户优惠券实例表）

```sql
CREATE TABLE user_coupon (
    id              BIGINT UNSIGNED NOT NULL COMMENT '实例ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    user_id         BIGINT UNSIGNED     NOT NULL COMMENT '用户ID（小程序用户）',
    coupon_id       BIGINT UNSIGNED     NOT NULL COMMENT '优惠券定义ID（逻辑外键：coupon.id）',
    lot_id          BIGINT UNSIGNED     DEFAULT NULL COMMENT '适用车场ID',
    plate_number    VARCHAR(16)         DEFAULT NULL COMMENT '绑定车牌（大写存储）',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1未使用 2已使用 3已过期 4已退回',
    used_order_id   BIGINT UNSIGNED     DEFAULT NULL COMMENT '使用订单ID（逻辑外键：parking_order.id）',
    used_time       DATETIME(3)         DEFAULT NULL COMMENT '使用时间',
    effective_start DATETIME(3)         NOT NULL COMMENT '有效期开始',
    effective_end   DATETIME(3)         NOT NULL COMMENT '有效期结束',
    source          TINYINT             NOT NULL DEFAULT 1 COMMENT '来源：1领取 2发放 3退款退回',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（核销用）',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_user_id (user_id),
    KEY idx_coupon_id (coupon_id),
    KEY idx_status (status),
    KEY idx_effective_end (effective_end),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券实例表';
```

**设计要点**：
- 用户领取优惠券后生成实例，状态机：未使用 → 已使用/已过期/已退回；
- 核销时通过 `version` 乐观锁防止重复核销；
- `plate_number` 用于车牌绑定类优惠券自动核销。

---

### 3.2.19 coupon_usage_log（优惠券核销流水表）

```sql
CREATE TABLE coupon_usage_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '流水ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    user_coupon_id      BIGINT UNSIGNED     NOT NULL COMMENT '用户券实例ID（逻辑外键：user_coupon.id）',
    coupon_id           BIGINT UNSIGNED     NOT NULL COMMENT '优惠券定义ID',
    user_id             BIGINT UNSIGNED     NOT NULL COMMENT '用户ID',
    order_id            BIGINT UNSIGNED     NOT NULL COMMENT '关联订单ID（逻辑外键：parking_order.id）',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码',
    original_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '优惠前金额',
    discount_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    final_amount        DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '优惠后金额',
    usage_type          TINYINT             NOT NULL DEFAULT 1 COMMENT '使用类型：1平台券 2商家券',
    merchant_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '商家ID（逻辑外键：merchant.id，商家券时必填）',
    use_time            DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '核销时间',
    refund_flag         TINYINT             DEFAULT 0 COMMENT '是否已退回：0否 1是',
    refund_time         DATETIME(3)         DEFAULT NULL COMMENT '退回时间',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_user_coupon_id (user_coupon_id),
    KEY idx_order_id (order_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_use_time (use_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券核销流水表';
```

**设计要点**：
- 记录每张券的核销详情，支持退款时按策略退回；
- 商家券核销同时扣减 `merchant_coupon_stock` 库存并生成 `merchant_order`。

---

### 3.2.20 member_points（会员积分账户表）

```sql
CREATE TABLE member_points (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '积分账户ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    user_id             BIGINT UNSIGNED     NOT NULL COMMENT '用户ID',
    total_points        INT                 NOT NULL DEFAULT 0 COMMENT '总积分',
    available_points    INT                 NOT NULL DEFAULT 0 COMMENT '可用积分',
    frozen_points       INT                 NOT NULL DEFAULT 0 COMMENT '冻结积分',
    total_earned        INT                 NOT NULL DEFAULT 0 COMMENT '累计获取',
    total_used          INT                 NOT NULL DEFAULT 0 COMMENT '累计使用',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（积分扣减用）',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_user (tenant_id, user_id, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_user_id (user_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员积分账户表';
```

**设计要点**：
- 积分扣减使用 Redis 分布式锁 + `version` 乐观锁；
- `available_points` 必须 ≥ 0；
- 所有积分变动写入 `points_transaction` 流水表。

---

### 3.2.21 points_transaction（积分流水表）

```sql
CREATE TABLE points_transaction (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '流水ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    user_id             BIGINT UNSIGNED     NOT NULL COMMENT '用户ID',
    points_account_id   BIGINT UNSIGNED     NOT NULL COMMENT '积分账户ID（逻辑外键：member_points.id）',
    change_type         TINYINT             NOT NULL COMMENT '变动类型：1获取 2抵扣 3退款退回 4过期扣除 5调账',
    change_points       INT                 NOT NULL DEFAULT 0 COMMENT '变动积分（正数增加，负数减少）',
    points_before       INT                 NOT NULL DEFAULT 0 COMMENT '变动前可用积分',
    points_after        INT                 NOT NULL DEFAULT 0 COMMENT '变动后可用积分',
    related_order_id    BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联订单ID（逻辑外键：parking_order.id）',
    related_business    VARCHAR(128)        DEFAULT NULL COMMENT '关联业务说明',
    expired_time        DATETIME(3)         DEFAULT NULL COMMENT '积分过期时间',
    operator_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '操作人ID',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_user_id (user_id),
    KEY idx_points_account_id (points_account_id),
    KEY idx_change_type (change_type),
    KEY idx_operation_time (operation_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';
```

**设计要点**：
- 流水不可变，不允许物理删除；
- 支持积分获取、抵扣、退款退回、过期扣除等类型。

---

### 3.2.22 points_config（积分规则配置表）【辅助表】

```sql
CREATE TABLE points_config (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '配置ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属车场ID，NULL表示租户级默认',
    rule_type           TINYINT             NOT NULL COMMENT '规则类型：1消费返积分 2签到 3任务 4积分抵扣比例',
    rule_key            VARCHAR(64)         NOT NULL COMMENT '规则KEY',
    rule_value          VARCHAR(256)        NOT NULL COMMENT '规则VALUE',
    description         VARCHAR(256)        DEFAULT NULL COMMENT '规则说明',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_type_key (tenant_id, lot_id, rule_type, rule_key, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分规则配置表';
```

**设计要点**：
- 车场级配置可覆盖租户级默认配置；
- 当前仅支持积分抵扣，积分商城字段预留但不实现。

---

### 3.2.23 merchant（优惠商家表）

```sql
CREATE TABLE merchant (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '商家ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    name                VARCHAR(128)        NOT NULL COMMENT '商家名称',
    merchant_type       TINYINT             NOT NULL DEFAULT 5 COMMENT '商家类型：1餐饮 2娱乐 3零售 4酒店 5其他',
    contact_name        VARCHAR(64)         NOT NULL COMMENT '联系人',
    contact_phone       VARCHAR(32)         NOT NULL COMMENT '联系电话',
    business_license    VARCHAR(512)        DEFAULT NULL COMMENT '营业执照图片URL',
    shop_location       VARCHAR(128)        DEFAULT NULL COMMENT '店铺位置',
    issue_permission    TINYINT             NOT NULL DEFAULT 1 COMMENT '发券权限：0禁用 1启用',
    available_balance   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '可用余额',
    frozen_balance      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
    total_issue_amount  DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '累计发券金额',
    total_verify_amount DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '累计核销金额',
    settlement_cycle    TINYINT             NOT NULL DEFAULT 3 COMMENT '结算周期：1实时 2日结 3月结 4季结',
    settlement_status   TINYINT             NOT NULL DEFAULT 1 COMMENT '结算状态：1正常 2欠费 3暂停',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1正常 2暂停 3注销',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（余额用）',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_status (status),
    KEY idx_settlement_status (settlement_status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠商家表';
```

**设计要点**：
- 商家优惠资金由商家自己承担（Q1 确认），商家需充值购买优惠额度；
- 余额不足时自动暂停发券权限；
- 商家与车场之间无分润关系。

---

### 3.2.24 merchant_recharge（商家充值记录表）【辅助表】

```sql
CREATE TABLE merchant_recharge (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '充值ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    merchant_id         BIGINT UNSIGNED     NOT NULL COMMENT '商家ID（逻辑外键：merchant.id）',
    recharge_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '充值金额',
    gift_amount         DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '赠送金额',
    actual_amount       DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '实际到账金额',
    recharge_channel    TINYINT             NOT NULL DEFAULT 1 COMMENT '充值方式：1银行转账 2微信 3支付宝 4对公账户',
    voucher_image       VARCHAR(512)        DEFAULT NULL COMMENT '转账凭证URL',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1待确认 2已到账 3已驳回',
    operator_id         BIGINT UNSIGNED     NOT NULL COMMENT '操作人ID',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家充值记录表';
```

**设计要点**：
- 充值确认后更新 `merchant.available_balance` 并生成 `merchant_coupon_stock` 库存批次；
- 大额充值需审批（可选）。

---

### 3.2.25 merchant_coupon_stock（商家优惠券库存表）

```sql
CREATE TABLE merchant_coupon_stock (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '库存ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    merchant_id         BIGINT UNSIGNED     NOT NULL COMMENT '商家ID（逻辑外键：merchant.id）',
    recharge_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联充值记录ID（逻辑外键：merchant_recharge.id）',
    coupon_type         TINYINT             NOT NULL DEFAULT 1 COMMENT '券类型：1数量券 2余额抵扣券 3时长券 4全免券',
    total_quantity      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '库存总量（数量或金额）',
    issued_quantity     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '已发放量',
    verified_quantity   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '已核销量',
    expired_return_quantity DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '已过期退回量',
    frozen_quantity     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '冻结量',
    available_quantity  DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '可用库存',
    valid_end           DATETIME(3)         DEFAULT NULL COMMENT '该批次库存有效期',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（库存用）',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_recharge_id (recharge_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家优惠券库存表';
```

**设计要点**：
- 商家发券时从库存中扣除，核销时冻结转已核销；
- 库存扣减使用 Redis 分布式锁 + `version` 乐观锁。

---

### 3.2.26 merchant_order（商家优惠订单表）

```sql
CREATE TABLE merchant_order (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '订单ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    merchant_id         BIGINT UNSIGNED     NOT NULL COMMENT '商家ID（逻辑外键：merchant.id）',
    parking_order_id    BIGINT UNSIGNED     NOT NULL COMMENT '关联停车订单ID（逻辑外键：parking_order.id）',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码',
    original_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '原始停车费用',
    discount_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '商家优惠金额',
    customer_pay_amount DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '顾客实付金额',
    coupon_type         TINYINT             NOT NULL DEFAULT 1 COMMENT '优惠类型：1数量券 2余额抵扣券 3车牌绑定优惠 4二维码核销',
    user_coupon_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联用户券实例ID',
    verify_time         DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '核销时间',
    verify_lane_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '核销通道ID（逻辑外键：parking_lane.id）',
    settlement_status   TINYINT             NOT NULL DEFAULT 2 COMMENT '结算状态：1已结算 2待结算 3争议',
    settlement_time     DATETIME(3)         DEFAULT NULL COMMENT '结算时间',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_parking_order_id (parking_order_id),
    KEY idx_settlement_status (settlement_status),
    KEY idx_verify_time (verify_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家优惠订单表';
```

**设计要点**：
- 记录商家优惠的核销明细，用于商家结算；
- 商家优惠部分退款不退回商家（已核销即结算）。

---

### 3.2.27 parking_order（停车订单表）

```sql
CREATE TABLE parking_order (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '订单ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属区域ID（逻辑外键：parking_zone.id）',
    lane_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '出场通道ID（逻辑外键：parking_lane.id）',
    order_no            VARCHAR(64)         NOT NULL COMMENT '订单编号',
    order_type          TINYINT             NOT NULL DEFAULT 1 COMMENT '订单类型：1临停 2月卡续费 3储值充值 4商家优惠 5访客 6补缴 7退款',
    order_status        TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1待支付 2支付中 3已支付 4已完成 5已取消 6支付失败 7待开票 8已开票 9退款中 10已退款',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码（大写存储）',
    plate_color         TINYINT             DEFAULT NULL COMMENT '车牌颜色',
    vehicle_type        VARCHAR(32)         DEFAULT 'TEMP' COMMENT '车辆类型',
    entry_time          DATETIME(3)         DEFAULT NULL COMMENT '入场时间',
    exit_time           DATETIME(3)         DEFAULT NULL COMMENT '出场时间',
    parking_duration    INT                 DEFAULT NULL COMMENT '停车时长（分钟）',
    fee_rule_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '计费规则ID',
    original_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '原始应收',
    coupon_discount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣',
    points_discount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '积分抵扣',
    merchant_discount   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '商家优惠抵扣',
    total_discount      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '总优惠金额',
    payable_amount      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '应付金额',
    paid_amount         DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    payment_channel     VARCHAR(32)         DEFAULT 'WECHAT' COMMENT '支付渠道：WECHAT/ALIPAY/UNION_PAY/OTHER【预留】',
    payment_method      TINYINT             DEFAULT 1 COMMENT '支付方式：1微信 2余额 3组合 4第三方【预留】',
    third_party_merchant_id VARCHAR(64)     DEFAULT NULL COMMENT '第三方商户号【预留】',
    third_party_app_id  VARCHAR(64)         DEFAULT NULL COMMENT '第三方应用ID【预留】',
    payment_extra       JSON                DEFAULT NULL COMMENT '支付扩展信息JSON【预留】',
    trade_no            VARCHAR(128)        DEFAULT NULL COMMENT '支付渠道交易流水号',
    payment_time        DATETIME(3)         DEFAULT NULL COMMENT '支付时间',
    refund_amount       DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '退款金额',
    refund_reason       VARCHAR(256)        DEFAULT NULL COMMENT '退款原因',
    refund_operator_id  BIGINT UNSIGNED     DEFAULT NULL COMMENT '退款操作人ID',
    refund_time         DATETIME(3)         DEFAULT NULL COMMENT '退款时间',
    invoice_status      TINYINT             DEFAULT 0 COMMENT '开票状态：0未开票 1待开票 2已开票 3已红冲',
    invoice_id          BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联发票ID',
    operator_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '岗亭操作员ID',
    finish_time         DATETIME(3)         DEFAULT NULL COMMENT '订单完成时间',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_order_no (tenant_id, order_no, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_plate_number (plate_number),
    KEY idx_order_status (order_status),
    KEY idx_order_type (order_type),
    KEY idx_payment_channel (payment_channel),
    KEY idx_invoice_status (invoice_status),
    KEY idx_created_at (created_at),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车订单表';
```

**设计要点**：
- 订单编号全局唯一，生成规则包含租户标识+时间戳+序列；
- 支付回调幂等通过 `order_no` + `trade_no` 控制；
- 退款金额不得超过 `paid_amount`；
- 第三方支付渠道字段已预留，一期仅使用微信支付。

---

### 3.2.28 vehicle_access_log（车辆进出记录表）

```sql
CREATE TABLE vehicle_access_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '记录ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属区域ID（逻辑外键：parking_zone.id）',
    lane_id             BIGINT UNSIGNED     NOT NULL COMMENT '通道ID（逻辑外键：parking_lane.id）',
    lane_type           TINYINT             NOT NULL DEFAULT 1 COMMENT '通道类型：1入口 2出口 3双向',
    record_type         TINYINT             NOT NULL DEFAULT 1 COMMENT '记录类型：1入场 2出场',
    pass_type           TINYINT             NOT NULL DEFAULT 1 COMMENT '通行类型：1正常 2异常 3手动 4脱机 5预约',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码（大写存储，无牌车为系统生成编号）',
    plate_color         TINYINT             DEFAULT NULL COMMENT '车牌颜色',
    vehicle_type        VARCHAR(32)         DEFAULT 'TEMP' COMMENT '车辆类型',
    recognize_mode      TINYINT             NOT NULL DEFAULT 1 COMMENT '识别方式：1自动识别 2手动输入 3扫码 4蓝牙 5无牌车 6预约',
    confidence          DECIMAL(5, 2)       DEFAULT NULL COMMENT '识别置信度（%）',
    recognize_time      DATETIME(3)         NOT NULL COMMENT '识别时间',
    snapshot_image      VARCHAR(512)        DEFAULT NULL COMMENT '抓拍图片URL',
    match_result        TINYINT             NOT NULL DEFAULT 1 COMMENT '匹配结果：1正常放行 2已过期 3余额不足 4黑名单 5车位已满 6无记录 7重复入场',
    related_order_id    BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联订单ID（逻辑外键：parking_order.id）',
    receivable_amount   DECIMAL(18,2)       DEFAULT 0.00 COMMENT '应收金额',
    actual_amount       DECIMAL(18,2)       DEFAULT 0.00 COMMENT '实收金额',
    discount_amount     DECIMAL(18,2)       DEFAULT 0.00 COMMENT '优惠金额',
    payment_method      TINYINT             DEFAULT NULL COMMENT '支付方式：1微信 2余额 3优惠券 4免费 5组合',
    operator_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '操作员ID',
    operation_type      TINYINT             DEFAULT NULL COMMENT '操作类型：1自动放行 2手动放行 3异常处理 4免费放行 5强制放行',
    exception_reason    TINYINT             DEFAULT NULL COMMENT '异常原因：1无入场记录 2识别纠错 3收费争议 4黑名单 5重复入场 6无牌车',
    exception_id        BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联异常处理记录ID',
    device_id           BIGINT UNSIGNED     DEFAULT NULL COMMENT '识别设备ID（逻辑外键：device_camera.id）',
    device_ip           VARCHAR(64)         DEFAULT NULL COMMENT '设备IP',
    network_status      TINYINT             DEFAULT 1 COMMENT '网络状态：1在线 2脱机',
    remark              VARCHAR(512)        DEFAULT NULL COMMENT '备注',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_lane_id (lane_id),
    KEY idx_plate_number (plate_number),
    KEY idx_record_type (record_type),
    KEY idx_pass_type (pass_type),
    KEY idx_match_result (match_result),
    KEY idx_recognize_time (recognize_time),
    KEY idx_related_order_id (related_order_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆进出记录表';
```

**设计要点**：
- 车辆进出记录保留 3 年，超期自动归档冷存储；
- 不保存视频片段，仅保存抓拍图片（Q34 确认）；
- 抓拍图片保存策略：正常 30 天、异常 90 天、争议订单至解决后+30 天。

---

### 3.2.29 offline_sync_batch（脱机同步批次表）

```sql
CREATE TABLE offline_sync_batch (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '批次ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    sync_type           TINYINT             NOT NULL DEFAULT 1 COMMENT '下发类型：1全量 2增量 3指定车辆 4访客专用',
    target_lanes        JSON                DEFAULT NULL COMMENT '目标通道ID列表，NULL表示全部',
    vehicle_type_scope  JSON                DEFAULT NULL COMMENT '车辆类型范围',
    visitor_scope       TINYINT             DEFAULT NULL COMMENT '访客范围：1今日 2未来X天 3指定（访客专用）',
    sync_status         TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1待下发 2下发中 3成功 4部分成功 5失败',
    sync_time           DATETIME(3)         DEFAULT NULL COMMENT '实际执行时间',
    success_count       INT                 NOT NULL DEFAULT 0 COMMENT '成功数量',
    fail_count          INT                 NOT NULL DEFAULT 0 COMMENT '失败数量',
    fail_detail         JSON                DEFAULT NULL COMMENT '失败详情',
    operator_id         BIGINT UNSIGNED     NOT NULL COMMENT '操作人ID',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_sync_status (sync_status),
    KEY idx_sync_time (sync_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脱机同步批次表';
```

**设计要点**：
- 车辆新增/修改/续期后自动增量下发到岗亭；
- 每日凌晨自动全量同步；
- 网络恢复后自动上传离线期间的操作记录。

---

### 3.2.30 offline_sync_log（脱机同步日志表）【辅助表】

```sql
CREATE TABLE offline_sync_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '日志ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    batch_id            BIGINT UNSIGNED     NOT NULL COMMENT '批次ID（逻辑外键：offline_sync_batch.id）',
    lane_id             BIGINT UNSIGNED     NOT NULL COMMENT '目标通道ID（逻辑外键：parking_lane.id）',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码',
    vehicle_type        VARCHAR(32)         DEFAULT NULL COMMENT '车辆类型',
    valid_start         DATETIME(3)         DEFAULT NULL COMMENT '有效期开始',
    valid_end           DATETIME(3)         DEFAULT NULL COMMENT '有效期结束',
    sync_time           DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下发时间',
    sync_result         TINYINT             NOT NULL DEFAULT 1 COMMENT '结果：1成功 2失败',
    fail_reason         VARCHAR(256)        DEFAULT NULL COMMENT '失败原因',
    retry_count         INT                 NOT NULL DEFAULT 0 COMMENT '重试次数',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_batch_id (batch_id),
    KEY idx_lane_id (lane_id),
    KEY idx_plate_number (plate_number),
    KEY idx_sync_result (sync_result),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脱机同步日志表';
```

**设计要点**：
- 记录每次下发的单条明细，便于追踪失败原因；
- 失败记录支持自动重试。

---

### 3.2.31 visitor_unit（来访单位表）

```sql
CREATE TABLE visitor_unit (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '单位ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    name                VARCHAR(128)        NOT NULL COMMENT '单位名称',
    unit_type           TINYINT             NOT NULL DEFAULT 5 COMMENT '单位类型：1企业 2政府 3学校 4个人 5其他',
    contact_name        VARCHAR(64)         NOT NULL COMMENT '联系人',
    contact_phone       VARCHAR(32)         NOT NULL COMMENT '联系电话',
    address             VARCHAR(256)        DEFAULT NULL COMMENT '单位地址',
    visit_reason_template VARCHAR(256)      DEFAULT NULL COMMENT '来访事由模板',
    default_visitor_count INT               DEFAULT 1 COMMENT '默认访客人数',
    default_parking_hours INT               DEFAULT 2 COMMENT '默认停车时长（小时）',
    audit_strategy      TINYINT             NOT NULL DEFAULT 2 COMMENT '审核策略：1自动通过 2人工审核 3指定管理员',
    assigned_auditor_id BIGINT UNSIGNED     DEFAULT NULL COMMENT '指定审核人ID（逻辑外键：admin_account.id）',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1正常 2暂停 3注销',
    total_visit_count   INT                 NOT NULL DEFAULT 0 COMMENT '累计来访次数',
    total_vehicle_count INT                 NOT NULL DEFAULT 0 COMMENT '累计来访车辆数',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='来访单位表';
```

**设计要点**：
- 白名单单位可配置自动通过，提升审批效率；
- 单位档案用于批量管理访客和快速审批。

---

### 3.2.32 visitor_apply（访客申请表）

```sql
CREATE TABLE visitor_apply (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '申请ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    source              TINYINT             NOT NULL DEFAULT 1 COMMENT '申请来源：1小程序 2员工代申请 3单位批量 4现场登记',
    visitor_name        VARCHAR(64)         NOT NULL COMMENT '访客姓名',
    visitor_phone       VARCHAR(32)         NOT NULL COMMENT '访客手机号',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码（大写存储）',
    plate_color         TINYINT             DEFAULT 1 COMMENT '车牌颜色',
    visitor_unit_id     BIGINT UNSIGNED     DEFAULT NULL COMMENT '来访单位ID（逻辑外键：visitor_unit.id）',
    visitor_unit_name   VARCHAR(128)        DEFAULT NULL COMMENT '来访单位名称（手动填写时）',
    visited_person      VARCHAR(64)         NOT NULL COMMENT '被访人姓名',
    visited_department  VARCHAR(128)        DEFAULT NULL COMMENT '被访人部门',
    visit_reason        VARCHAR(256)        NOT NULL COMMENT '来访事由',
    visitor_count       INT                 DEFAULT 1 COMMENT '来访人数',
    appointment_start   DATETIME(3)         NOT NULL COMMENT '预约开始时间',
    appointment_end     DATETIME(3)         NOT NULL COMMENT '预约结束时间',
    charge_type         TINYINT             NOT NULL DEFAULT 1 COMMENT '收费方式：1免费 2按临停 3固定金额',
    charge_amount       DECIMAL(18,2)       DEFAULT 0.00 COMMENT '固定收费金额',
    allow_advance       TINYINT             DEFAULT 0 COMMENT '是否允许提前入场：0否 1是',
    advance_hours       INT                 DEFAULT 0 COMMENT '提前入场时长（小时）',
    overtime_strategy   TINYINT             DEFAULT 1 COMMENT '超时处理：1自动延期 2拒绝出场 3弹窗确认',
    materials           JSON                DEFAULT NULL COMMENT '申请材料图片URL',
    audit_status        TINYINT             NOT NULL DEFAULT 1 COMMENT '审核状态：1待审核 2审核中 3已通过 4已驳回 5已取消',
    auditor_id          BIGINT UNSIGNED     DEFAULT NULL COMMENT '审核人ID',
    audit_time          DATETIME(3)         DEFAULT NULL COMMENT '审核时间',
    audit_remark        VARCHAR(512)        DEFAULT NULL COMMENT '审核备注',
    sync_status         TINYINT             DEFAULT 0 COMMENT '下发状态：0未下发 1待下发 2已下发 3下发失败',
    sync_time           DATETIME(3)         DEFAULT NULL COMMENT '下发时间',
    actual_entry_time   DATETIME(3)         DEFAULT NULL COMMENT '实际入场时间',
    actual_exit_time    DATETIME(3)         DEFAULT NULL COMMENT '实际出场时间',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_plate_number (plate_number),
    KEY idx_visitor_unit_id (visitor_unit_id),
    KEY idx_audit_status (audit_status),
    KEY idx_appointment_time (appointment_start, appointment_end),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访客申请表';
```

**设计要点**：
- 访客预约线上和现场并重（Q4 确认）；
- 默认前 2 小时免费，超时按临停收费（Q5 确认）；
- 审核通过后自动下发车牌到岗亭；
- 不需要被访人确认，直接提交车场管理员审核（Q42 确认）。

---

### 3.2.33 visitor_record（访客预约记录表）【辅助表】

```sql
CREATE TABLE visitor_record (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '记录ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    apply_id            BIGINT UNSIGNED     NOT NULL COMMENT '申请ID（逻辑外键：visitor_apply.id）',
    visitor_no          VARCHAR(64)         NOT NULL COMMENT '访客编号',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码',
    visitor_name        VARCHAR(64)         DEFAULT NULL COMMENT '访客姓名',
    visitor_unit        VARCHAR(128)        DEFAULT NULL COMMENT '来访单位',
    visited_person      VARCHAR(64)         DEFAULT NULL COMMENT '被访人',
    visit_reason        VARCHAR(256)        DEFAULT NULL COMMENT '来访事由',
    appointment_start   DATETIME(3)         NOT NULL COMMENT '预约开始时间',
    appointment_end     DATETIME(3)         NOT NULL COMMENT '预约结束时间',
    actual_entry_time   DATETIME(3)         DEFAULT NULL COMMENT '实际入场时间',
    actual_exit_time    DATETIME(3)         DEFAULT NULL COMMENT '实际出场时间',
    parking_fee         DECIMAL(18,2)       DEFAULT 0.00 COMMENT '停车费用',
    discount_amount     DECIMAL(18,2)       DEFAULT 0.00 COMMENT '优惠抵扣',
    actual_pay          DECIMAL(18,2)       DEFAULT 0.00 COMMENT '实付金额',
    overtime_minutes    INT                 DEFAULT 0 COMMENT '超时时长（分钟）',
    overtime_fee        DECIMAL(18,2)       DEFAULT 0.00 COMMENT '超时费用',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1已完成 2已取消 3已过期 4未到场',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_visitor_no (tenant_id, visitor_no, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_apply_id (apply_id),
    KEY idx_plate_number (plate_number),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访客预约记录表';
```

**设计要点**：
- 记录访客预约的全流程结果，保留 3 年；
- `visitor_no` 作为访客唯一标识，用于预约二维码。

---

### 3.2.34 audit_flow（审核流程表）

```sql
CREATE TABLE audit_flow (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '流程ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    flow_name           VARCHAR(128)        NOT NULL COMMENT '流程名称',
    audit_type          TINYINT             NOT NULL DEFAULT 1 COMMENT '审核类型：1商家发券 2商家充值 3商家入驻 4特殊优惠策略 5储值调账 6固定车转免费车 7删除全部车辆',
    trigger_condition   VARCHAR(512)        DEFAULT NULL COMMENT '触发条件描述，如金额阈值',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_audit_type (audit_type),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核流程表';
```

**设计要点**：
- 支持自定义多级审批流程；
- 不同审核类型可配置不同流程和触发条件。

---

### 3.2.35 audit_node（审核节点表）【辅助表】

```sql
CREATE TABLE audit_node (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '节点ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    flow_id             BIGINT UNSIGNED     NOT NULL COMMENT '流程ID（逻辑外键：audit_flow.id）',
    node_order          INT                 NOT NULL DEFAULT 1 COMMENT '节点顺序',
    node_name           VARCHAR(128)        NOT NULL COMMENT '节点名称',
    approver_type       TINYINT             NOT NULL DEFAULT 1 COMMENT '审批人类型：1指定人员 2指定角色 3上级领导',
    approver_ids        JSON                DEFAULT NULL COMMENT '审批人ID列表',
    approve_mode        TINYINT             NOT NULL DEFAULT 1 COMMENT '审批方式：1或签 2会签',
    time_limit_hours    INT                 DEFAULT 24 COMMENT '审批时限（小时）',
    pass_condition      VARCHAR(256)        DEFAULT NULL COMMENT '通过条件',
    reject_strategy     TINYINT             DEFAULT 1 COMMENT '驳回策略：1直接驳回 2退回上一节点 3退回申请人',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_flow_id (flow_id),
    KEY idx_node_order (node_order),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核节点表';
```

**设计要点**：
- 多级审批节点按 `node_order` 顺序执行；
- 支持或签/会签两种审批模式。

---

### 3.2.36 audit_record（审核记录表）【辅助表】

```sql
CREATE TABLE audit_record (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '记录ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    flow_id             BIGINT UNSIGNED     NOT NULL COMMENT '流程ID（逻辑外键：audit_flow.id）',
    business_type       TINYINT             NOT NULL COMMENT '业务类型',
    business_id         BIGINT UNSIGNED     NOT NULL COMMENT '业务单ID',
    node_id             BIGINT UNSIGNED     NOT NULL COMMENT '节点ID（逻辑外键：audit_node.id）',
    node_name           VARCHAR(128)        DEFAULT NULL COMMENT '节点名称',
    approver_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '审批人ID',
    approve_result      TINYINT             NOT NULL DEFAULT 1 COMMENT '审批结果：1通过 2驳回 3转交',
    approve_opinion     VARCHAR(512)        DEFAULT NULL COMMENT '审批意见',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    approve_time        DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审批时间',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_flow_id (flow_id),
    KEY idx_business (business_type, business_id),
    KEY idx_node_id (node_id),
    KEY idx_approver_id (approver_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表';
```

**设计要点**：
- 记录每次审批节点的结果，支持流程追溯；
- 所有审批操作记录操作日志。

---

### 3.2.37 admin_account（管理员账号表）

```sql
CREATE TABLE admin_account (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '账号ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID（平台级管理员为0）',
    company_id          BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属公司ID（逻辑外键：company.id）',
    lot_id              BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属车场ID（三级管理员绑定单个车场）',
    username            VARCHAR(64)         NOT NULL COMMENT '登录账号',
    password            VARCHAR(256)        NOT NULL COMMENT '密码（BCrypt加密）',
    real_name           VARCHAR(64)         DEFAULT NULL COMMENT '姓名',
    phone               VARCHAR(32)         DEFAULT NULL COMMENT '手机号（AES加密存储）',
    email               VARCHAR(128)        DEFAULT NULL COMMENT '邮箱',
    role_level          TINYINT             NOT NULL DEFAULT 3 COMMENT '角色级别：1超级管理员 2租户管理员 3车场管理员 4自定义角色',
    custom_role_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '自定义角色ID（逻辑外键：custom_role.id）',
    data_scope          TINYINT             DEFAULT 1 COMMENT '数据范围：1全部车场 2指定车场 3本车场',
    managed_lot_ids     JSON                DEFAULT NULL COMMENT '管理车场ID列表',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用 3锁定',
    last_login_time     DATETIME(3)         DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip       VARCHAR(64)         DEFAULT NULL COMMENT '最后登录IP',
    login_fail_count    INT                 NOT NULL DEFAULT 0 COMMENT '登录失败次数',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_company_id (company_id),
    KEY idx_lot_id (lot_id),
    KEY idx_role_level (role_level),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员账号表';
```

**设计要点**：
- 支持三级管理员体系 + 自定义角色；
- 一个岗亭操作员可管理多个车场（Q21 确认），通过 `managed_lot_ids` 存储；
- 密码必须加密存储，登录失败超阈值自动锁定。

---

### 3.2.38 custom_role（自定义角色表）

```sql
CREATE TABLE custom_role (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '角色ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    role_name           VARCHAR(64)         NOT NULL COMMENT '角色名称',
    role_level          TINYINT             NOT NULL DEFAULT 2 COMMENT '角色级别：1平台级 2租户级 3车场级',
    data_scope          TINYINT             NOT NULL DEFAULT 1 COMMENT '数据范围：1全部车场 2指定车场 3本车场',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    created_by          BIGINT UNSIGNED     NOT NULL COMMENT '创建人ID',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_role_level (role_level),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自定义角色表';
```

**设计要点**：
- 支持租户自定义角色（如财务查看员、岗亭操作员）；
- 具体权限矩阵存储在 `role_permission` 表。

---

### 3.2.39 role_permission（角色权限矩阵表）【辅助表】

```sql
CREATE TABLE role_permission (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '权限ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    role_id             BIGINT UNSIGNED     NOT NULL COMMENT '角色ID（逻辑外键：custom_role.id 或 admin_account.id）',
    role_type           TINYINT             NOT NULL DEFAULT 2 COMMENT '角色类型：1系统角色 2自定义角色',
    module_code         VARCHAR(64)         NOT NULL COMMENT '模块代码',
    permission_code     VARCHAR(64)         NOT NULL COMMENT '权限代码：view/add/edit/delete/export/audit',
    granted             TINYINT             NOT NULL DEFAULT 0 COMMENT '是否授权：0否 1是',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_module_perm (tenant_id, role_id, role_type, module_code, permission_code, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_role_id (role_id),
    KEY idx_module_code (module_code),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限矩阵表';
```

**设计要点**：
- 细粒度权限矩阵，按模块和权限代码授权；
- 数据范围通过 `custom_role.data_scope` 控制。

---

### 3.2.40 device（设备主表）【辅助表】

```sql
CREATE TABLE device (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '设备ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    device_name         VARCHAR(128)        NOT NULL COMMENT '设备名称',
    device_type         TINYINT             NOT NULL DEFAULT 1 COMMENT '设备类型：1相机 2充电桩 3LED屏 4道闸 5地感【预留】',
    device_model        VARCHAR(64)         DEFAULT NULL COMMENT '设备型号',
    device_sn           VARCHAR(128)        NOT NULL COMMENT '设备序列号/SN码',
    install_location    VARCHAR(128)        DEFAULT NULL COMMENT '安装位置',
    online_status       TINYINT             NOT NULL DEFAULT 2 COMMENT '在线状态：1在线 2离线 3维护中',
    last_heartbeat_time DATETIME(3)         DEFAULT NULL COMMENT '最后心跳时间',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用 3故障',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_sn (tenant_id, device_sn, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_device_type (device_type),
    KEY idx_online_status (online_status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备主表';
```

**设计要点**：
- 当前主要管理相机设备，其他类型设备字段预留；
- `device_sn` 作为硬件唯一标识。

---

### 3.2.41 device_camera（相机设备扩展表）

```sql
CREATE TABLE device_camera (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '扩展ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    device_id           BIGINT UNSIGNED     NOT NULL COMMENT '设备ID（逻辑外键：device.id）',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    lane_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '绑定通道ID（逻辑外键：parking_lane.id）',
    ip_address          VARCHAR(64)         NOT NULL COMMENT '相机内网IP',
    port                INT                 NOT NULL DEFAULT 80 COMMENT '端口',
    firmware_version    VARCHAR(64)         DEFAULT NULL COMMENT '固件版本',
    auth_code_id        BIGINT UNSIGNED     DEFAULT NULL COMMENT '授权码ID（逻辑外键：auth_code.id）【预留】',
    display_config      JSON                DEFAULT NULL COMMENT '显示屏默认内容配置【预留】',
    voice_config        JSON                DEFAULT NULL COMMENT '语音播报配置【预留】',
    sync_whitelist      TINYINT             DEFAULT 1 COMMENT '是否同步白名单到本地：0否 1是',
    sync_blacklist      TINYINT             DEFAULT 1 COMMENT '是否同步黑名单到本地：0否 1是',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_device (tenant_id, device_id, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_device_id (device_id),
    KEY idx_lot_id (lot_id),
    KEY idx_lane_id (lane_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='相机设备扩展表';
```

**设计要点**：
- 平台只对接相机，显示屏/道闸通过相机统一下发控制（Q7 确认）；
- 显示屏/语音配置字段预留，后续协议完善后启用。

---

### 3.2.42 device_command_log（设备指令下发日志表）

```sql
CREATE TABLE device_command_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '日志ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    device_id           BIGINT UNSIGNED     NOT NULL COMMENT '目标设备ID（逻辑外键：device.id）',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    lane_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '通道ID',
    command_type        TINYINT             NOT NULL COMMENT '指令类型：1抬杆 2落杆 3常开 4常闭 5显示屏文字 6语音 7清屏 8重启 9固件升级 10参数同步【预留】',
    command_params      JSON                DEFAULT NULL COMMENT '指令参数JSON',
    response_status     TINYINT             DEFAULT 1 COMMENT '响应状态：1待响应 2成功 3失败 4超时',
    response_data       JSON                DEFAULT NULL COMMENT '响应数据JSON',
    send_time           DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间',
    response_time       DATETIME(3)         DEFAULT NULL COMMENT '响应时间',
    retry_count         INT                 NOT NULL DEFAULT 0 COMMENT '重试次数',
    operator_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '操作人ID',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_device_id (device_id),
    KEY idx_command_type (command_type),
    KEY idx_response_status (response_status),
    KEY idx_send_time (send_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备指令下发日志表';
```

**设计要点**：
- 当前阶段仅实现与相机相关的基础指令记录；
- 抬杆等控制指令待设备协议完善后实现，当前字段预留；
- 指令日志保留 1 年，超期归档。

---

### 3.2.43 device_heartbeat（设备心跳记录表）【辅助表】

```sql
CREATE TABLE device_heartbeat (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '心跳ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    device_id           BIGINT UNSIGNED     NOT NULL COMMENT '设备ID（逻辑外键：device.id）',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    heartbeat_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '心跳时间',
    ip_address          VARCHAR(64)         DEFAULT NULL COMMENT '设备IP',
    firmware_version    VARCHAR(64)         DEFAULT NULL COMMENT '固件版本',
    extra               JSON                DEFAULT NULL COMMENT '扩展信息',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_device_id (device_id),
    KEY idx_heartbeat_time (heartbeat_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备心跳记录表';
```

**设计要点**：
- 心跳记录保留 1 年，超期归档；
- 设备在线状态由最近心跳时间判断。

---

## 3.3 系统配置与日志辅助表

### 3.3.1 auth_code（授权码表）

```sql
CREATE TABLE auth_code (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '授权码ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL DEFAULT 0 COMMENT '租户ID（平台级为0）',
    auth_code           VARCHAR(64)         NOT NULL COMMENT '授权码',
    auth_type           TINYINT             NOT NULL DEFAULT 1 COMMENT '授权类型：1车场开通授权',
    lot_limit           INT                 DEFAULT NULL COMMENT '可开通车场数量，NULL表示不限',
    feature_modules     JSON                DEFAULT NULL COMMENT '功能模块权限：基础版/标准版/高级版',
    effective_start     DATETIME(3)         DEFAULT NULL COMMENT '有效期开始',
    effective_end       DATETIME(3)         DEFAULT NULL COMMENT '有效期结束',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1未使用 2已激活 3已过期 4已禁用',
    activated_by        BIGINT UNSIGNED     DEFAULT NULL COMMENT '激活人ID',
    activated_time      DATETIME(3)         DEFAULT NULL COMMENT '激活时间',
    generated_by        BIGINT UNSIGNED     NOT NULL COMMENT '生成人ID',
    generated_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_code (auth_code, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权码表';
```

**设计要点**：
- 授权码用于新车场/新租户开通平台账号（Q13 确认）；
- 非硬件设备激活码，非软件 License。

---

### 3.3.2 value_added_balance（车场增值余额表）

```sql
CREATE TABLE value_added_balance (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '余额ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID（逻辑外键：parking_lot.id）',
    available_balance   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '可用余额',
    frozen_balance      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
    total_recharge      DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '累计充值',
    total_consumption   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '累计消费',
    warning_threshold   DECIMAL(18,2)       DEFAULT 100.00 COMMENT '余额预警阈值',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot (tenant_id, lot_id, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车场增值余额表';
```

**设计要点**：
- 用于支付短信、语音播报、云存储、广告位等增值服务；
- 余额为 0 时暂停部分增值服务（Q38 确认）。

---

### 3.3.3 value_added_order（增值订单表）

```sql
CREATE TABLE value_added_order (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '订单ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    service_type        TINYINT             NOT NULL COMMENT '服务类型：1短信包 2语音包 3云存储 4广告位 5高级功能',
    order_amount        DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    payment_method      TINYINT             NOT NULL DEFAULT 1 COMMENT '支付方式：1增值余额 2微信 3支付宝',
    order_status        TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1待支付 2已支付 3已开通 4已退款',
    effective_time      DATETIME(3)         DEFAULT NULL COMMENT '生效时间',
    expiry_time         DATETIME(3)         DEFAULT NULL COMMENT '到期时间',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_service_type (service_type),
    KEY idx_order_status (order_status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='增值订单表';
```

**设计要点**：
- 记录车场购买增值服务的订单；
- 到期时间用于服务续期提醒。

---

### 3.3.4 sms_send_log（短信发送记录表）

```sql
CREATE TABLE sms_send_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '记录ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    sms_type            TINYINT             NOT NULL COMMENT '短信类型：1入场 2出场 3欠费 4到期 5营销',
    receive_phone       VARCHAR(32)         NOT NULL COMMENT '接收号码',
    content             TEXT                NOT NULL COMMENT '短信内容',
    channel             TINYINT             NOT NULL DEFAULT 1 COMMENT '通道：1平台默认 2车场自定义',
    send_status         TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1发送中 2成功 3失败',
    fail_reason         VARCHAR(256)        DEFAULT NULL COMMENT '失败原因',
    send_time           DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间',
    cost_amount         DECIMAL(18,4)       DEFAULT 0.0000 COMMENT '扣费金额',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_sms_type (sms_type),
    KEY idx_send_status (send_status),
    KEY idx_send_time (send_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信发送记录表';
```

**设计要点**：
- 短信通道支持平台默认 + 车场自定义，失败回退到平台通道；
- 短信记录保留 1 年。

---

### 3.3.5 vehicle_operation_log（车辆操作日志表）

```sql
CREATE TABLE vehicle_operation_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '日志ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    operator_id         BIGINT UNSIGNED     NOT NULL COMMENT '操作人ID',
    operator_role       TINYINT             NOT NULL DEFAULT 3 COMMENT '操作人角色：1一级 2二级 3三级 4系统',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_type      TINYINT             NOT NULL COMMENT '操作类型：1新增 2修改 3删除 4批量删除 5删除全部 6续期 7充值 8调账 9导入 10导出 11审核通过 12审核驳回',
    target_id           BIGINT UNSIGNED     DEFAULT NULL COMMENT '操作对象ID',
    target_plate        VARCHAR(16)         DEFAULT NULL COMMENT '操作对象车牌',
    vehicle_type        VARCHAR(32)         DEFAULT NULL COMMENT '车辆类型',
    lot_id              BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属车场ID',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    operation_result    TINYINT             NOT NULL DEFAULT 1 COMMENT '结果：1成功 2失败',
    fail_reason         VARCHAR(512)        DEFAULT NULL COMMENT '失败原因',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_operation_time (operation_time),
    KEY idx_operator_id (operator_id),
    KEY idx_operation_type (operation_type),
    KEY idx_target_plate (target_plate),
    KEY idx_lot_id (lot_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆操作日志表';
```

**设计要点**：
- 车辆操作日志保留 1 年，超期归档冷存储；
- 所有配置变更必须记录 `before_value`/`after_value`。

---

### 3.3.6 business_log（业务日志表）

```sql
CREATE TABLE business_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '日志ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    module              VARCHAR(64)         NOT NULL COMMENT '模块：车场管理/车辆管理/订单管理/优惠管理/访客管理/系统配置',
    operation_type      TINYINT             NOT NULL COMMENT '操作类型：1新增 2修改 3删除 4审核 5导出 6登录 7退出',
    operator_id         BIGINT UNSIGNED     NOT NULL COMMENT '操作人ID',
    operator_ip         VARCHAR(64)         DEFAULT NULL COMMENT '操作人IP',
    operation_time      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    operation_detail    JSON                DEFAULT NULL COMMENT '操作详情JSON',
    operation_result    TINYINT             NOT NULL DEFAULT 1 COMMENT '结果：1成功 2失败',
    before_value        JSON                DEFAULT NULL COMMENT '变更前JSON',
    after_value         JSON                DEFAULT NULL COMMENT '变更后JSON',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_module (module),
    KEY idx_operation_type (operation_type),
    KEY idx_operator_id (operator_id),
    KEY idx_operation_time (operation_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务日志表';
```

**设计要点**：
- 全平台业务操作日志，覆盖所有模块关键操作；
- 保留 1 年，超期归档。

---

## 3.4 充电优惠辅助表（可选）

### 3.4.1 charge_station（充电桩档案表）

```sql
CREATE TABLE charge_station (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '充电桩ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '所属区域ID',
    station_no          VARCHAR(64)         NOT NULL COMMENT '充电桩编号',
    station_name        VARCHAR(128)        DEFAULT NULL COMMENT '充电桩名称/位置',
    station_type        TINYINT             NOT NULL DEFAULT 1 COMMENT '桩类型：1自营 2第三方',
    third_party_code    VARCHAR(64)         DEFAULT NULL COMMENT '第三方充电桩编码',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用 3故障',
    version             INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_no (tenant_id, lot_id, station_no, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充电桩档案表';
```

**设计要点**：
- 支持自营充电桩和第三方充电桩（Q3 确认）；
- 第三方充电桩通过 `third_party_code` 与外部系统关联。

---

### 3.4.2 charge_record（充电记录表）

```sql
CREATE TABLE charge_record (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '记录ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    station_id          BIGINT UNSIGNED     NOT NULL COMMENT '充电桩ID（逻辑外键：charge_station.id）',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码',
    start_time          DATETIME(3)         NOT NULL COMMENT '充电开始时间',
    end_time            DATETIME(3)         DEFAULT NULL COMMENT '充电结束时间',
    duration_minutes    INT                 DEFAULT NULL COMMENT '充电时长（分钟）',
    electricity         DECIMAL(10, 2)      DEFAULT NULL COMMENT '充电电量（kWh）',
    charge_fee          DECIMAL(18,2)       DEFAULT 0.00 COMMENT '充电费用',
    charge_status       TINYINT             DEFAULT 1 COMMENT '状态：1充电中 2已完成 3异常中断',
    related_order_id    BIGINT UNSIGNED     DEFAULT NULL COMMENT '关联停车订单ID',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_station_id (station_id),
    KEY idx_plate_number (plate_number),
    KEY idx_charge_status (charge_status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充电记录表';
```

**设计要点**：
- 自营充电桩直接记录，第三方充电桩通过 API 对接获取；
- 充电记录保留 3 年。

---

### 3.4.3 charge_discount_log（充电优惠记录表）

```sql
CREATE TABLE charge_discount_log (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '记录ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED     NOT NULL COMMENT '车场ID',
    plate_number        VARCHAR(16)         NOT NULL COMMENT '车牌号码',
    parking_order_id    BIGINT UNSIGNED     NOT NULL COMMENT '停车订单ID',
    charge_record_id    BIGINT UNSIGNED     NOT NULL COMMENT '充电记录ID',
    original_parking_fee DECIMAL(18,2)      NOT NULL DEFAULT 0.00 COMMENT '原始停车费用',
    charge_minutes      INT                 NOT NULL DEFAULT 0 COMMENT '充电时长（分钟）',
    strategy_id         BIGINT UNSIGNED     DEFAULT NULL COMMENT '满足策略ID',
    discount_amount     DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '减免金额',
    final_parking_fee   DECIMAL(18,2)       NOT NULL DEFAULT 0.00 COMMENT '实付停车费用',
    verify_time         DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '核销时间',
    status              TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1已核销 2已退款',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_parking_order_id (parking_order_id),
    KEY idx_charge_record_id (charge_record_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充电优惠记录表';
```

**设计要点**：
- 记录充电后停车费用减免明细；
- 保留 3 年。

---

## 3.5 归档表

### 3.5.1 archive_data（归档数据索引表）

```sql
CREATE TABLE archive_data (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '归档ID（Snowflake）',
    tenant_id           BIGINT              NOT NULL COMMENT '租户ID',
    table_name          VARCHAR(64)         NOT NULL COMMENT '原表名',
    record_id           BIGINT UNSIGNED     NOT NULL COMMENT '原记录ID',
    archive_time        DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '归档时间',
    archive_path        VARCHAR(512)        NOT NULL COMMENT '冷存储路径',
    archive_size        BIGINT              DEFAULT NULL COMMENT '归档数据大小（字节）',
    data_start_time     DATETIME(3)         DEFAULT NULL COMMENT '数据时间范围开始',
    data_end_time       DATETIME(3)         DEFAULT NULL COMMENT '数据时间范围结束',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_table_name (table_name),
    KEY idx_archive_time (archive_time),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='归档数据索引表';
```

**设计要点**：
- 记录已归档到冷存储的数据索引；
- 归档数据只读，不可修改；
- 查询归档数据时显示“已归档”标识。

---

## 3.6 索引与性能建议

1. **所有业务表**必须包含 `tenant_id` 索引和 `deleted_at` 索引。
2. **高频查询字段**根据业务场景建立组合索引，例如：
   - `vehicle_access_log` 的 `(tenant_id, lot_id, recognize_time)`；
   - `parking_order` 的 `(tenant_id, lot_id, order_status, created_at)`；
   - `vehicle` 的 `(tenant_id, lot_id, plate_number)`。
3. **车牌字段**统一大写存储，避免大小写混合导致索引失效；模糊搜索建议使用前缀匹配或搜索引擎。
4. **时间字段**查询频繁，建议按时间范围分区（可选）或建立时间索引。
5. **大表**（如 `vehicle_access_log`、`parking_order`）建议按 `tenant_id` 或时间进行分表/分区规划，控制单表数据量。
6. **乐观锁字段** `version` 配合 `UPDATE ... WHERE version = #{oldVersion}` 使用，防止并发更新。
7. **敏感字段**（手机号、支付密钥等）建议使用应用层 AES 加密后存储，避免明文落库。

---

## 3.7 数据保留与归档策略

| 数据类型 | 保留期限 | 超期处理 | 查询权限 |
|---------|---------|---------|---------|
| 操作日志 | 1年 | 自动归档到冷存储 | 可查询，不可修改 |
| 车辆进出记录 | 3年 | 自动归档到冷存储 | 可查询，不可修改 |
| 车辆登记档案 | 永久 | 不归档 | 正常查询 |
| 收费订单 | 永久 | 不归档 | 正常查询 |
| 优惠券/积分流水 | 永久 | 不归档 | 正常查询 |
| 访客记录 | 3年 | 自动归档到冷存储 | 可查询，不可修改 |
| 商家优惠订单 | 永久 | 不归档 | 正常查询 |
| 系统配置 | 永久 | 不归档 | 正常查询 |
| 归档数据 | - | 冷存储 | 查询响应时间延长（<5秒） |

---

## 3.8 已删除/不实现表说明

根据 PRD 第 29 章确认结果，以下表不实现：

| 表名 | 原模块 | 删除原因 |
|-----|-------|---------|
| `parking_box` | 车辆登记 | Q10 确认不需要盒子车牌 |
| `ocr_record` | 系统配置 | Q15 确认不需要 OCR 证件识别 |
| `points_mall` / `points_mall_order` | 优惠管理 | Q12 确认当前不需要积分商城 |
| `profit_share` | 系统配置 | Q2 确认不需要车场分润 |

---

## 3.9 DDL 执行建议

1. 先创建 **基础表**（`company`、`parking_lot`、`parking_zone`、`parking_lane`）。
2. 再创建 **车辆相关表**（`department`、`vehicle`、`vehicle_multi_plate`、`vehicle_wallet`、`vehicle_wallet_log`、`lane_permission`、`blacklist`）。
3. 再创建 **收费与订单表**（`fee_rule`、`fee_rule_segment`、`parking_order`、`vehicle_access_log`）。
4. 再创建 **优惠与积分表**（`coupon`、`user_coupon`、`coupon_usage_log`、`member_points`、`points_transaction`、`merchant`、`merchant_coupon_stock`、`merchant_order`）。
5. 再创建 **访客与审核表**（`visitor_unit`、`visitor_apply`、`visitor_record`、`audit_flow`、`audit_node`、`audit_record`）。
6. 再创建 **系统配置与日志表**（`admin_account`、`custom_role`、`role_permission`、`auth_code`、`value_added_balance`、`value_added_order`、`sms_send_log`、`vehicle_operation_log`、`business_log`）。
7. 最后创建 **设备与充电表**（`device`、`device_camera`、`device_command_log`、`device_heartbeat`、`charge_station`、`charge_record`、`charge_discount_log`、`archive_data`）。
8. 所有 DDL 脚本建议通过 Flyway 分版本管理，禁止修改已发布迁移。
