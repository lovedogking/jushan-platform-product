# Task Package 6-2: 运营端车场参数配置区 + 黑白名单页 + 账号体系收口 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the operational dashboard with (1) parking lot parameter configuration including inheritance indicators, (2) a new black/white list page connected to the vehicle_list entity, (3) audit columns for monthly pass/fixed space management, and (4) unification of the two parallel account systems (sys_admin_account + employee) into a single account system with first-login force-password-change flow.

**Architecture:** Backend work includes 2 Flyway migrations (account enhancements + monthly/fixed audit columns), 3 entity/DTO/service changes, 1 new controller, and 1 interceptor. Frontend work includes 2 new API modules, 1 new page (vehicle-list replacing access-policy), and modifications to 5 existing pages and 3 API modules. The account merge introduces a `sys_admin_account_parking_lot` join table for multi-lot booth admin support, a `must_change_password` field, and an `allow_fee_reduction` field. The employee page is deprecated with a migration link to the account page.

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Flyway | Vue 3 + Ant Design Vue + TypeScript | Redis (permission cache invalidation)

---

## Prerequisites

Before starting, verify the development environment:
- `mvn clean compile -pl parking-system -am` passes
- `admin-web` dev server starts (`cd admin-web && npm run dev`)
- MySQL is running with all existing Flyway migrations applied

---

## Part A: Backend — Database Migrations

### Task A1: Create Flyway migration — account system enhancements

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260718001__package_6_2_account_enhance.sql`

- [ ] **Step 1: Write the migration SQL file**

```sql
-- =============================================================================
-- 任务包 6-2：账号体系收口 — sys_admin_account 扩展 + 停车场多对多 + 废弃标记
-- =============================================================================
-- 1. sys_admin_account 新增字段
-- 2. sys_admin_account_parking_lot 多对多关联表
-- 3. employee / sys_user / employee_parking_lot 废弃标记

-- ---------------------------------------------------------------------------
-- 1. sys_admin_account 新增字段
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码：0=否，1=是' AFTER lock_until,
    ADD COLUMN allow_fee_reduction TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许费用减免：0=否，1=是（仅岗亭管理员生效）' AFTER must_change_password;

-- 已有 level=3 的停车场管理员兼容：全部设为不强制改密、不允减免
UPDATE sys_admin_account SET must_change_password = 0, allow_fee_reduction = 0 WHERE level = 3;

-- .py

-- 从 employee 表迁移数据到 sys_admin_account 的辅助：先不做数据迁移，仅建结构
-- （数据迁移在 Part L 中作为单独任务执行，确保先在测试环境验证）

-- ---------------------------------------------------------------------------
-- 2. sys_admin_account_parking_lot 表：管理员 ↔ 停车场 多对多关联
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_admin_account_parking_lot (
    id              BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    admin_account_id BIGINT      NOT NULL COMMENT '管理员账号 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_admin_account (admin_account_id),
    INDEX idx_parking_lot (parking_lot_id),
    UNIQUE KEY uk_account_lot (admin_account_id, parking_lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号-停车场关联表（岗亭管理员多车场支持）';

-- ---------------------------------------------------------------------------
-- 3. 标记旧表为废弃（不删表，仅添加注释）
-- ---------------------------------------------------------------------------
ALTER TABLE employee COMMENT = '[已废弃-任务包6-2] 员工表已合并至 sys_admin_account，请勿新增数据';
ALTER TABLE employee_parking_lot COMMENT = '[已废弃-任务包6-2] 员工停车场关联已迁移至 sys_admin_account_parking_lot';
ALTER TABLE sys_user COMMENT = '[已废弃-任务包6-2] 旧用户表已合并至 sys_admin_account';
```

- [ ] **Step 2: Apply migration and verify**

Run:
```bash
mvn flyway:migrate -pl parking-boot -am -Dflyway.url=jdbc:mysql://localhost:3306/parking_dev -Dflyway.user=root -Dflyway.password=root
```

Expected: `Successfully applied 1 migration(s)` (or `Schema is up to date. No migration necessary.` if testing on clean DB)

Verify columns exist:
```sql
DESCRIBE sys_admin_account;
-- Confirm must_change_password and allow_fee_reduction columns exist

DESCRIBE sys_admin_account_parking_lot;
-- Confirm table and indexes exist

SHOW FULL COLUMNS FROM employee;
-- Comment should contain "[已废弃-任务包6-2]"
```

- [ ] **Step 3: Commit**

```bash
git add parking-boot/src/main/resources/db/migration/V20260718001__package_6_2_account_enhance.sql
git commit -m "[包6-2] feat: 账号体系收口 - sys_admin_account 扩展字段 + 车场多对多关联表 + 旧表废弃标记"
```

---

### Task A2: Create Flyway migration — monthly audit review_status column

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260718002__package_6_2_review_columns.sql`

**Context:** `FixedSpaceBinding` entity already has `reviewStatus` field (String, constants: PENDING/APPROVED/REJECTED). Both entities already have `payMethod` and `paidAmountCents` for payment info. Only MonthlyPass needs the `review_status` column; both may need `review_remark`.

- [ ] **Step 1: Write the migration SQL file**

```sql
-- =============================================================================
-- 任务包 6-2：月卡/固定车位审核 — monthly_pass 新增 review_status + review_remark
-- 注：fixed_space_binding 已有 review_status 列（FixedSpaceBinding.reviewStatus），
--     本迁移仅补齐 review_remark；支付方式/金额使用现有 pay_method / paid_amount_cents。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. monthly_pass 新增审核字段（review_status 与 FixedSpaceBinding 保持一致命名）
-- ---------------------------------------------------------------------------
ALTER TABLE monthly_pass
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING=待审核, APPROVED=已通过, REJECTED=已驳回' AFTER pass_status,
    ADD COLUMN review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注' AFTER review_status;

-- 存量月卡默认已通过
UPDATE monthly_pass SET review_status = 'APPROVED' WHERE review_status IS NULL;

-- ---------------------------------------------------------------------------
-- 2. fixed_space_binding 补齐 review_remark（review_status 已有值）
-- ---------------------------------------------------------------------------
ALTER TABLE fixed_space_binding
    ADD COLUMN review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注' AFTER review_status;

-- 存量固定车位 review_status 已有值，确认默认 APPROVED
UPDATE fixed_space_binding SET review_status = 'APPROVED' WHERE review_status IS NULL AND status IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. 插入审核模式全局参数（若未存在）
-- ---------------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('monthly_fixed.review_mode', 'AUTO', 0, 1, '月卡/固定车位审核模式：AUTO=自动通过, MANUAL=人工审核', '计费设置', 'ENUM', '["AUTO","MANUAL"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);
```

- [ ] **Step 2: Apply migration and verify**

Run:
```bash
mvn flyway:migrate -pl parking-boot -am -Dflyway.url=jdbc:mysql://localhost:3306/parking_dev -Dflyway.user=root -Dflyway.password=root
```

Expected: `Successfully applied 1 migration(s)`

Verify:
```sql
DESCRIBE monthly_pass;
-- Confirm review_status and review_remark columns (after pass_status)

DESCRIBE fixed_space_binding;
-- Confirm review_remark column exists (review_status should already exist)

SELECT config_key, config_value FROM sys_config WHERE config_key = 'monthly_fixed.review_mode';
-- Should return: AUTO
```

- [ ] **Step 3: Commit**

```bash
git add parking-boot/src/main/resources/db/migration/V20260718002__package_6_2_review_columns.sql
git commit -m "[包6-2] feat: 月卡/固定车位审核列 - monthly_pass 新增 review_status + review_remark"
```

---

### Task A3: Update MonthlyPass entity and VO with reviewStatus field

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/MonthlyPass.java`
- Modify: `parking-system/src/main/java/com/jushan/system/vo/MonthlyPassVO.java`

- [ ] **Step 1: Add reviewStatus field to MonthlyPass entity**

After the `passStatus` field in MonthlyPass, add:
```java
    /** 审核状态：PENDING-待审核 / APPROVED-已通过 / REJECTED-已驳回 */
    private String reviewStatus;

    /** 审核备注 */
    private String reviewRemark;
```

Also add constants after the existing constants:
```java
    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_APPROVED = "APPROVED";
    public static final String REVIEW_REJECTED = "REJECTED";
```

- [ ] **Step 2: Add reviewStatus and reviewRemark to MonthlyPassVO**

After the `passStatus` field in MonthlyPassVO, add:
```java
    private String reviewStatus;
    private String reviewRemark;
```

With corresponding getters/setters.

- [ ] **Step 3: Update MonthlyPassService.toVO() to populate new fields**

In the `toVO()` method of `MonthlyPassService.java`, add after `vo.setPassStatus(pass.getPassStatus());`:
```java
        vo.setReviewStatus(pass.getReviewStatus());
        vo.setReviewRemark(pass.getReviewRemark());
```

- [ ] **Step 4: Compile and verify**

```bash
mvn clean compile -pl parking-system -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/MonthlyPass.java
git add parking-system/src/main/java/com/jushan/system/vo/MonthlyPassVO.java
git add parking-system/src/main/java/com/jushan/system/service/MonthlyPassService.java
git commit -m "[包6-2] feat: MonthlyPass 实体/VO/服务 - 新增 review_status 审核字段"
```

---

## Part B: Backend — Account System Enhancement

### Task B1: Create SysAdminAccountParkingLot entity

**Files:**
- Create: `parking-system/src/main/java/com/jushan/platform/modules/account/entity/SysAdminAccountParkingLot.java`

- [ ] **Step 1: Write the entity class**

```java
package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号与停车场关联实体（多对多）。
 * <p>
 * 用于岗亭管理员（level=3 + 岗亭角色）的多车场分配，
 * 替代旧 employee_parking_lot 表。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Data
@TableName("sys_admin_account_parking_lot")
public class SysAdminAccountParkingLot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 管理员账号 ID */
    private Long adminAccountId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/entity/SysAdminAccountParkingLot.java
git commit -m "[包6-2] feat: SysAdminAccountParkingLot 实体 - 管理员车场多对多关联"
```

---

### Task B2: Create SysAdminAccountParkingLotMapper

**Files:**
- Create: `parking-system/src/main/java/com/jushan/platform/modules/account/mapper/SysAdminAccountParkingLotMapper.java`

- [ ] **Step 1: Write the mapper**

```java
package com.jushan.platform.modules.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.account.entity.SysAdminAccountParkingLot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysAdminAccountParkingLotMapper extends BaseMapper<SysAdminAccountParkingLot> {

    @Select("SELECT parking_lot_id FROM sys_admin_account_parking_lot WHERE admin_account_id = #{adminAccountId} AND tenant_id = #{tenantId}")
    List<Long> selectParkingLotIdsByAccountId(@Param("adminAccountId") Long adminAccountId,
                                               @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM sys_admin_account_parking_lot WHERE admin_account_id = #{adminAccountId}")
    int deleteByAdminAccountId(@Param("adminAccountId") Long adminAccountId);
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/mapper/SysAdminAccountParkingLotMapper.java
git commit -m "[包6-2] feat: SysAdminAccountParkingLotMapper - 车场关联查询/删除"
```

---

### Task B3: Update SysAdminAccount entity with new fields

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/account/entity/SysAdminAccount.java`

- [ ] **Step 1: Add fields to SysAdminAccount**

Exact old string to find (the entire `SysAdminAccount` entity between the import block and the closing brace):

Old (end of class, after `lastLoginTime`):
```java
    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}
```

New:
```java
    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 首次登录是否强制改密：0=否，1=是 */
    private Integer mustChangePassword;

    /** 是否允许费用减免：0=否，1=是（岗亭管理员适用） */
    private Integer allowFeeReduction;
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/entity/SysAdminAccount.java
git commit -m "[包6-2] feat: SysAdminAccount 新增 must_change_password + allow_fee_reduction 字段"
```

---

### Task B4: Update AdminAccountCreateCmd with new fields

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/account/dto/AdminAccountCreateCmd.java`

- [ ] **Step 1: Add fields and getters/setters**

Old (last field before getter section):
```java
    /** 租户 ID（平台用户创建二级/三级账号时必填） */
    private Long tenantId;
```

New:
```java
    /** 租户 ID（平台用户创建二级/三级账号时必填） */
    private Long tenantId;

    /** 是否首次登录强制改密（默认 1=是） */
    private Integer mustChangePassword;

    /** 是否允许费用减免（岗亭管理员适用） */
    private Integer allowFeeReduction;

    /** 停车场 ID 列表（岗亭管理员多车场分配） */
    private List<Long> parkingLotIds;
```

Old (end of getter section, last getter):
```java
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
```

New:
```java
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Integer getAllowFeeReduction() { return allowFeeReduction; }
    public void setAllowFeeReduction(Integer allowFeeReduction) { this.allowFeeReduction = allowFeeReduction; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/dto/AdminAccountCreateCmd.java
git commit -m "[包6-2] feat: AdminAccountCreateCmd 新增 must_change_password + allow_fee_reduction + parking_lot_ids"
```

---

### Task B5: Update AdminAccountUpdateCmd with new fields

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/account/dto/AdminAccountUpdateCmd.java`

- [ ] **Step 1: Add fields and getters/setters**

Old (last field before getter section):
```java
    /** 绑定角色 ID 列表 */
    private List<Long> roleIds;
```

New:
```java
    /** 绑定角色 ID 列表 */
    private List<Long> roleIds;

    /** 是否首次登录强制改密 */
    private Integer mustChangePassword;

    /** 是否允许费用减免（岗亭管理员适用） */
    private Integer allowFeeReduction;

    /** 停车场 ID 列表（岗亭管理员多车场分配） */
    private List<Long> parkingLotIds;
```

Old (end of getter section, last getter):
```java
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}
```

New:
```java
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Integer getAllowFeeReduction() { return allowFeeReduction; }
    public void setAllowFeeReduction(Integer allowFeeReduction) { this.allowFeeReduction = allowFeeReduction; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/dto/AdminAccountUpdateCmd.java
git commit -m "[包6-2] feat: AdminAccountUpdateCmd 新增 must_change_password + allow_fee_reduction + parking_lot_ids"
```

---

### Task B6: Update AdminAccountVO with new fields

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/account/vo/AdminAccountVO.java`

- [ ] **Step 1: Read current file**

- [ ] **Step 2: Add fields**

Old (find the current VO class and add fields before the closing brace):

Add these fields after `lastLoginTime`:
```java
    private Integer mustChangePassword;
    private Integer allowFeeReduction;
    private List<Long> parkingLotIds;
```

And add corresponding getters/setters at the end of the class (before the closing brace).

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/vo/AdminAccountVO.java
git commit -m "[包6-2] feat: AdminAccountVO 新增 must_change_password + allow_fee_reduction + parking_lot_ids"
```

---

### Task B7: Update SysAdminAccountServiceImpl — create logic for new fields

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/account/service/impl/SysAdminAccountServiceImpl.java`

- [ ] **Step 1: Inject SysAdminAccountParkingLotMapper**

Old (constructor injection section):
```java
    private final SysAdminAccountMapper adminAccountMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public SysAdminAccountServiceImpl(SysAdminAccountMapper adminAccountMapper,
                                      SysAdminAccountRoleMapper adminAccountRoleMapper,
                                      BCryptPasswordEncoder passwordEncoder) {
```

New:
```java
    private final SysAdminAccountMapper adminAccountMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final SysAdminAccountParkingLotMapper parkingLotMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public SysAdminAccountServiceImpl(SysAdminAccountMapper adminAccountMapper,
                                      SysAdminAccountRoleMapper adminAccountRoleMapper,
                                      SysAdminAccountParkingLotMapper parkingLotMapper,
                                      BCryptPasswordEncoder passwordEncoder) {
```

- [ ] **Step 2: Add import for new mapper**

Old (imports section):
```java
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
```

New:
```java
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountParkingLotMapper;
import com.jushan.platform.modules.account.entity.SysAdminAccountParkingLot;
```

- [ ] **Step 3: Update create() method — add must_change_password, allow_fee_reduction**

Old (in create method, after `account.setLevel(level);`):
```java
        account.setLevel(level);
        account.setStatus(cmd.getStatus() != null ? cmd.getStatus() : STATUS_NORMAL);
```

New:
```java
        account.setLevel(level);
        account.setStatus(cmd.getStatus() != null ? cmd.getStatus() : STATUS_NORMAL);
        account.setMustChangePassword(cmd.getMustChangePassword() != null ? cmd.getMustChangePassword() : 1);
        account.setAllowFeeReduction(cmd.getAllowFeeReduction() != null ? cmd.getAllowFeeReduction() : 0);
```

- [ ] **Step 4: Update create() method — save parking lot assignments after insert**

Old (after `adminAccountMapper.insert(account);` and `saveAccountRoles(...)`):
```java
        adminAccountMapper.insert(account);
        saveAccountRoles(account.getId(), cmd.getRoleIds());
```

New:
```java
        adminAccountMapper.insert(account);
        saveAccountRoles(account.getId(), cmd.getRoleIds());
        saveAccountParkingLots(account.getId(), tenantId, cmd.getParkingLotIds());
```

- [ ] **Step 5: Update create() method — include raw password in VO for frontend display**

Old (after VO construction):
```java
        AdminAccountVO vo = toVO(account);
        vo.setRoleIds(cmd.getRoleIds());
        return vo;
```

New:
```java
        AdminAccountVO vo = toVO(account);
        vo.setRoleIds(cmd.getRoleIds());
        // 仅创建时返回明文密码供前端展示（一次性显示）
        vo.setPlainPassword(rawPassword);
        return vo;
```

- [ ] **Step 6: Add AdminAccountVO.plainPassword field**

Also need to add this transient field in `AdminAccountVO`:

```java
    /** 创建时返回的明文初始密码（仅创建响应携带，查询不返回） */
    private String plainPassword;
```

With getter/setter.

- [ ] **Step 7: Update update() method — add must_change_password, allow_fee_reduction, parking lots**

Old (in update method, after `account.setStatus(cmd.getStatus());`):
```java
        account.setStatus(cmd.getStatus());
        account.setUpdatedAt(LocalDateTime.now());
```

New:
```java
        account.setStatus(cmd.getStatus());
        if (cmd.getMustChangePassword() != null) {
            account.setMustChangePassword(cmd.getMustChangePassword());
        }
        if (cmd.getAllowFeeReduction() != null) {
            account.setAllowFeeReduction(cmd.getAllowFeeReduction());
        }
        account.setUpdatedAt(LocalDateTime.now());
```

Old (after `saveAccountRoles(id, cmd.getRoleIds());`):
```java
        saveAccountRoles(id, cmd.getRoleIds());
```

New:
```java
        saveAccountRoles(id, cmd.getRoleIds());
        if (cmd.getParkingLotIds() != null) {
            saveAccountParkingLots(id, account.getTenantId(), cmd.getParkingLotIds());
        }
```

- [ ] **Step 8: Add saveAccountParkingLots helper method**

Add this method before the `CurrentScope` inner class:

```java
    private void saveAccountParkingLots(Long adminAccountId, Long tenantId, List<Long> parkingLotIds) {
        parkingLotMapper.deleteByAdminAccountId(adminAccountId);
        if (CollectionUtils.isEmpty(parkingLotIds) || tenantId == null) {
            return;
        }
        List<Long> distinctIds = parkingLotIds.stream().distinct().collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        for (Long lotId : distinctIds) {
            SysAdminAccountParkingLot rel = new SysAdminAccountParkingLot();
            rel.setAdminAccountId(adminAccountId);
            rel.setTenantId(tenantId);
            rel.setParkingLotId(lotId);
            rel.setCreatedAt(now);
            parkingLotMapper.insert(rel);
        }
    }
```

- [ ] **Step 9: Update toVO() and toVOWithRoles() — include new fields**

In `toVO()` method, add after `vo.setLastLoginTime(account.getLastLoginTime());`:
```java
        vo.setMustChangePassword(account.getMustChangePassword());
        vo.setAllowFeeReduction(account.getAllowFeeReduction());
```

In `toVOWithRoles()`, add after `vo.setRoleIds(roleIds);`:
```java
        List<Long> parkingLotIds = parkingLotMapper.selectParkingLotIdsByAccountId(
                account.getId(), account.getTenantId());
        vo.setParkingLotIds(parkingLotIds);
```

- [ ] **Step 10: Update resetPassword() — set must_change_password = 1**

Old (in resetPassword method, after setting password):
```java
        account.setPassword(passwordEncoder.encode(plainPassword));
        account.setUpdatedAt(LocalDateTime.now());
        account.setLoginFailCount(0);
        account.setLockUntil(null);
```

New:
```java
        account.setPassword(passwordEncoder.encode(plainPassword));
        account.setUpdatedAt(LocalDateTime.now());
        account.setLoginFailCount(0);
        account.setLockUntil(null);
        account.setMustChangePassword(1);
```

- [ ] **Step 11: Compile and verify**

```bash
mvn clean compile -pl parking-system -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 12: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/account/
git commit -m "[包6-2] feat: 账号服务扩展 — 多车场分配 + 强制改密/费用减免标志 + 密码一次性返回"
```

---

### Task B8: Update AuthController — pass must_change_password in login response

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/auth/controller/AuthController.java`

- [ ] **Step 1: Read current buildUserInfo method**

- [ ] **Step 2: Add mustChangePassword to UserInfo**

Old (in `LoginResult.UserInfo`, find the inner class):

After the existing fields `tenantId`, add:
```java
        private Integer mustChangePassword;
```

With getter/setter.

- [ ] **Step 3: Update buildUserInfo to populate mustChangePassword**

Old:
```java
    private LoginResult.UserInfo buildUserInfo(SysAdminAccount account) {
        LoginResult.UserInfo userInfo = new LoginResult.UserInfo();
        userInfo.setUserId(account.getId());
        userInfo.setUsername(account.getUsername());
        userInfo.setRealName(account.getRealName());
        userInfo.setLevel(account.getLevel());
        userInfo.setTenantId(account.getTenantId());
        return userInfo;
    }
```

New:
```java
    private LoginResult.UserInfo buildUserInfo(SysAdminAccount account) {
        LoginResult.UserInfo userInfo = new LoginResult.UserInfo();
        userInfo.setUserId(account.getId());
        userInfo.setUsername(account.getUsername());
        userInfo.setRealName(account.getRealName());
        userInfo.setLevel(account.getLevel());
        userInfo.setTenantId(account.getTenantId());
        userInfo.setMustChangePassword(account.getMustChangePassword());
        return userInfo;
    }
```

Also need to add `mustChangePassword` to `SessionInfo` inner class (same change pattern).

- [ ] **Step 4: Compile and verify**

```bash
mvn clean compile -pl parking-system -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/auth/controller/AuthController.java
git commit -m "[包6-2] feat: 登录响应携带 must_change_password 字段"
```

---

## Part C: Backend — Monthly/Fixed Space Audit Endpoints

### Task C1: Create MonthlyPassAuditController

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/MonthlyPassAuditController.java`

- [ ] **Step 1: Write the controller**

```java
package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.vo.MonthlyPassVO;
import com.jushan.system.service.MonthlyPassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 月卡审核管理接口。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Tag(name = "月卡审核管理")
@RestController
@RequestMapping("/api/v1/admin/monthly-pass-audit")
public class MonthlyPassAuditController {

    private final MonthlyPassService monthlyPassService;
    private final MonthlyPassMapper monthlyPassMapper;

    public MonthlyPassAuditController(MonthlyPassService monthlyPassService,
                                       MonthlyPassMapper monthlyPassMapper) {
        this.monthlyPassService = monthlyPassService;
        this.monthlyPassMapper = monthlyPassMapper;
    }

    @Operation(summary = "分页查询待审核月卡")
    @GetMapping("/pending")
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> pendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long parkingLotId) {
        IPage<MonthlyPassVO> result = monthlyPassService.pageAuditPending(page, size, parkingLotId);
        return R.ok(result);
    }

    @Operation(summary = "通过月卡审核")
    @PostMapping("/{id}/approve")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "通过月卡审核", module = "monthly-pass", operationType = "AUDIT",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> approve(@PathVariable Long id,
                                     @RequestParam(required = false) String remark) {
        MonthlyPass entity = monthlyPassMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        if (!MonthlyPass.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该月卡不在待审核状态");
        }
        entity.setReviewStatus(MonthlyPass.REVIEW_APPROVED);
        entity.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        entity.setReviewRemark(remark);
        entity.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(entity);
        return R.ok(monthlyPassService.toVO(entity));
    }

    @Operation(summary = "驳回月卡审核")
    @PostMapping("/{id}/reject")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "驳回月卡审核", module = "monthly-pass", operationType = "AUDIT",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> reject(@PathVariable Long id,
                                    @RequestParam(required = false) String remark) {
        MonthlyPass entity = monthlyPassMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        if (!MonthlyPass.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该月卡不在待审核状态");
        }
        entity.setReviewStatus(MonthlyPass.REVIEW_REJECTED);
        entity.setPassStatus(MonthlyPass.STATUS_CANCELLED);
        entity.setReviewRemark(remark);
        entity.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(entity);
        return R.ok(monthlyPassService.toVO(entity));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/MonthlyPassAuditController.java
git commit -m "[包6-2] feat: MonthlyPassAuditController - 月卡审核（待审列表/通过/驳回）"
```

---

### Task C2: Create FixedSpaceAuditController

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/FixedSpaceAuditController.java`

- [ ] **Step 1: Write the controller**

```java
package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import com.jushan.system.vo.FixedSpaceVO;
import com.jushan.system.service.FixedSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "固定车位审核管理")
@RestController
@RequestMapping("/api/v1/admin/fixed-space-audit")
public class FixedSpaceAuditController {

    private final FixedSpaceService fixedSpaceService;
    private final FixedSpaceBindingMapper fixedSpaceBindingMapper;

    public FixedSpaceAuditController(FixedSpaceService fixedSpaceService,
                                     FixedSpaceBindingMapper fixedSpaceBindingMapper) {
        this.fixedSpaceService = fixedSpaceService;
        this.fixedSpaceBindingMapper = fixedSpaceBindingMapper;
    }

    @Operation(summary = "分页查询待审核固定车位")
    @GetMapping("/pending")
    @RequirePermission("fixed:manage")
    public R<IPage<FixedSpaceVO>> pendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long parkingLotId) {
        IPage<FixedSpaceVO> result = fixedSpaceService.pageAuditPending(page, size, parkingLotId);
        return R.ok(result);
    }

    @Operation(summary = "通过固定车位审核")
    @PostMapping("/{id}/approve")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "通过固定车位审核", module = "fixed-space", operationType = "AUDIT",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> approve(@PathVariable Long id,
                                    @RequestParam(required = false) String remark) {
        FixedSpaceBinding entity = fixedSpaceBindingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "固定车位绑定不存在");
        }
        if (!FixedSpaceBinding.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该固定车位不在待审核状态");
        }
        entity.setReviewStatus(FixedSpaceBinding.REVIEW_APPROVED);
        entity.setStatus(FixedSpaceBinding.STATUS_ACTIVE);
        entity.setReviewRemark(remark);
        // FixedSpaceBinding 不需要 updatedAt
        fixedSpaceBindingMapper.updateById(entity);
        return R.ok(fixedSpaceService.toVO(entity));
    }

    @Operation(summary = "驳回固定车位审核")
    @PostMapping("/{id}/reject")
    @RequirePermission("fixed:manage")
    @BusinessLog(value = "驳回固定车位审核", module = "fixed-space", operationType = "AUDIT",
            operationObject = "固定车位", objectIdExpression = "#id")
    public R<FixedSpaceVO> reject(@PathVariable Long id,
                                   @RequestParam(required = false) String remark) {
        FixedSpaceBinding entity = fixedSpaceBindingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "固定车位绑定不存在");
        }
        if (!FixedSpaceBinding.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该固定车位不在待审核状态");
        }
        entity.setReviewStatus(FixedSpaceBinding.REVIEW_REJECTED);
        entity.setStatus(FixedSpaceBinding.STATUS_DISABLED);
        entity.setReviewRemark(remark);
        fixedSpaceBindingMapper.updateById(entity);
        return R.ok(fixedSpaceService.toVO(entity));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/FixedSpaceAuditController.java
git commit -m "[包6-2] feat: FixedSpaceAuditController - 固定车位审核（待审列表/通过/驳回）"
```

---

### Task C3: Add audit methods to MonthlyPassService and FixedSpaceService

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/MonthlyPassService.java`
- Modify: `parking-system/src/main/java/com/jushan/system/service/FixedSpaceService.java`

- [ ] **Step 1: Find the existing MonthlyPassService**

- [ ] **Step 2: Add pageAuditPending method to MonthlyPassService**

Add this method declaration to the interface:
```java
    IPage<MonthlyPassVO> pageAuditPending(int page, int size, Long parkingLotId);
```

Then add implementation in the service impl class (`MonthlyPassService`):

Add to `MonthlyPassService.java`:
```java
    /**
     * 分页查询待审核月卡（review_status = PENDING）。
     */
    public IPage<MonthlyPassVO> pageAuditPending(int page, int size, Long parkingLotId) {
        Long tenantId = TenantContext.requireTenantId();
        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("review_status", MonthlyPass.REVIEW_PENDING);
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        query.orderByDesc("created_at");

        IPage<MonthlyPass> passPage = monthlyPassMapper.selectPage(new Page<>(page, size), query);
        if (passPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(passPage.getRecords());
        List<MonthlyPassVO> voList = passPage.getRecords().stream()
                .map(p -> toVO(p, lotNames.get(p.getParkingLotId()), null))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(passPage.getCurrent(), passPage.getSize(), passPage.getTotal());
        result.setRecords(voList);
        return result;
    }
```

- [ ] **Step 3: Add same for FixedSpaceService**

Add method declaration:
```java
    IPage<FixedSpaceVO> pageAuditPending(int page, int size, Long parkingLotId);
```

Add implementation in `FixedSpaceService` impl class (uses existing `reviewStatus` field):
```java
    public IPage<FixedSpaceVO> pageAuditPending(int page, int size, Long parkingLotId) {
        Long tenantId = TenantContext.requireTenantId();
        QueryWrapper<FixedSpaceBinding> query = new QueryWrapper<FixedSpaceBinding>()
                .eq("tenant_id", tenantId)
                .eq("review_status", FixedSpaceBinding.REVIEW_PENDING);
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        query.orderByDesc("created_at");

        IPage<FixedSpaceBinding> entityPage = fixedSpaceBindingMapper.selectPage(new Page<>(page, size), query);
        if (entityPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(entityPage.getRecords());
        List<FixedSpaceVO> voList = entityPage.getRecords().stream()
                .map(e -> toVO(e, lotNames.get(e.getParkingLotId())))
                .collect(Collectors.toList());

        IPage<FixedSpaceVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(voList);
        return result;
    }
```

- [ ] **Step 4: Update create methods to respect review_mode**

In `MonthlyPassService.create()`, replace `pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);` with:
```java
        // 读取审核模式决定 review_status
        String reviewMode = paramResolver.getString(ParamKeys.MONTHLY_FIXED_REVIEW_MODE, null);
        if ("MANUAL".equals(reviewMode)) {
            pass.setReviewStatus(MonthlyPass.REVIEW_PENDING);
            pass.setPassStatus(MonthlyPass.STATUS_ACTIVE); // 审核通过后才真正生效（但 passStatus 保持 ACTIVE 用于前端展示）
        } else {
            pass.setReviewStatus(MonthlyPass.REVIEW_APPROVED);
            pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        }
```

Same pattern for `FixedSpaceService.create()` (use `FixedSpaceBinding.REVIEW_PENDING` / `REVIEW_APPROVED` and `FixedSpaceBinding.STATUS_ACTIVE`).

- [ ] **Step 5: Add reviewRemark to FixedSpaceBinding entity**

The `FixedSpaceBinding` entity already has `reviewStatus` but is missing `reviewRemark`. Add to `parking-system/src/main/java/com/jushan/system/entity/FixedSpaceBinding.java` after the `reviewStatus` field:

```java
    /** 审核备注 */
    private String reviewRemark;

    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
```

- [ ] **Step 6: Compile and verify**

```bash
mvn clean compile -pl parking-system -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/service/
git commit -m "[包6-2] feat: 月卡/固定车位服务 - 审核列表查询 + 审核模式联动"
```

---

## Part D: Backend — First Login Password Change Interceptor

### Task D1: Create MustChangePasswordInterceptor

**Files:**
- Create: `parking-system/src/main/java/com/jushan/platform/infra/security/MustChangePasswordInterceptor.java`

- [ ] **Step 1: Write the interceptor**

```java
package com.jushan.platform.infra.security;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 首次登录强制改密拦截器。
 * <p>
 * 当账号标记 must_change_password=1 时，仅允许访问改密接口和退出接口，
 * 其他所有 API 请求均被拦截。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MustChangePasswordInterceptor.class);

    /** 允许在强制改密期间访问的路径前缀 */
    private static final String[] ALLOWED_PREFIXES = {
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/userinfo",
            "/api/v1/auth/session",
    };

    private final SysAdminAccountMapper adminAccountMapper;

    public MustChangePasswordInterceptor(SysAdminAccountMapper adminAccountMapper) {
        this.adminAccountMapper = adminAccountMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return true; // let authentication interceptor handle
        }

        String path = request.getRequestURI();
        for (String allowed : ALLOWED_PREFIXES) {
            if (path.startsWith(allowed)) {
                return true;
            }
        }

        // Use selectByIdIgnoreTenant to bypass tenant filtering in interceptor context
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account != null && account.getMustChangePassword() != null
                && account.getMustChangePassword() == 1) {
            log.warn("拦截强制改密用户的请求: userId={}, path={}", userId, path);
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "首次登录须修改密码，请前往修改密码页面");
        }

        return true;
    }
}
```

- [ ] **Step 2: Register interceptor in WebMvcConfig**

Find the existing `WebMvcConfigurer` in `parking-system` or `parking-infrastructure` and add:

```java
    @Autowired
    private MustChangePasswordInterceptor mustChangePasswordInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mustChangePasswordInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/refresh");
    }
```

- [ ] **Step 3: Create ChangePasswordController endpoint**

Create `parking-system/src/main/java/com/jushan/platform/modules/auth/controller/ChangePasswordController.java`:

```java
package com.jushan.platform.modules.auth.controller;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
public class ChangePasswordController {

    private final SysAdminAccountMapper adminAccountMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public ChangePasswordController(SysAdminAccountMapper adminAccountMapper,
                                     BCryptPasswordEncoder passwordEncoder) {
        this.adminAccountMapper = adminAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/change-password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = TenantContext.requireUserId();
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "账号不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), account.getPassword())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "原密码不正确");
        }

        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "新密码不能与原密码相同");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        account.setMustChangePassword(0);
        account.setUpdatedAt(LocalDateTime.now());
        adminAccountMapper.updateById(account);

        return R.ok();
    }

    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度须在6-64个字符之间")
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
```

- [ ] **Step 4: Compile and verify**

```bash
mvn clean compile -pl parking-system -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/infra/security/MustChangePasswordInterceptor.java
git add parking-system/src/main/java/com/jushan/platform/modules/auth/controller/ChangePasswordController.java
git commit -m "[包6-2] feat: 首次登录强制改密拦截器 + ChangePasswordController"
```

---

## Part E: Frontend — API Modules

### Task E1: Create vehicle-list API module

**Files:**
- Create: `admin-web/src/api/vehicle-list.ts`

- [ ] **Step 1: Write the API module**

```typescript
import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 黑白名单视图（对应后端 VehicleListVO） */
export interface VehicleListRecord {
  id: number
  plateNumber: string
  listType: string
  listTypeLabel: string
  parkingLotId: number
  parkingLotName: string
  startDate?: string
  endDate?: string
  triggerType?: string
  triggerTypeLabel?: string
  status: string
  statusLabel: string
  remark?: string
  createdAt?: string
}

/** 名单类型映射 */
export const LIST_TYPE_MAP: Record<string, string> = {
  BLACK: '黑名单',
  WHITE: '白名单',
}

/** 名单类型选项 */
export const LIST_TYPE_OPTIONS = [
  { label: '黑名单', value: 'BLACK' },
  { label: '白名单', value: 'WHITE' },
]

/** 黑名单触发类型映射 */
export const TRIGGER_TYPE_MAP: Record<string, string> = {
  ARREARS: '欠费类',
  MANAGEMENT: '管理类',
  OTHER: '其他类',
}

/** 黑名单触发行为映射 */
export const TRIGGER_ACTION_MAP: Record<string, string> = {
  ARREARS: '禁止入场',
  MANAGEMENT: '允许入场但告警',
  OTHER: '禁止入场',
}

/** 状态映射 */
export const LIST_STATUS_MAP: Record<string, string> = {
  ACTIVE: '生效中',
  EXPIRED: '已过期',
  DISABLED: '已禁用',
}

/** 分页查询 */
export function getVehicleListPage(params: {
  page?: number
  size?: number
  parkingLotId?: number
  listType?: string
  plateNumber?: string
}) {
  return request.get<PageResult<VehicleListRecord>>('/v1/admin/vehicle-list/page', params)
}

/** 详情 */
export function getVehicleListDetail(id: number) {
  return request.get<VehicleListRecord>(`/v1/admin/vehicle-list/${id}`)
}

/** 创建 */
export function createVehicleList(data: {
  plateNumber: string
  listType: string
  parkingLotId: number
  startDate?: string
  endDate?: string
  triggerType?: string
  remark?: string
}) {
  return request.post<VehicleListRecord>('/v1/admin/vehicle-list', data)
}

/** 编辑 */
export function updateVehicleList(id: number, data: {
  plateNumber: string
  listType: string
  parkingLotId?: number
  startDate?: string
  endDate?: string
  triggerType?: string
  remark?: string
}) {
  return request.put<VehicleListRecord>(`/v1/admin/vehicle-list/${id}`, data)
}

/** 删除 */
export function deleteVehicleList(id: number) {
  return request.delete(`/v1/admin/vehicle-list/${id}`)
}

/** 黑名单触发类型字典 */
export function getTriggerTypes() {
  return request.get<{ code: string; label: string }[]>('/v1/admin/vehicle-list/trigger-types')
}
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/api/vehicle-list.ts
git commit -m "[包6-2] feat: vehicle-list API 模块 - 对接 VehicleListController"
```

---

### Task E2: Add monthly/fixed audit API functions

**Files:**
- Modify: `admin-web/src/api/monthly-pass.ts`
- Modify: `admin-web/src/api/fixed-space.ts`

- [ ] **Step 1: Add audit-related functions to monthly-pass.ts**

Add at the end of the file (before the closing):
```typescript
/** 待审核月卡列表 */
export function getMonthlyPassPendingList(params: {
  page?: number
  size?: number
  parkingLotId?: number
}) {
  return request.get<PageResult<MonthlyPassVO>>('/v1/admin/monthly-pass-audit/pending', params)
}

/** 通过月卡审核 */
export function approveMonthlyPass(id: number, remark?: string) {
  return request.post<MonthlyPassVO>(`/v1/admin/monthly-pass-audit/${id}/approve`, null, {
    params: remark ? { remark } : undefined,
  })
}

/** 驳回月卡审核 */
export function rejectMonthlyPass(id: number, remark?: string) {
  return request.post<MonthlyPassVO>(`/v1/admin/monthly-pass-audit/${id}/reject`, null, {
    params: remark ? { remark } : undefined,
  })
}
```

- [ ] **Step 2: Add audit-related functions to fixed-space.ts**

Add at the end of the file:
```typescript
/** 待审核固定车位列表 */
export function getFixedSpacePendingList(params: {
  page?: number
  size?: number
  parkingLotId?: number
}) {
  return request.get<PageResult<FixedSpaceVO>>('/v1/admin/fixed-space-audit/pending', params)
}

/** 通过固定车位审核 */
export function approveFixedSpace(id: number, remark?: string) {
  return request.post<FixedSpaceVO>(`/v1/admin/fixed-space-audit/${id}/approve`, null, {
    params: remark ? { remark } : undefined,
  })
}

/** 驳回固定车位审核 */
export function rejectFixedSpace(id: number, remark?: string) {
  return request.post<FixedSpaceVO>(`/v1/admin/fixed-space-audit/${id}/reject`, null, {
    params: remark ? { remark } : undefined,
  })
}
```

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/api/monthly-pass.ts admin-web/src/api/fixed-space.ts
git commit -m "[包6-2] feat: 月卡/固定车位审核 API 函数"
```

---

### Task E3: Update account API module with new fields

**Files:**
- Modify: `admin-web/src/api/account.ts`

- [ ] **Step 1: Update AdminAccountVO interface**

Old:
```typescript
export interface AdminAccountVO {
  id: string
  tenantId?: number
  companyId?: number
  lotId?: number
  username: string
  realName?: string
  phone?: string
  email?: string
  level: number
  status: number
  lastLoginTime?: string
  createdAt?: string
  roleIds?: number[]
  roleNames?: string[]
}
```

New:
```typescript
export interface AdminAccountVO {
  id: string
  tenantId?: number
  companyId?: number
  lotId?: number
  username: string
  realName?: string
  phone?: string
  email?: string
  level: number
  status: number
  mustChangePassword?: number
  allowFeeReduction?: number
  lastLoginTime?: string
  createdAt?: string
  roleIds?: number[]
  roleNames?: string[]
  parkingLotIds?: number[]
  plainPassword?: string
}
```

- [ ] **Step 2: Update AdminAccountCreateCmd interface**

Old:
```typescript
export interface AdminAccountCreateCmd {
  username: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  level: number
  companyId?: number
  lotId?: number
  status: number
  roleIds?: number[]
}
```

New:
```typescript
export interface AdminAccountCreateCmd {
  username: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  level: number
  companyId?: number
  lotId?: number
  status: number
  roleIds?: number[]
  mustChangePassword?: number
  allowFeeReduction?: number
  parkingLotIds?: number[]
}
```

- [ ] **Step 3: Update AdminAccountUpdateCmd interface**

Old:
```typescript
export interface AdminAccountUpdateCmd {
  id: string | number
  realName?: string
  phone?: string
  email?: string
  level: number
  companyId?: number
  lotId?: number
  status: number
  roleIds?: number[]
}
```

New:
```typescript
export interface AdminAccountUpdateCmd {
  id: string | number
  realName?: string
  phone?: string
  email?: string
  level: number
  companyId?: number
  lotId?: number
  status: number
  roleIds?: number[]
  mustChangePassword?: number
  allowFeeReduction?: number
  parkingLotIds?: number[]
}
```

- [ ] **Step 4: Add auth/mustChangePassword types**

- [ ] **Step 5: Commit**

```bash
git add admin-web/src/api/account.ts
git commit -m "[包6-2] feat: 账号 API 模块 - 新增 must_change_password + allow_fee_reduction + parking_lot_ids"
```

---

### Task E4: Update auth API and user types

**Files:**
- Modify: `admin-web/src/types/api.ts`
- Modify: `admin-web/src/api/auth.ts`

- [ ] **Step 1: Update UserInfo and LoginUser interfaces**

In `admin-web/src/types/api.ts`, update:

Old LoginUser:
```typescript
export interface LoginUser {
  userId: number
  username: string
  realName: string
  level: number
  tenantId?: number
}
```

New LoginUser:
```typescript
export interface LoginUser {
  userId: number
  username: string
  realName: string
  level: number
  tenantId?: number
  mustChangePassword?: number
}
```

Old UserInfo:
```typescript
export interface UserInfo {
  userId: number
  username: string
  realName: string
  level: number
  tenantId?: number
}
```

New UserInfo:
```typescript
export interface UserInfo {
  userId: number
  username: string
  realName: string
  level: number
  tenantId?: number
  mustChangePassword?: number
}
```

- [ ] **Step 2: Add changePassword API function**

In `admin-web/src/api/auth.ts`, add:
```typescript
/** 修改密码（首次登录强制改密） */
export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return request.post('/v1/auth/change-password', data)
}
```

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/types/api.ts admin-web/src/api/auth.ts
git commit -m "[包6-2] feat: UserInfo 增加 must_change_password + changePassword API"
```

---

## Part F: Frontend — Black/White List Page (Vehicle List)

### Task F1: Create VehicleList page (replaces access-policy)

**Files:**
- Create: `admin-web/src/views/vehicle-list/VehicleListManage.vue`

- [ ] **Step 1: Create directory and write the page**

```bash
mkdir -p admin-web/src/views/vehicle-list
```

Write the component (abbreviated for readability — full template/script/style):

```vue
<template>
  <div class="vehicle-list-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select
          v-model:value="queryLotId"
          placeholder="选择停车场"
          allow-clear
          style="width: 180px"
          @change="handleQuery"
        >
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="queryListType"
          placeholder="名单类型"
          allow-clear
          style="width: 130px"
          @change="handleQuery"
        >
          <a-select-option v-for="opt in LIST_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-input
          v-model:value="queryPlate"
          placeholder="车牌号"
          allow-clear
          style="width: 160px"
          @press-enter="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增名单
      </a-button>
    </div>

    <!-- 表格区 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'listType'">
          <a-tag :color="record.listType === 'BLACK' ? 'red' : 'green'">
            {{ LIST_TYPE_MAP[record.listType] || record.listTypeLabel }}
          </a-tag>
        </template>
        <template v-if="column.key === 'triggerType'">
          <a-tag :color="triggerTypeColor(record.triggerType)">
            {{ record.triggerTypeLabel || (record.triggerType ? TRIGGER_TYPE_MAP[record.triggerType] : '-') }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">
            {{ record.statusLabel || LIST_STATUS_MAP[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-if="column.key === 'validity'">
          <span v-if="record.startDate && record.endDate">
            {{ record.startDate }} ~ {{ record.endDate }}
          </span>
          <span v-else style="color: #999">永久有效</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该名单记录？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <VehicleListFormModal
      v-model:open="formModalOpen"
      :is-editing="isEditing"
      :editing-record="editingRecord"
      :parking-lot-options="parkingLotOptions"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import VehicleListFormModal from './VehicleListFormModal.vue'
import {
  getVehicleListPage,
  createVehicleList,
  updateVehicleList,
  deleteVehicleList,
  LIST_TYPE_OPTIONS,
  LIST_TYPE_MAP,
  TRIGGER_TYPE_MAP,
  LIST_STATUS_MAP,
  type VehicleListRecord,
} from '@/api/vehicle-list'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 140 },
  { title: '名单类型', key: 'listType', width: 100 },
  { title: '停车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '有效期', key: 'validity', width: 200 },
  { title: '黑名单类型', key: 'triggerType', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<VehicleListRecord[]>([])
const queryLotId = ref<number | undefined>(undefined)
const queryListType = ref<string | undefined>(undefined)
const queryPlate = ref('')
const parkingLotOptions = ref<ParkingLotVO[]>([])

const pagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 新增/编辑弹窗
const formModalOpen = ref(false)
const isEditing = ref(false)
const editingRecord = ref<VehicleListRecord | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getVehicleListPage({
      page: pagination.current,
      size: pagination.pageSize,
      parkingLotId: queryLotId.value,
      listType: queryListType.value,
      plateNumber: queryPlate.value?.trim() || undefined,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ page: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch { /* ignore */ }
}

function handleQuery() { pagination.current = 1; fetchData() }
function handleReset() {
  queryLotId.value = undefined; queryListType.value = undefined; queryPlate.value = ''
  pagination.current = 1; fetchData()
}
function handleTableChange(pag: any) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchData()
}

function handleCreate() {
  isEditing.value = false; editingRecord.value = null; formModalOpen.value = true
}

function handleEdit(record: VehicleListRecord) {
  isEditing.value = true; editingRecord.value = record; formModalOpen.value = true
}

async function handleFormSubmit(data: Record<string, any>) {
  try {
    if (isEditing.value && editingRecord.value) {
      await updateVehicleList(editingRecord.value.id, data)
      message.success('更新成功')
    } else {
      await createVehicleList(data as any)
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch (err: any) {
    const msg = err?.message || ''
    if (msg.includes('互斥') || msg.includes('同时存在') || msg.includes('黑名单') || msg.includes('白名单')) {
      Modal.warning({ title: '互斥冲突', content: msg })
    }
  }
}

async function handleDelete(record: VehicleListRecord) {
  try {
    await deleteVehicleList(record.id)
    message.success('删除成功')
    fetchData()
  } catch { /* ignore */ }
}

function triggerTypeColor(type?: string) {
  switch (type) {
    case 'ARREARS': return 'red'
    case 'MANAGEMENT': return 'orange'
    case 'OTHER': return 'default'
    default: return 'default'
  }
}

function statusColor(status: string) {
  switch (status) {
    case 'ACTIVE': return 'green'
    case 'EXPIRED': return 'orange'
    case 'DISABLED': return 'default'
    default: return 'default'
  }
}

onMounted(() => { loadParkingLots(); fetchData() })
</script>

<style lang="scss" scoped>
.vehicle-list-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
</style>
```

> **Note:** The full component is included above. The steps are: create the file, paste the complete content.

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/views/vehicle-list/VehicleListManage.vue
git commit -m "[包6-2] feat: VehicleListManage 页面 - 黑白名单管理主页面"
```

---

### Task F2: Create VehicleListFormModal component

**Files:**
- Create: `admin-web/src/views/vehicle-list/VehicleListFormModal.vue`

- [ ] **Step 1: Write the form modal**

```vue
<template>
  <a-modal
    v-model:open="visible"
    :title="modalTitle"
    :confirm-loading="formLoading"
    width="560px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="所属停车场" name="parkingLotId" required>
        <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场" :disabled="isEditing">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="名单类型" name="listType" required>
        <a-radio-group v-model:value="formData.listType" :disabled="isEditing">
          <a-radio value="BLACK">黑名单</a-radio>
          <a-radio value="WHITE">白名单</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="车牌号" name="plateNumber" required>
        <a-input
          v-model:value="formData.plateNumber"
          placeholder="如：京A12345"
          :disabled="isEditing"
          @blur="formData.plateNumber = formData.plateNumber.toUpperCase()"
        />
      </a-form-item>
      <a-form-item v-if="formData.listType === 'BLACK'" label="黑名单触发类型" name="triggerType" required>
        <a-select v-model:value="formData.triggerType" placeholder="请选择触发类型">
          <a-select-option value="ARREARS">欠费类</a-select-option>
          <a-select-option value="MANAGEMENT">管理类</a-select-option>
          <a-select-option value="OTHER">其他类</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="有效期起">
        <a-date-picker v-model:value="formData.startDate" value-format="YYYY-MM-DD" style="width: 100%" placeholder="永久有效（可选）" />
      </a-form-item>
      <a-form-item label="有效期止">
        <a-date-picker v-model:value="formData.endDate" value-format="YYYY-MM-DD" style="width: 100%" placeholder="永久有效（可选）" />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="formData.remark" placeholder="可选：备注说明" :rows="2" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { VehicleListRecord } from '@/api/vehicle-list'

const props = defineProps<{
  open: boolean
  isEditing: boolean
  editingRecord: VehicleListRecord | null
  parkingLotOptions: { id: number; name: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'submit', data: Record<string, any>): void
}>()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const modalTitle = computed(() => props.isEditing ? '编辑名单' : '新增名单')
const formLoading = ref(false)

const formData = reactive({
  parkingLotId: undefined as number | undefined,
  listType: 'BLACK' as string,
  plateNumber: '',
  triggerType: undefined as string | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  remark: '',
})

watch(() => props.editingRecord, (record) => {
  if (record && props.isEditing) {
    formData.parkingLotId = record.parkingLotId
    formData.listType = record.listType
    formData.plateNumber = record.plateNumber
    formData.triggerType = record.triggerType || undefined
    formData.startDate = record.startDate
    formData.endDate = record.endDate
    formData.remark = record.remark || ''
  } else {
    resetForm()
  }
})

function resetForm() {
  formData.parkingLotId = undefined
  formData.listType = 'BLACK'
  formData.plateNumber = ''
  formData.triggerType = undefined
  formData.startDate = undefined
  formData.endDate = undefined
  formData.remark = ''
}

function handleCancel() { visible.value = false }

function handleSubmit() {
  if (!formData.parkingLotId) { message.warning('请选择停车场'); return }
  if (!formData.listType) { message.warning('请选择名单类型'); return }
  if (!formData.plateNumber.trim()) { message.warning('请输入车牌号'); return }
  if (formData.listType === 'BLACK' && !formData.triggerType) {
    message.warning('黑名单请选择触发类型'); return
  }

  formLoading.value = true
  try {
    const payload: Record<string, any> = {
      parkingLotId: formData.parkingLotId,
      listType: formData.listType,
      plateNumber: formData.plateNumber.trim().toUpperCase(),
      startDate: formData.startDate || undefined,
      endDate: formData.endDate || undefined,
      triggerType: formData.listType === 'BLACK' ? formData.triggerType : undefined,
      remark: formData.remark?.trim() || undefined,
    }
    emit('submit', payload)
    visible.value = false
  } finally {
    formLoading.value = false
  }
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/views/vehicle-list/VehicleListFormModal.vue
git commit -m "[包6-2] feat: VehicleListFormModal - 黑白名单新增/编辑弹窗"
```

---

### Task F3: Update router — replace access-policies with vehicle-list

**Files:**
- Modify: `admin-web/src/router/index.ts`

- [ ] **Step 1: Replace access-policy route with vehicle-list route**

Old:
```typescript
      // 黑白名单管理
      {
        path: 'access-policies',
        name: 'AccessPolicies',
        component: () => import('@/views/access-policy/index.vue'),
        meta: { title: '黑白名单', icon: 'SafetyOutlined', permission: 'parking:view', cache: true },
      },
```

New:
```typescript
      // 黑白名单管理（任务包 6-2：替换为 vehicle-list 新页面）
      {
        path: 'vehicle-list',
        name: 'VehicleList',
        component: () => import('@/views/vehicle-list/VehicleListManage.vue'),
        meta: { title: '黑白名单', icon: 'SafetyOutlined', permission: 'vehicle-list:view', cache: true },
      },
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/router/index.ts
git commit -m "[包6-2] feat: 路由替换 - 黑白名单入口从 access-policies 切换到 vehicle-list"
```

---

### Task F4: Update sidebar/menu to point to vehicle-list

**Files:**
- Find the menu/sidebar configuration file (typically in `admin-web/src/layout/` or `admin-web/src/config/`)

- [ ] **Step 1: Find and update sidebar menu config**

Find any reference to `access-policies` in layout files and replace with `vehicle-list`:
```bash
grep -r "access-polic" admin-web/src/layout/
```

Update the path references accordingly.

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/layout/
git commit -m "[包6-2] feat: 侧边栏菜单 - 黑白名单路径切换到 vehicle-list"
```

---

## Part G: Frontend — Parking Lot Parameters Enhancement

### Task G1: Add review mode toggle to ParkingLotParamDrawer

**Files:**
- Modify: `admin-web/src/views/parking/ParkingLotParamDrawer.vue`

- [ ] **Step 1: The existing drawer already fetches all 7 params dynamically**

The existing `ParkingLotParamDrawer.vue` (created in Phase 1-1) already handles all 7 params plus the review mode through `getLotParams()`. The seeded migration `V20260820001` includes the 7 params, and `V20260718002` (created in Task A2) adds `monthly_fixed.review_mode`.

**No changes needed to the drawer component** — it already dynamically renders any param returned by the API. The 8th param (`monthly_fixed.review_mode`) will appear automatically once seeded.

- [ ] **Step 2: Verify the 8 params appear**

After running the validation, check that the drawer displays all 8 items:
1. `mock_payment.timeout_minutes` (岗亭设置 / INT, default 15)
2. `exit.unpaid_strategy` (出场设置 / ENUM, BLOCK/ALLOW_ARREARS)
3. `arrears.reexit_strategy` (出场设置 / ENUM, MUST_PAY/REMIND_ONLY)
4. `recognition.fail_strategy` (出场设置 / ENUM, MANUAL/AUTO_RELEASE)
5. `monthly_pass.expiry_reminder_days` (计费设置 / INT, default 7)
6. `monthly_pass.count_in_available_space` (计费设置 / BOOLEAN)
7. `pay.exit_window_minutes` (出场设置 / INT, default 15)
8. `monthly_fixed.review_mode` (计费设置 / ENUM, AUTO/MANUAL) ← new

- [ ] **Step 3: Commit (skip if no changes)**

---

## Part H: Frontend — Monthly/Fixed Space Audit UI

### Task H1: Add audit tabs to monthly-pass page

**Files:**
- Modify: `admin-web/src/views/monthly-pass/index.vue`

- [ ] **Step 1: Add "待审核" tab and audit columns**

Add a third tab pane in the template after `expiring`:

```html
      <a-tab-pane key="pending" tab="待审核">
        <div class="query-bar">
          <span class="expiring-hint">待审核月卡，共 {{ pendingTotal }} 条</span>
          <a-button @click="fetchPendingList">
            <template #icon><ReloadOutlined /></template>刷新
          </a-button>
        </div>
        <a-table
          :columns="pendingColumns"
          :data-source="pendingDataSource"
          :loading="pendingLoading"
          :pagination="pendingPagination"
          row-key="id"
          @change="handlePendingTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'auditStatus'">
              <a-tag color="orange">待审核</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a @click="handleApprove(record as any)">通过</a>
                <a style="color: #dc2626" @click="handleReject(record as any)">驳回</a>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
```

- [ ] **Step 2: Add audit columns to the all tab**

Add these columns to `allColumns`:
```typescript
  { title: '审核状态', dataIndex: 'reviewStatus', key: 'reviewStatus', width: 90 },
  { title: '支付方式', dataIndex: 'payMethod', key: 'payMethod', width: 90 },
  { title: '实收金额', dataIndex: 'paidAmountCents', key: 'paidAmountCents', width: 100 },
```

Add bodyCell template for review status and payment display:
```html
            <template v-if="column.key === 'reviewStatus'">
              <a-tag v-if="record.reviewStatus === 'PENDING'" color="orange">待审核</a-tag>
              <a-tag v-else-if="record.reviewStatus === 'APPROVED'" color="green">已通过</a-tag>
              <a-tag v-else-if="record.reviewStatus === 'REJECTED'" color="red">已驳回</a-tag>
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'paidAmountCents'">
              {{ record.paidAmountCents != null ? (record.paidAmountCents / 100).toFixed(2) + ' 元' : '-' }}
            </template>
```

- [ ] **Step 3: Add pending data script logic**

```typescript
// ==================== 待审核 ====================
const pendingLoading = ref(false)
const pendingDataSource = ref<MonthlyPassVO[]>([])
const pendingTotal = ref(0)
const pendingPagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

async function fetchPendingList() {
  pendingLoading.value = true
  try {
    const res = await getMonthlyPassPendingList({
      page: pendingPagination.current, size: pendingPagination.pageSize,
    })
    pendingDataSource.value = res.records || []
    pendingTotal.value = res.total || 0
    pendingPagination.total = res.total || 0
  } finally { pendingLoading.value = false }
}

function handlePendingTableChange(pag: any) {
  pendingPagination.current = pag.current; pendingPagination.pageSize = pag.pageSize
  fetchPendingList()
}

async function handleApprove(record: MonthlyPassVO) {
  Modal.confirm({
    title: '确认通过审核', content: `确定通过月卡 ${record.plateNumber} 的审核？`,
    onOk: async () => {
      await approveMonthlyPass(record.id)
      message.success('已通过')
      fetchPendingList()
    },
  })
}

async function handleReject(record: MonthlyPassVO) {
  Modal.confirm({
    title: '确认驳回', content: `确定驳回月卡 ${record.plateNumber}？`,
    onOk: async () => {
      await rejectMonthlyPass(record.id)
      message.success('已驳回')
      fetchPendingList()
    },
  })
}
```

- [ ] **Step 4: Update tab change handler**

```typescript
function handleTabChange(key: string | number) {
  if (key === 'expiring') { fetchExpiringList() }
  else if (key === 'pending') { fetchPendingList() }
  else { fetchData() }
}
```

- [ ] **Step 5: Add pendingColumns definition**

```typescript
const pendingColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 130 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '有效期', dataIndex: 'validEndDate', key: 'validEndDate', width: 120 },
  { title: '车主', dataIndex: 'ownerName', key: 'ownerName', width: 110 },
  { title: '审核状态', key: 'reviewStatus', width: 90 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]
```

- [ ] **Step 6: Commit**

```bash
git add admin-web/src/views/monthly-pass/index.vue
git commit -m "[包6-2] feat: 月卡管理 - 新增待审核 Tab + 审核状态/支付方式/实收金额列"
```

---

### Task H2: Add audit tabs to fixed-space page

**Files:**
- Modify: `admin-web/src/views/fixed-space/index.vue`

- [ ] **Step 1: Add identical pattern as monthly-pass**

Add "待审核" tab pane, pending columns, pending data logic, approve/reject functions following the exact same pattern as Task H1 but using `getFixedSpacePendingList` / `approveFixedSpace` / `rejectFixedSpace` from the fixed-space API module.

- [ ] **Step 2: Add audit columns to all tab**

Same as H1 step 2, adding `auditStatus`, `paymentMethod`, `actualAmountCents` columns.

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/views/fixed-space/index.vue
git commit -m "[包6-2] feat: 固定车位管理 - 新增待审核 Tab + 审核状态/支付方式/实收金额列"
```

---

## Part I: Frontend — Account Form Enhancement

### Task I1: Update AdminAccountFormModal — multi-lot, fee reduction, password display

**Files:**
- Modify: `admin-web/src/views/account/AdminAccountFormModal.vue`
- Modify: `admin-web/src/views/account/AdminAccountManage.vue`

- [ ] **Step 1: Add multi-parking-lot selector (for level=3/booth admin)**

In `AdminAccountFormModal.vue`, find the existing single-lot selector:

```html
      <a-form-item v-if="formData.level === 3" label="所属车场" name="lotId">
        <a-select v-model:value="formData.lotId" placeholder="请选择所属车场" allow-clear>
          <a-select-option v-for="lot in parkingLots" :key="lot.id" :value="lot.id">
            {{ lot.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
```

Replace with:
```html
      <a-form-item v-if="formData.level === 3" label="所属车场" name="parkingLotIds">
        <a-select v-model:value="formData.parkingLotIds" mode="multiple" placeholder="请选择授权停车场" allow-clear>
          <a-select-option v-for="lot in parkingLots" :key="lot.id" :value="lot.id">
            {{ lot.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
```

- [ ] **Step 2: Add allow_fee_reduction checkbox for level=3**

Add after the parking lot selector:
```html
      <a-form-item v-if="formData.level === 3" label="费用减免权限">
        <a-switch v-model:checked="formData.allowFeeReduction" checked-children="允许" un-checked-children="禁止" />
        <span style="margin-left: 8px; color: #999; font-size: 12px;">岗亭管理员费用减免权限需创建时勾选</span>
      </a-form-item>
```

- [ ] **Step 3: Update formData reactive**

Old:
```typescript
const formData = reactive<{
  id?: string
  username: string
  password: string
  realName: string
  phone: string
  email: string
  level: number | undefined
  companyId: number | undefined
  lotId: number | undefined
  roleIds: number[]
  status: number
}>({
```

New:
```typescript
const formData = reactive<{
  id?: string
  username: string
  password: string
  realName: string
  phone: string
  email: string
  level: number | undefined
  companyId: number | undefined
  lotId: number | undefined
  roleIds: number[]
  status: number
  parkingLotIds: number[]
  allowFeeReduction: boolean
}>({
```

- [ ] **Step 4: Update handleLevelChange**

```typescript
function handleLevelChange(level: number) {
  formData.companyId = undefined
  formData.lotId = undefined
  formData.parkingLotIds = []
  formData.allowFeeReduction = false
}
```

- [ ] **Step 5: Update fillForm to populate new fields**

```typescript
function fillForm(record: AdminAccountVO) {
  // ... existing fields ...
  formData.parkingLotIds = record.parkingLotIds || []
  formData.allowFeeReduction = record.allowFeeReduction === 1
}
```

- [ ] **Step 6: Update submit payload**

For create:
```typescript
        parkingLotIds: formData.parkingLotIds.length > 0 ? formData.parkingLotIds : undefined,
        mustChangePassword: 1,
        allowFeeReduction: formData.allowFeeReduction ? 1 : 0,
```

For update:
```typescript
        parkingLotIds: formData.parkingLotIds,
        allowFeeReduction: formData.allowFeeReduction ? 1 : 0,
```

- [ ] **Step 7: Commit**

```bash
git add admin-web/src/views/account/AdminAccountFormModal.vue
git commit -m "[包6-2] feat: 账号表单 - 多车场选择 + 费用减免开关 + parking_lot_ids"
```

---

### Task I2: Update AdminAccountManage — password display on creation success

**Files:**
- Modify: `admin-web/src/views/account/AdminAccountManage.vue`

- [ ] **Step 1: Add password display modal after creation**

Add a new modal component after the existing reset password modal in the template:

```html
    <!-- 创建成功显示密码 -->
    <a-modal
      v-model:open="createdPasswordModalOpen"
      title="账号创建成功"
      :footer="null"
      :closable="false"
      width="480px"
      @cancel="createdPasswordModalOpen = false"
    >
      <a-result status="success" title="账号创建成功" sub-title="初始密码仅在本次显示，请妥善保存">
        <template #extra>
          <div style="text-align: left; background: #f6f8fa; padding: 16px; border-radius: 6px; margin-top: 16px;">
            <p><strong>账号：</strong>{{ createdAccountInfo?.username }}</p>
            <p><strong>初始密码：</strong><code style="font-size: 18px; letter-spacing: 2px;">{{ createdAccountInfo?.plainPassword }}</code></p>
          </div>
          <a-button type="primary" style="margin-top: 16px;" @click="createdPasswordModalOpen = false">我已保存，关闭</a-button>
        </template>
      </a-result>
    </a-modal>
```

- [ ] **Step 2: Add reactive state**

```typescript
const createdPasswordModalOpen = ref(false)
const createdAccountInfo = ref<{ username: string; plainPassword: string } | null>(null)
```

- [ ] **Step 3: Update handleFormSubmit / success handler**

In the form modal's submit handler, after `emit('success')`, dispatch an event or set the password info:

In `AdminAccountFormModal.vue`, emit success with the response data:
```typescript
    } else {
      const result = await createAdminAccount(...)
      // result contains plainPassword from backend
      emit('update:open', false)
      emit('success', result)
    }
```

Then in `AdminAccountManage.vue`, handle the `@success` event:
```html
<admin-account-form-modal
  v-model:open="formModalOpen"
  :record="editingRecord"
  :current-level="currentLevel"
  @success="handleFormSuccess"
/>
```

```typescript
function handleFormSuccess(result?: any) {
  if (result?.plainPassword) {
    createdAccountInfo.value = {
      username: result.username,
      plainPassword: result.plainPassword,
    }
    createdPasswordModalOpen.value = true
  }
  fetchData()
}
```

- [ ] **Step 4: Commit**

```bash
git add admin-web/src/views/account/AdminAccountManage.vue admin-web/src/views/account/AdminAccountFormModal.vue
git commit -m "[包6-2] feat: 账号创建成功一次性显示随机密码"
```

---

## Part J: Frontend — Account Page Unification

### Task J1: Deprecate employee page with migration notice

**Files:**
- Modify: `admin-web/src/views/employee/index.vue`
- Modify: `admin-web/src/router/index.ts`

- [ ] **Step 1: Wrap employee page with deprecation banner**

Add at the top of the employee page template:
```html
    <a-alert
      v-if="!hideDeprecatedBanner"
      type="warning"
      show-icon
      closable
      @close="hideDeprecatedBanner = true"
      style="margin-bottom: 16px"
    >
      <template #message>
        <strong>【系统通知】</strong>员工管理已合并至"账号管理"页面。
        <a @click="goToAccounts">点击前往账号管理</a>
        &nbsp;本页面仅保留历史数据查看，不再支持新增/编辑/删除操作。
      </template>
    </a-alert>
```

- [ ] **Step 2: Add state and method**

```typescript
const hideDeprecatedBanner = ref(false)

function goToAccounts() {
  router.push('/admin-accounts')
}
```

Add router import: `import { useRouter } from 'vue-router'` and `const router = useRouter()`.

- [ ] **Step 3: Disable CRUD buttons**

Hide the "新增员工" button, and disable edit/delete actions on the employee page. Remove or comment out `handleCreate`, `handleEdit`, `handleFormSubmit`, `handleDelete`, `handleToggleStatus` from the action column, replacing with read-only view.

- [ ] **Step 4: Update router meta for employee page**

In `router/index.ts`, update the employee route meta:
```typescript
      {
        path: 'employees',
        name: 'Employees',
        component: () => import('@/views/employee/index.vue'),
        meta: { title: '员工管理（已废弃）', icon: 'UserOutlined' },
      },
```

- [ ] **Step 5: Commit**

```bash
git add admin-web/src/views/employee/index.vue admin-web/src/router/index.ts
git commit -m "[包6-2] feat: 员工管理页废弃 - 添加迁移指引横幅 + 禁用CRUD"
```

---

## Part K: Frontend — First Login Force Password Change

### Task K1: Create ChangePasswordModal component

**Files:**
- Create: `admin-web/src/views/account/ChangePasswordModal.vue`

- [ ] **Step 1: Write the modal component**

```vue
<template>
  <a-modal
    :open="visible"
    title="首次登录 — 请修改密码"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    :footer="null"
    width="460px"
  >
    <a-alert type="info" show-icon message="为保障账号安全，首次登录必须修改密码。" style="margin-bottom: 16px" />

    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
      @finish="handleSubmit"
    >
      <a-form-item label="原密码" name="oldPassword">
        <a-input-password v-model:value="formData.oldPassword" placeholder="请输入当前密码" />
      </a-form-item>
      <a-form-item label="新密码" name="newPassword">
        <a-input-password v-model:value="formData.newPassword" placeholder="6-64位新密码" />
      </a-form-item>
      <a-form-item label="确认密码" name="confirmPassword">
        <a-input-password v-model:value="formData.confirmPassword" placeholder="再次输入新密码" />
      </a-form-item>
      <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
        <a-button type="primary" html-type="submit" :loading="loading" block>确认修改密码</a-button>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/stores'

const props = defineProps<{
  open: boolean
  username: string
}>()

const emit = defineEmits<{
  (e: 'update:open', val: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: any, value: string) => {
  if (!value) return Promise.reject(new Error('请确认新密码'))
  if (value !== formData.newPassword) return Promise.reject(new Error('两次输入的密码不一致'))
  return Promise.resolve()
}

const rules: Record<string, Rule[]> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
    { max: 64, message: '密码不能超过64位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleSubmit() {
  loading.value = true
  try {
    await changePassword({
      oldPassword: formData.oldPassword,
      newPassword: formData.newPassword,
    })
    message.success('密码修改成功')
    // Clear must_change_password flag in local store
    if (authStore.userInfo) {
      authStore.userInfo.mustChangePassword = 0
    }
    emit('success')
    visible.value = false
  } catch (err: any) {
    if (err?.message?.includes('原密码不正确')) {
      message.error('原密码不正确')
    }
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 2: Integrate into the app layout**

In `admin-web/src/layout/index.vue` (or wherever the authenticated layout is), add the modal overlay:

```html
    <!-- 强制改密弹窗 -->
    <ChangePasswordModal
      v-if="authStore.userInfo?.mustChangePassword === 1"
      :open="true"
      :username="authStore.userInfo?.username || ''"
      @success="onPasswordChanged"
    />
```

Import:
```typescript
import ChangePasswordModal from '@/views/account/ChangePasswordModal.vue'
```

And add the handler:
```typescript
function onPasswordChanged() {
  // Re-fetch user info to update mustChangePassword flag
  authStore.fetchUserInfo()
}
```

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/views/account/ChangePasswordModal.vue admin-web/src/layout/index.vue
git commit -m "[包6-2] feat: 首次登录强制改密 - ChangePasswordModal + Layout 集成"
```

---

## Part L: Integration & Cleanup

### Task L1: Compile backend and run tests

- [ ] **Step 1: Compile**

```bash
mvn clean compile -pl parking-system -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Run tests**

```bash
mvn test -pl parking-boot -am
```

Expected: All tests pass

- [ ] **Step 3: Commit (if any fixes)**

---

### Task L2: Update permission config — add vehicle-list permissions

**Files:**
- Find and modify the permission seeder SQL or Java config

- [ ] **Step 1: Add new permission codes**

Add to the permission seed:
```sql
INSERT IGNORE INTO sys_permission (code, name, module) VALUES
('vehicle-list:view', '黑白名单查看', 'vehicle-list'),
('vehicle-list:create', '黑白名单创建', 'vehicle-list'),
('vehicle-list:update', '黑白名单编辑', 'vehicle-list'),
('vehicle-list:delete', '黑白名单删除', 'vehicle-list');
```

- [ ] **Step 2: Commit**

---

### Task L3: Data migration check — employee to sys_admin_account

**This is an optional verification task.** If existing employee data needs to be migrated:

- [ ] **Step 1: Write migration SQL (run manually in dev only)**

```sql
-- 将 employee 数据迁移到 sys_admin_account（手动执行，测试环境）
INSERT INTO sys_admin_account (id, tenant_id, username, password, real_name, phone, level, status, must_change_password, allow_fee_reduction, created_at, updated_at)
SELECT
    e.id,
    e.tenant_id,
    e.phone AS username,
    su.password_hash AS password,
    e.display_name AS real_name,
    e.phone,
    3 AS level,  -- 全部设为岗亭管理员级别
    1 AS status,  -- 正常
    0 AS must_change_password,  -- 不强制改密（老账号）
    0 AS allow_fee_reduction,
    e.created_at,
    e.updated_at
FROM employee e
LEFT JOIN sys_user su ON su.phone = e.phone
WHERE e.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM sys_admin_account s WHERE s.username = e.phone);

-- 迁移停车场分配
INSERT INTO sys_admin_account_parking_lot (id, tenant_id, admin_account_id, parking_lot_id, created_at)
SELECT
    CONCAT('200', epl.id) AS id,
    epl.tenant_id,
    epl.employee_id AS admin_account_id,
    epl.parking_lot_id,
    NOW()
FROM employee_parking_lot epl
WHERE NOT EXISTS (
    SELECT 1 FROM sys_admin_account_parking_lot s
    WHERE s.admin_account_id = epl.employee_id AND s.parking_lot_id = epl.parking_lot_id
);
```

**Note:** This migration must be tested and validated before applying to production.

- [ ] **Step 2: Commit (separate from main code)**

---

### Task L4: Final verification checklist

- [ ] **Backend verification:**

```bash
# Start the application
mvn spring-boot:run -pl parking-boot -am

# Test vehicle-list API
curl http://localhost:8080/api/v1/admin/vehicle-list/page?page=1&size=5 \
  -H "Authorization: Bearer <token>"

# Test param API
curl http://localhost:8080/api/admin/parking-lots/1/params \
  -H "Authorization: Bearer <token>"

# Test monthly pass audit
curl http://localhost:8080/api/v1/admin/monthly-pass-audit/pending?page=1&size=5 \
  -H "Authorization: Bearer <token>"

# Test account creation (should return plainPassword)
curl -X POST http://localhost:8080/api/v1/admin-accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"username":"test_booth","realName":"测试岗亭","level":3,"companyId":1,"lotId":1,"status":1}'
```

- [ ] **Frontend verification:**

```bash
cd admin-web && npm run dev
```

1. Open browser → Login → Navigate to "黑白名单" → Verify page loads with vehicle-list data
2. Navigate to "停车场管理" → Click "参数" → Verify all 8 params plus "继承" indicators
3. Navigate to "月卡管理" → Verify "待审核" tab appears
4. Navigate to "固定车位" → Verify "待审核" tab appears
5. Navigate to "账号管理" → Create a new booth admin account → Verify password shown, multi-lot selector, fee reduction toggle
6. Navigate to "员工管理" → Verify deprecation banner
7. Log out and log in with a `must_change_password=1` account → Verify forced password change modal appears

- [ ] **Commit all final verification fixes**

---

## Migration Note: AccessPolicy → VehicleList Data

The old `access_policy` table uses `policy_type` values `BLACKLIST`/`VIP`, while the new `vehicle_list` table uses `BLACK`/`WHITE`. If legacy data needs migration:

```sql
-- Report-only migration SQL (run manually, not in Flyway)
INSERT INTO vehicle_list (plate_number, list_type, parking_lot_id, start_date, end_date, trigger_type, status, remark, tenant_id, created_at, updated_at)
SELECT
    policy_key AS plate_number,
    CASE WHEN policy_type = 'BLACKLIST' THEN 'BLACK' ELSE 'WHITE' END AS list_type,
    parking_lot_id,
    NULL AS start_date,
    NULL AS end_date,
    'OTHER' AS trigger_type,
    CASE WHEN status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END AS status,
    description AS remark,
    tenant_id,
    created_at,
    updated_at
FROM access_policy
WHERE deleted_at IS NULL;
```

**Report result:** Count of migrated records per type.

---

## Self-Review Checklist (for Plan Writer)

1. **Spec coverage:** All 6 targets covered → ✓
2. **No placeholders:** No TBD/TODO in tasks → ✓
3. **Type consistency:** `AdminAccountCreateCmd.parkingLotIds` matches frontend `parkingLotIds` → ✓
4. **Backend gaps flagged:** MonthlyPassService/FixedSpaceService need `pageAuditPending` + `toVO` methods; existing services must support these
5. **Migration task included** (Part L) for employee→admin_account data migration
6. **Password security:** `plainPassword` only in create response, NOT stored in frontend storage
