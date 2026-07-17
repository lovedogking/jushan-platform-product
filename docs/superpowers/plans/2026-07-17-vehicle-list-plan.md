# 黑白名单独立实体 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 新建 vehicle_list 表统一管理黑白名单，替代 AccessPolicy + sys_vehicle 散装数据，实现互斥校验、触发模式、有效期、缓存判定。

**Architecture:** 新建 Entity → Mapper → Service（含互斥校验 + 缓存）→ Controller 全链路；改造 VehicleTypeDecisionService 优先级链（白/黑名单最高优先级插入）；EntryService/ExitService 增加拦截；复用 MonitorAlertService + BoothWebSocketPublisher 告警。

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus 3.5.x + MySQL 8 + Flyway + JUnit 5 + Mockito + Redis (StringRedisTemplate)

---

### Task 1: Flyway 迁移脚本 — 建表 + 存量迁移 + 全局参数

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260828001__create_vehicle_list.sql`
- Modify: `parking-boot/src/main/resources/application.yml`

**Description:** 创建 vehicle_list 表、索引、迁移存量数据、插入全局参数默认值。

- [x] **Step 1: 编写 Flyway 迁移脚本**

创建 `parking-boot/src/main/resources/db/migration/V20260828001__create_vehicle_list.sql`：

```sql
-- ============================================================
-- 1. 创建 vehicle_list 表
-- ============================================================
CREATE TABLE IF NOT EXISTS vehicle_list (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    list_type       VARCHAR(16)     NOT NULL COMMENT '名单类型：BLACK / WHITE',
    parking_lot_id  BIGINT          NOT NULL COMMENT '生效车场ID',
    start_date      DATE            NULL COMMENT '有效期开始（NULL=立即生效）',
    end_date        DATE            NULL COMMENT '有效期结束（NULL=永久）',
    trigger_type    VARCHAR(32)     NULL COMMENT '黑名单触发类型：ARREARS / MANAGEMENT / OTHER（白名单为NULL）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / DISABLED',
    remark          VARCHAR(255)    NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME        NULL COMMENT '软删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆黑白名单表';

-- 索引
CREATE UNIQUE INDEX uk_lot_plate_type ON vehicle_list (parking_lot_id, plate_number, list_type);
CREATE INDEX idx_plate_number ON vehicle_list (plate_number);
CREATE INDEX idx_parking_lot_id ON vehicle_list (parking_lot_id);
CREATE INDEX idx_list_type_status ON vehicle_list (list_type, status);

-- ============================================================
-- 2. 迁移 AccessPolicy BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    ap.tenant_id,
    UPPER(ap.policy_key) AS plate_number,
    'BLACK'               AS list_type,
    ap.parking_lot_id,
    NULL                  AS start_date,
    NULL                  AS end_date,
    'OTHER'               AS trigger_type,
    'ACTIVE'              AS status,
    CONCAT('迁移自access_policy: ', COALESCE(ap.description, '')) AS remark,
    ap.created_at,
    NOW()
FROM access_policy ap
WHERE ap.policy_type = 'BLACKLIST'
  AND ap.status = 'ACTIVE'
  AND ap.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = ap.parking_lot_id
        AND vl.plate_number  = UPPER(ap.policy_key)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 3. 迁移 sys_vehicle BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'BLACK'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    'OTHER'                AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle BLACKLIST: ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type = 'BLACKLIST'
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 4. 迁移 sys_vehicle VIP/SUPER/FREE → vehicle_list（白名单）
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'WHITE'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    NULL                   AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle ', sv.vehicle_type, ': ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type IN ('VIP', 'SUPER', 'FREE')
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'WHITE'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 5. 插入全局参数默认值
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_mode', 'DENY_ENTRY',
        '黑名单触发模式：DENY_ENTRY-禁止入场 / ALLOW_WITH_ALERT-允许但告警 / BY_TYPE-按类型区分',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_types',
        '[{"code":"ARREARS","label":"欠费类"},{"code":"MANAGEMENT","label":"管理类"},{"code":"OTHER","label":"其他类"}]',
        '黑名单触发类型字典（JSON数组）',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
```

- [x] **Step 2: Verify Flyway migration**

```bash
mvn clean compile -pl parking-system -am
```

Expected: BUILD SUCCESS, no SQL errors.

- [x] **Step 3: Commit**

```bash
git add parking-boot/src/main/resources/db/migration/V20260828001__create_vehicle_list.sql
git commit -m "[ADMIN-008] feat: create vehicle_list table with data migration"
```

---

### Task 2: Entity + Mapper + DTO/VO 层

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/entity/VehicleList.java`
- Create: `parking-system/src/main/java/com/jushan/system/mapper/VehicleListMapper.java`
- Create: `parking-system/src/main/java/com/jushan/system/dto/VehicleListCreateCmd.java`
- Create: `parking-system/src/main/java/com/jushan/system/dto/VehicleListUpdateCmd.java`
- Create: `parking-system/src/main/java/com/jushan/system/dto/VehicleListPageQuery.java`
- Create: `parking-system/src/main/java/com/jushan/system/vo/VehicleListVO.java`
- Create: `parking-system/src/main/java/com/jushan/system/vo/VehicleListDecisionVO.java`

**Description:** 创建 Entity 实体类、MyBatis-Plus Mapper、请求 DTO、响应 VO 和判定结果 VO。

- [x] **Step 1: 创建 VehicleList Entity**

```java
package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 车辆黑白名单实体。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vehicle_list")
public class VehicleList extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 车牌号（标准化大写） */
    private String plateNumber;

    /** 名单类型：BLACK / WHITE */
    private String listType;

    /** 生效车场ID */
    private Long parkingLotId;

    /** 有效期开始（NULL=立即生效） */
    private LocalDate startDate;

    /** 有效期结束（NULL=永久） */
    private LocalDate endDate;

    /** 黑名单触发类型：ARREARS / MANAGEMENT / OTHER（白名单为NULL） */
    private String triggerType;

    /** 状态：ACTIVE / EXPIRED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    // ==================== 常量 ====================

    public static final String TYPE_BLACK = "BLACK";
    public static final String TYPE_WHITE = "WHITE";

    public static final String TRIGGER_ARREARS = "ARREARS";
    public static final String TRIGGER_MANAGEMENT = "MANAGEMENT";
    public static final String TRIGGER_OTHER = "OTHER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_DISABLED = "DISABLED";
}
```

- [x] **Step 2: 创建 VehicleListMapper**

```java
package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.VehicleList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车辆黑白名单 Mapper。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Mapper
public interface VehicleListMapper extends BaseMapper<VehicleList> {

    /**
     * 查询指定车场指定车牌的有效白名单。
     */
    @Select("SELECT * FROM vehicle_list WHERE parking_lot_id = #{parkingLotId} "
            + "AND plate_number = #{plateNumber} AND list_type = 'WHITE' "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    VehicleList selectActiveWhite(@Param("parkingLotId") Long parkingLotId,
                                   @Param("plateNumber") String plateNumber);

    /**
     * 查询指定车场指定车牌的有效黑名单。
     */
    @Select("SELECT * FROM vehicle_list WHERE parking_lot_id = #{parkingLotId} "
            + "AND plate_number = #{plateNumber} AND list_type = 'BLACK' "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    VehicleList selectActiveBlack(@Param("parkingLotId") Long parkingLotId,
                                   @Param("plateNumber") String plateNumber);

    /**
     * 查询互斥名单（反类型）。
     */
    @Select("SELECT * FROM vehicle_list WHERE parking_lot_id = #{parkingLotId} "
            + "AND plate_number = #{plateNumber} AND list_type = #{listType} "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    VehicleList selectByLotAndPlateAndType(@Param("parkingLotId") Long parkingLotId,
                                            @Param("plateNumber") String plateNumber,
                                            @Param("listType") String listType);

    /**
     * 查询到期的有效名单（定时任务用）。
     */
    @Select("SELECT * FROM vehicle_list WHERE end_date < CURRENT_DATE "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT #{limit}")
    List<VehicleList> selectExpired(@Param("limit") int limit);
}
```

- [x] **Step 3: 创建 DTO 类**

`VehicleListCreateCmd.java`:
```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class VehicleListCreateCmd {

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    @NotBlank(message = "名单类型不能为空")
    private String listType;

    @NotNull(message = "生效车场不能为空")
    private Long parkingLotId;

    private LocalDate startDate;
    private LocalDate endDate;
    private String triggerType;
    private String remark;
}
```

`VehicleListUpdateCmd.java`:
```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class VehicleListUpdateCmd {

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    @NotBlank(message = "名单类型不能为空")
    private String listType;

    private Long parkingLotId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String triggerType;
    private String remark;
}
```

`VehicleListPageQuery.java`:
```java
package com.jushan.system.dto;

import lombok.Data;

@Data
public class VehicleListPageQuery {

    private Integer page = 1;
    private Integer size = 20;
    private Long parkingLotId;
    private String listType;
    private String plateNumber;
}
```

- [x] **Step 4: 创建 VO 类**

`VehicleListVO.java`:
```java
package com.jushan.system.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class VehicleListVO {

    private Long id;
    private String plateNumber;
    private String listType;
    private String listTypeLabel;
    private Long parkingLotId;
    private String parkingLotName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String triggerType;
    private String triggerTypeLabel;
    private String status;
    private String statusLabel;
    private String remark;
    private LocalDateTime createdAt;
}
```

`VehicleListDecisionVO.java`:
```java
package com.jushan.system.vo;

import lombok.Data;

/**
 * 名单判定结果 VO，供 EntryService/ExitService 使用。
 */
@Data
public class VehicleListDecisionVO {

    /** 是否禁止入场/出场 */
    private boolean denyEntry;

    /** 是否允许但需告警 */
    private boolean alert;

    /** 名单类型：WHITE / BLACK */
    private String listType;

    /** 黑名单触发类型（白名单为null） */
    private String triggerType;

    /** 拒绝/告警原因 */
    private String reason;
}
```

- [x] **Step 5: Compile and commit**

```bash
mvn clean compile -pl parking-system -am
```

Expected: BUILD SUCCESS.

```bash
git add parking-system/src/main/java/com/jushan/system/entity/VehicleList.java \
        parking-system/src/main/java/com/jushan/system/mapper/VehicleListMapper.java \
        parking-system/src/main/java/com/jushan/system/dto/VehicleListCreateCmd.java \
        parking-system/src/main/java/com/jushan/system/dto/VehicleListUpdateCmd.java \
        parking-system/src/main/java/com/jushan/system/dto/VehicleListPageQuery.java \
        parking-system/src/main/java/com/jushan/system/vo/VehicleListVO.java \
        parking-system/src/main/java/com/jushan/system/vo/VehicleListDecisionVO.java
git commit -m "[ADMIN-008] feat: add VehicleList entity, mapper, dto and vo"
```

---

### Task 3: VehicleListCacheStore — 缓存存储

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/cache/VehicleListCacheStore.java`
- Create: `parking-system/src/test/java/com/jushan/system/cache/VehicleListCacheStoreTest.java`

**Description:** 基于 Redis + ConcurrentHashMap 降级的名单缓存存储，遵循项目 RedisParamCacheStore 模式。

- [x] **Step 1: 创建 VehicleListCacheStore**

```java
package com.jushan.system.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车辆名单缓存存储。
 * 遵循项目 RedisParamCacheStore 的双层缓存模式：
 * Redis 可用 → Redis String；Redis 不可用 → 进程内 ConcurrentHashMap 降级。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
public class VehicleListCacheStore {

    private static final Logger log = LoggerFactory.getLogger(VehicleListCacheStore.class);

    static final String KEY_PREFIX = "vehicle_list:";
    static final long TTL_MINUTES = 30;
    static final String NULL_MARKER = "__NULL__";

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, String> localCache = new ConcurrentHashMap<>();

    public VehicleListCacheStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * 构建缓存键：vehicle_list:{parkingLotId}:{plateNumber}
     */
    public static String buildKey(Long parkingLotId, String plateNumber) {
        return parkingLotId + ":" + plateNumber;
    }

    public String get(Long parkingLotId, String plateNumber) {
        String key = buildKey(parkingLotId, plateNumber);
        if (stringRedisTemplate == null) {
            return localCache.get(key);
        }
        try {
            return stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("名单缓存读取异常，回退本地缓存: lotId={} plate={}", parkingLotId, plateNumber, e);
            return localCache.get(key);
        }
    }

    public void put(Long parkingLotId, String plateNumber, String value) {
        String key = buildKey(parkingLotId, plateNumber);
        String val = value != null ? value : NULL_MARKER;
        if (stringRedisTemplate == null) {
            localCache.put(key, val);
            return;
        }
        try {
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + key, val, Duration.ofMinutes(TTL_MINUTES));
        } catch (Exception e) {
            log.warn("名单缓存写入异常（忽略）: lotId={} plate={}", parkingLotId, plateNumber, e);
            localCache.put(key, val);
        }
    }

    public void evict(Long parkingLotId, String plateNumber) {
        String key = buildKey(parkingLotId, plateNumber);
        if (stringRedisTemplate == null) {
            localCache.remove(key);
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("名单缓存失效异常（忽略）: lotId={} plate={}", parkingLotId, plateNumber, e);
        }
        localCache.remove(key);
    }
}
```

- [x] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/cache/VehicleListCacheStore.java
git commit -m "[ADMIN-008] feat: add VehicleListCacheStore with Redis + local fallback"
```

---

### Task 4: VehicleListService — 核心业务服务（含互斥校验 + 缓存）

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/service/VehicleListService.java`
- Create: `parking-system/src/test/java/com/jushan/system/service/VehicleListServiceTest.java`

**Description:** CRUD、互斥校验、缓存判定（isBlacklisted / isWhitelisted / checkEntry）。

- [x] **Step 1: 编写第一个单元测试（互斥校验）**

创建 `parking-system/src/test/java/com/jushan/system/service/VehicleListServiceTest.java`:
```java
package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.system.cache.VehicleListCacheStore;
import com.jushan.system.dto.VehicleListCreateCmd;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.mapper.VehicleListMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleListServiceTest {

    @Mock
    private VehicleListMapper vehicleListMapper;

    @Mock
    private VehicleListCacheStore cacheStore;

    private VehicleListService service;

    @BeforeEach
    void setUp() {
        service = new VehicleListService(vehicleListMapper, cacheStore);
    }

    @Test
    @DisplayName("互斥校验：同车场同车牌已存在白名单，再添加黑名单应被拦截")
    void shouldRejectBlacklistWhenWhitelistExists() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京A12345");
        cmd.setListType(VehicleList.TYPE_BLACK);
        cmd.setParkingLotId(1L);
        cmd.setTriggerType(VehicleList.TRIGGER_OTHER);

        VehicleList existingWhite = new VehicleList();
        existingWhite.setPlateNumber("京A12345");
        existingWhite.setListType(VehicleList.TYPE_WHITE);
        existingWhite.setParkingLotId(1L);

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(1L), eq("京A12345"), eq(VehicleList.TYPE_WHITE)))
                .thenReturn(existingWhite);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在于白名单中");
    }

    @Test
    @DisplayName("互斥校验：同车场同车牌已存在黑名单，再添加白名单应被拦截")
    void shouldRejectWhitelistWhenBlacklistExists() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京B67890");
        cmd.setListType(VehicleList.TYPE_WHITE);
        cmd.setParkingLotId(2L);

        VehicleList existingBlack = new VehicleList();
        existingBlack.setPlateNumber("京B67890");
        existingBlack.setListType(VehicleList.TYPE_BLACK);
        existingBlack.setParkingLotId(2L);

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(2L), eq("京B67890"), eq(VehicleList.TYPE_BLACK)))
                .thenReturn(existingBlack);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在于黑名单中");
    }

    @Test
    @DisplayName("黑名单创建时 triggerType 为空应抛异常")
    void shouldRejectBlacklistWithoutTriggerType() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京C11111");
        cmd.setListType(VehicleList.TYPE_BLACK);
        cmd.setParkingLotId(3L);

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(3L), eq("京C11111"), eq(VehicleList.TYPE_WHITE)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("触发类型不能为空");
    }

    @Test
    @DisplayName("正常创建黑名单后缓存被设置")
    void shouldCreateBlacklistAndEvictCache() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京D22222");
        cmd.setListType(VehicleList.TYPE_BLACK);
        cmd.setParkingLotId(4L);
        cmd.setTriggerType(VehicleList.TRIGGER_ARREARS);
        cmd.setRemark("测试欠费拉黑");

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(4L), eq("京D22222"), eq(VehicleList.TYPE_WHITE)))
                .thenReturn(null);
        when(vehicleListMapper.insert(any(VehicleList.class))).thenReturn(1);

        VehicleList result = service.create(cmd);

        assertThat(result.getPlateNumber()).isEqualTo("京D22222");
        assertThat(result.getListType()).isEqualTo(VehicleList.TYPE_BLACK);
        assertThat(result.getTriggerType()).isEqualTo(VehicleList.TRIGGER_ARREARS);
        assertThat(result.getStatus()).isEqualTo(VehicleList.STATUS_ACTIVE);
        verify(cacheStore).evict(eq(4L), eq("京D22222"));
    }
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
mvn test -pl parking-system -am -Dtest=VehicleListServiceTest
```

Expected: FAIL (VehicleListService 类不存在).

- [x] **Step 3: 实现 VehicleListService**

```java
package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.cache.VehicleListCacheStore;
import com.jushan.system.dto.VehicleListCreateCmd;
import com.jushan.system.dto.VehicleListUpdateCmd;
import com.jushan.system.dto.VehicleListPageQuery;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.mapper.VehicleListMapper;
import com.jushan.system.vo.VehicleListDecisionVO;
import com.jushan.system.vo.VehicleListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 车辆黑白名单业务服务。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Slf4j
@Service
public class VehicleListService extends ServiceImpl<VehicleListMapper, VehicleList> {

    private final VehicleListMapper vehicleListMapper;
    private final VehicleListCacheStore cacheStore;

    private static final Map<String, String> TRIGGER_TYPE_LABEL_MAP = new HashMap<>();
    private static final Map<String, String> LIST_TYPE_LABEL_MAP = new HashMap<>();
    private static final Map<String, String> STATUS_LABEL_MAP = new HashMap<>();

    static {
        TRIGGER_TYPE_LABEL_MAP.put(VehicleList.TRIGGER_ARREARS, "欠费类");
        TRIGGER_TYPE_LABEL_MAP.put(VehicleList.TRIGGER_MANAGEMENT, "管理类");
        TRIGGER_TYPE_LABEL_MAP.put(VehicleList.TRIGGER_OTHER, "其他类");

        LIST_TYPE_LABEL_MAP.put(VehicleList.TYPE_BLACK, "黑名单");
        LIST_TYPE_LABEL_MAP.put(VehicleList.TYPE_WHITE, "白名单");

        STATUS_LABEL_MAP.put(VehicleList.STATUS_ACTIVE, "生效中");
        STATUS_LABEL_MAP.put(VehicleList.STATUS_EXPIRED, "已过期");
        STATUS_LABEL_MAP.put(VehicleList.STATUS_DISABLED, "已禁用");
    }

    public VehicleListService(VehicleListMapper vehicleListMapper, VehicleListCacheStore cacheStore) {
        this.vehicleListMapper = vehicleListMapper;
        this.cacheStore = cacheStore;
    }

    @Transactional
    public VehicleList create(VehicleListCreateCmd cmd) {
        String plate = cmd.getPlateNumber().toUpperCase();
        Long lotId = cmd.getParkingLotId();
        String listType = cmd.getListType();

        // 互斥校验
        String oppositeType = VehicleList.TYPE_BLACK.equals(listType)
                ? VehicleList.TYPE_WHITE : VehicleList.TYPE_BLACK;
        VehicleList conflict = vehicleListMapper.selectByLotAndPlateAndType(lotId, plate, oppositeType);
        if (conflict != null) {
            String oppositeLabel = LIST_TYPE_LABEL_MAP.getOrDefault(oppositeType, oppositeType);
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "该车牌已存在于" + oppositeLabel + "中，无法同时添加为" + getListTypeLabel(listType));
        }

        // 黑名单必填 triggerType
        if (VehicleList.TYPE_BLACK.equals(listType)
                && (cmd.getTriggerType() == null || cmd.getTriggerType().isBlank())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "黑名单触发类型不能为空");
        }

        // 有效期校验
        if (cmd.getStartDate() != null && cmd.getEndDate() != null
                && cmd.getStartDate().isAfter(cmd.getEndDate())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "有效期开始时间不能晚于结束时间");
        }

        VehicleList entity = new VehicleList();
        entity.setPlateNumber(plate);
        entity.setListType(listType);
        entity.setParkingLotId(lotId);
        entity.setStartDate(cmd.getStartDate());
        entity.setEndDate(cmd.getEndDate());
        entity.setTriggerType(VehicleList.TYPE_WHITE.equals(listType) ? null : cmd.getTriggerType());
        entity.setStatus(VehicleList.STATUS_ACTIVE);
        entity.setRemark(cmd.getRemark());

        baseMapper.insert(entity);

        // 失效缓存
        cacheStore.evict(lotId, plate);

        log.info("名单已创建: id={} plate={} type={} lotId={}", entity.getId(), plate, listType, lotId);
        return entity;
    }

    @Transactional
    public VehicleList update(Long id, VehicleListUpdateCmd cmd) {
        VehicleList entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "名单记录不存在");
        }

        String plate = cmd.getPlateNumber().toUpperCase();
        Long lotId = cmd.getParkingLotId() != null ? cmd.getParkingLotId() : entity.getParkingLotId();
        String listType = cmd.getListType() != null ? cmd.getListType() : entity.getListType();

        // 互斥校验
        String oppositeType = VehicleList.TYPE_BLACK.equals(listType)
                ? VehicleList.TYPE_WHITE : VehicleList.TYPE_BLACK;
        VehicleList conflict = vehicleListMapper.selectByLotAndPlateAndType(lotId, plate, oppositeType);
        if (conflict != null && !conflict.getId().equals(id)) {
            String oppositeLabel = LIST_TYPE_LABEL_MAP.getOrDefault(oppositeType, oppositeType);
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "该车牌已存在于" + oppositeLabel + "中，无法同时设置为" + getListTypeLabel(listType));
        }

        if (cmd.getStartDate() != null && cmd.getEndDate() != null
                && cmd.getStartDate().isAfter(cmd.getEndDate())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "有效期开始时间不能晚于结束时间");
        }

        entity.setPlateNumber(plate);
        entity.setListType(listType);
        if (cmd.getParkingLotId() != null) {
            entity.setParkingLotId(cmd.getParkingLotId());
        }
        entity.setStartDate(cmd.getStartDate() != null ? cmd.getStartDate() : entity.getStartDate());
        entity.setEndDate(cmd.getEndDate() != null ? cmd.getEndDate() : entity.getEndDate());
        if (VehicleList.TYPE_WHITE.equals(listType)) {
            entity.setTriggerType(null);
        } else if (cmd.getTriggerType() != null) {
            entity.setTriggerType(cmd.getTriggerType());
        }
        entity.setRemark(cmd.getRemark() != null ? cmd.getRemark() : entity.getRemark());

        baseMapper.updateById(entity);

        // 失效原车牌+新车牌的缓存
        cacheStore.evict(entity.getParkingLotId(), entity.getPlateNumber());
        if (!plate.equals(entity.getPlateNumber())) {
            cacheStore.evict(entity.getParkingLotId(), plate);
        }

        log.info("名单已更新: id={} plate={} type={}", id, plate, listType);
        return entity;
    }

    @Transactional
    public void delete(Long id) {
        VehicleList entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "名单记录不存在");
        }
        baseMapper.deleteById(id);
        cacheStore.evict(entity.getParkingLotId(), entity.getPlateNumber());
        log.info("名单已删除: id={} plate={}", id, entity.getPlateNumber());
    }

    public IPage<VehicleListVO> pageList(VehicleListPageQuery query) {
        Page<VehicleList> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<VehicleList> wrapper = new LambdaQueryWrapper<VehicleList>()
                .eq(query.getParkingLotId() != null, VehicleList::getParkingLotId, query.getParkingLotId())
                .eq(query.getListType() != null && !query.getListType().isEmpty(),
                        VehicleList::getListType, query.getListType())
                .eq(query.getPlateNumber() != null && !query.getPlateNumber().isEmpty(),
                        VehicleList::getPlateNumber, query.getPlateNumber().toUpperCase())
                .orderByDesc(VehicleList::getCreatedAt);

        IPage<VehicleList> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    public VehicleListVO detail(Long id) {
        VehicleList entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "名单记录不存在");
        }
        return toVO(entity);
    }

    // ==================== 判定方法（识别链路用） ====================

    /**
     * 判定车辆是否白名单（缓存优先）。
     */
    public boolean isWhitelisted(Long parkingLotId, String plateNumber) {
        String cached = cacheStore.get(parkingLotId, plateNumber);
        if (cached != null) {
            if (VehicleListCacheStore.NULL_MARKER.equals(cached)) return false;
            return cached.startsWith("WHITE");
        }

        VehicleList entity = vehicleListMapper.selectActiveWhite(parkingLotId, plateNumber.toUpperCase());
        if (entity != null && isInDateRange(entity)) {
            cacheStore.put(parkingLotId, plateNumber, "WHITE");
            return true;
        }
        cacheStore.put(parkingLotId, plateNumber, VehicleListCacheStore.NULL_MARKER);
        return false;
    }

    /**
     * 判定车辆是否黑名单（缓存优先）。
     */
    public boolean isBlacklisted(Long parkingLotId, String plateNumber) {
        return resolveBlacklist(parkingLotId, plateNumber) != null;
    }

    /**
     * 解析黑名单规则（返回完整实体）。
     */
    public VehicleList resolveBlacklist(Long parkingLotId, String plateNumber) {
        String cached = cacheStore.get(parkingLotId, plateNumber);
        if (cached != null) {
            if (VehicleListCacheStore.NULL_MARKER.equals(cached)) return null;
            if (cached.startsWith("BLACK")) {
                VehicleList entity = vehicleListMapper.selectActiveBlack(parkingLotId, plateNumber.toUpperCase());
                if (entity != null && isInDateRange(entity)) return entity;
            }
            return null;
        }

        VehicleList entity = vehicleListMapper.selectActiveBlack(parkingLotId, plateNumber.toUpperCase());
        if (entity != null && isInDateRange(entity)) {
            cacheStore.put(parkingLotId, plateNumber, "BLACK:" + entity.getTriggerType());
            return entity;
        }
        cacheStore.put(parkingLotId, plateNumber, VehicleListCacheStore.NULL_MARKER);
        return null;
    }

    /**
     * 入场判定：返回拦截/告警决策。
     */
    public VehicleListDecisionVO checkEntry(Long parkingLotId, String plateNumber) {
        VehicleListDecisionVO decision = new VehicleListDecisionVO();
        decision.setDenyEntry(false);
        decision.setAlert(false);

        // 1. 检查白名单（命中短路，不往下）
        if (isWhitelisted(parkingLotId, plateNumber)) {
            decision.setListType(VehicleList.TYPE_WHITE);
            decision.setReason("白名单车辆，自动放行");
            return decision;
        }

        // 2. 检查黑名单
        VehicleList black = resolveBlacklist(parkingLotId, plateNumber);
        if (black == null) {
            return decision; // 不在任何名单中
        }

        decision.setListType(VehicleList.TYPE_BLACK);
        decision.setTriggerType(black.getTriggerType());

        // 3. 读取触发模式（从 sys_config，此处简化为直接查 DB 或缓存，实际接入 ParamResolver）
        String triggerMode = resolveTriggerMode(parkingLotId);

        switch (triggerMode) {
            case "DENY_ENTRY":
                decision.setDenyEntry(true);
                decision.setReason("黑名单车辆，禁止入场");
                break;
            case "ALLOW_WITH_ALERT":
                decision.setAlert(true);
                decision.setReason("黑名单车辆，允许入场但已触发告警");
                break;
            case "BY_TYPE":
                if (VehicleList.TRIGGER_ARREARS.equals(black.getTriggerType())) {
                    decision.setDenyEntry(true);
                    decision.setReason("欠费类黑名单车辆，禁止入场");
                } else if (VehicleList.TRIGGER_MANAGEMENT.equals(black.getTriggerType())) {
                    decision.setAlert(true);
                    decision.setReason("管理类黑名单车辆，允许入场但已触发告警");
                } else {
                    decision.setDenyEntry(true);
                    decision.setReason("其他类黑名单车辆，禁止入场");
                }
                break;
            default:
                decision.setDenyEntry(true);
                decision.setReason("黑名单车辆，禁止入场（默认策略）");
        }

        return decision;
    }

    // ==================== 私有方法 ====================

    private boolean isInDateRange(VehicleList entity) {
        LocalDate now = LocalDate.now();
        if (entity.getStartDate() != null && now.isBefore(entity.getStartDate())) return false;
        if (entity.getEndDate() != null && now.isAfter(entity.getEndDate())) return false;
        return true;
    }

    private String resolveTriggerMode(Long parkingLotId) {
        // 简化实现：从 sys_config 读取，后续接入 ParamResolver。默认 DENY_ENTRY
        return "DENY_ENTRY";
    }

    private VehicleListVO toVO(VehicleList entity) {
        VehicleListVO vo = new VehicleListVO();
        vo.setId(entity.getId());
        vo.setPlateNumber(entity.getPlateNumber());
        vo.setListType(entity.getListType());
        vo.setListTypeLabel(LIST_TYPE_LABEL_MAP.getOrDefault(entity.getListType(), entity.getListType()));
        vo.setParkingLotId(entity.getParkingLotId());
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setTriggerType(entity.getTriggerType());
        vo.setTriggerTypeLabel(TRIGGER_TYPE_LABEL_MAP.getOrDefault(entity.getTriggerType(), ""));
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(STATUS_LABEL_MAP.getOrDefault(entity.getStatus(), entity.getStatus()));
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String getListTypeLabel(String listType) {
        return LIST_TYPE_LABEL_MAP.getOrDefault(listType, listType);
    }
}
```

- [x] **Step 4: 运行测试确认通过**

```bash
mvn test -pl parking-system -am -Dtest=VehicleListServiceTest
```

Expected: All tests PASS.

- [x] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/service/VehicleListService.java \
        parking-system/src/test/java/com/jushan/system/service/VehicleListServiceTest.java
git commit -m "[ADMIN-008] feat: add VehicleListService with mutual exclusion and cache"
```

---

### Task 5: VehicleListController — 运营端 CRUD 接口

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/VehicleListController.java`

**Description:** 名单管理 CRUD 控制器，含权限注解、Swagger、触发类型字典接口。

- [x] **Step 1: 编写 VehicleListController**

```java
package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.framework.auth.RequirePermission;
import com.jushan.framework.log.BusinessLog;
import com.jushan.system.dto.VehicleListCreateCmd;
import com.jushan.system.dto.VehicleListPageQuery;
import com.jushan.system.dto.VehicleListUpdateCmd;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.service.VehicleListService;
import com.jushan.system.vo.VehicleListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 车辆黑白名单管理接口。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Tag(name = "黑白名单管理")
@RestController
@RequestMapping("/api/v1/admin/vehicle-list")
public class VehicleListController {

    private final VehicleListService vehicleListService;

    public VehicleListController(VehicleListService vehicleListService) {
        this.vehicleListService = vehicleListService;
    }

    @Operation(summary = "创建名单")
    @PostMapping
    @RequirePermission("vehicle-list:create")
    @BusinessLog("创建黑白名单")
    public R<VehicleListVO> create(@RequestBody @Valid VehicleListCreateCmd cmd) {
        VehicleList entity = vehicleListService.create(cmd);
        return R.ok(vehicleListService.detail(entity.getId()));
    }

    @Operation(summary = "编辑名单")
    @PutMapping("/{id}")
    @RequirePermission("vehicle-list:update")
    @BusinessLog("编辑黑白名单")
    public R<VehicleListVO> update(@PathVariable Long id, @RequestBody @Valid VehicleListUpdateCmd cmd) {
        VehicleList entity = vehicleListService.update(id, cmd);
        return R.ok(vehicleListService.detail(entity.getId()));
    }

    @Operation(summary = "删除名单")
    @DeleteMapping("/{id}")
    @RequirePermission("vehicle-list:delete")
    @BusinessLog("删除黑白名单")
    public R<Void> delete(@PathVariable Long id) {
        vehicleListService.delete(id);
        return R.ok();
    }

    @Operation(summary = "分页查询名单")
    @GetMapping("/page")
    @RequirePermission("vehicle-list:view")
    public R<IPage<VehicleListVO>> page(@Valid VehicleListPageQuery query) {
        return R.ok(vehicleListService.pageList(query));
    }

    @Operation(summary = "名单详情")
    @GetMapping("/{id}")
    @RequirePermission("vehicle-list:view")
    public R<VehicleListVO> detail(@PathVariable Long id) {
        return R.ok(vehicleListService.detail(id));
    }

    @Operation(summary = "黑名单触发类型字典")
    @GetMapping("/trigger-types")
    @RequirePermission("vehicle-list:view")
    public R<List<Map<String, String>>> triggerTypes() {
        List<Map<String, String>> types = Arrays.asList(
                Map.of("code", VehicleList.TRIGGER_ARREARS, "label", "欠费类"),
                Map.of("code", VehicleList.TRIGGER_MANAGEMENT, "label", "管理类"),
                Map.of("code", VehicleList.TRIGGER_OTHER, "label", "其他类")
        );
        return R.ok(types);
    }
}
```

- [x] **Step 2: Compile and commit**

```bash
mvn clean compile -pl parking-system -am
```

Expected: BUILD SUCCESS.

```bash
git add parking-system/src/main/java/com/jushan/system/controller/VehicleListController.java
git commit -m "[ADMIN-008] feat: add VehicleListController with CRUD APIs"
```

---

### Task 6: VehicleTypeDecisionService 改造 — 插入白名单/黑名单最高优先级

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/vehicle/service/VehicleTypeDecisionService.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImpl.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/vehicle/vo/VehicleTypeDecisionVO.java`
- Modify: `parking-system/src/test/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImplTest.java`

**Description:** 在现有判定链最高优先级插入 white/black list 检查，新增 `decide(plateNumber, parkingLotId, tenantId)` 重载。

- [x] **Step 1: VehicleTypeDecisionVO 新增字段**

修改 `VehicleTypeDecisionVO.java`，新增：
```java
/** 黑名单触发类型（仅黑名单命中时非null） */
private String triggerType;

/** 黑名单触发类型中文 */
private String triggerTypeLabel;
```

- [x] **Step 2: VehicleTypeDecisionService 接口新增方法**

修改 `VehicleTypeDecisionService.java`，新增方法签名：
```java
/**
 * 判定车辆类型（含车场维度，供黑白名单判定使用）。
 *
 * @param plateNumber  车牌号
 * @param parkingLotId 停车场ID（null=跳过名单查询）
 * @param tenantId     租户ID
 * @return 判定结果
 */
VehicleTypeDecisionVO decide(String plateNumber, Long parkingLotId, Long tenantId);
```

- [x] **Step 3: VehicleTypeDecisionServiceImpl 改造**

修改 `VehicleTypeDecisionServiceImpl.java`，注入 `VehicleListService`，在 `decide(String, Long, Long)` 中插入白名单/黑名单检查：

在构造函数注入 `VehicleListService vehicleListService`。

新增 `decide(String plateNumber, Long parkingLotId, Long tenantId)` 方法：
```java
@Override
public VehicleTypeDecisionVO decide(String plateNumber, Long parkingLotId, Long tenantId) {
    String standardizedPlate = plateNumber.toUpperCase();

    // ====== 新增：步骤 -1 白名单检查 ======
    if (parkingLotId != null && vehicleListService != null
            && vehicleListService.isWhitelisted(parkingLotId, standardizedPlate)) {
        VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
        result.setPlateNumber(standardizedPlate);
        result.setVehicleType("WHITE");
        result.setTypeDescription("白名单车辆");
        result.setAllowEntry(true);
        result.setAllowExit(true);
        result.setNeedCharge(false);
        result.setDecisionReason("白名单车辆，自动放行不计费");
        return result;
    }

    // ====== 新增：步骤 0' 黑名单检查 ======
    if (parkingLotId != null && vehicleListService != null) {
        VehicleList black = vehicleListService.resolveBlacklist(parkingLotId, standardizedPlate);
        if (black != null) {
            VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
            result.setPlateNumber(standardizedPlate);
            result.setVehicleType("BLACK");
            result.setTypeDescription("黑名单车辆");
            result.setTriggerType(black.getTriggerType());
            result.setTriggerTypeLabel(TRIGGER_TYPE_LABEL_MAP.getOrDefault(black.getTriggerType(), ""));

            String triggerMode = resolveTriggerMode(parkingLotId);
            switch (triggerMode) {
                case "DENY_ENTRY":
                    result.setAllowEntry(false);
                    result.setAllowExit(false);
                    result.setNeedCharge(false);
                    result.setDecisionReason("黑名单车辆，禁止入场");
                    break;
                case "ALLOW_WITH_ALERT":
                    result.setAllowEntry(true);
                    result.setAllowExit(true);
                    result.setNeedCharge(true);
                    result.setDecisionReason("黑名单车辆，允许入场但已触发告警");
                    break;
                case "BY_TYPE":
                    if (VehicleList.TRIGGER_ARREARS.equals(black.getTriggerType())) {
                        result.setAllowEntry(false);
                        result.setAllowExit(false);
                        result.setNeedCharge(false);
                        result.setDecisionReason("欠费类黑名单车辆，禁止入场");
                    } else if (VehicleList.TRIGGER_MANAGEMENT.equals(black.getTriggerType())) {
                        result.setAllowEntry(true);
                        result.setAllowExit(true);
                        result.setNeedCharge(true);
                        result.setDecisionReason("管理类黑名单车辆，允许入场但已触发告警");
                    } else {
                        result.setAllowEntry(false);
                        result.setAllowExit(false);
                        result.setNeedCharge(false);
                        result.setDecisionReason("其他类黑名单车辆，禁止入场");
                    }
                    break;
                default:
                    result.setAllowEntry(false);
                    result.setAllowExit(false);
                    result.setNeedCharge(false);
                    result.setDecisionReason("黑名单车辆，禁止入场");
            }
            return result;
        }
    }

    // ====== 原链路 continue ======
    // 步骤 0: 查询月卡
    MonthlyPass monthlyPass = ... (现有代码保持不变);
    // ... 后续步骤不变
}
```

- [x] **Step 4: update decide(String) 旧方法为 deprecated**

修改旧 `decide(String plateNumber)` 方法，标记 `@Deprecated`，内部调用 `decide(plateNumber, null, TenantContext.getTenantId())`。

- [x] **Step 5: 新增 triggerMode 读取方法**

```java
private String resolveTriggerMode(Long parkingLotId) {
    if (paramResolver != null) {
        String mode = paramResolver.get("blacklist.trigger_mode", parkingLotId);
        return mode != null ? mode : "DENY_ENTRY";
    }
    return "DENY_ENTRY";
}
```

- [x] **Step 6: run tests and commit**

```bash
mvn test -pl parking-system -am -Dtest=VehicleTypeDecisionServiceImplTest
mvn clean compile -pl parking-system -am
```

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/vehicle/service/VehicleTypeDecisionService.java \
        parking-system/src/main/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImpl.java \
        parking-system/src/main/java/com/jushan/platform/modules/vehicle/vo/VehicleTypeDecisionVO.java
git commit -m "[ADMIN-008] feat: integrate vehicle_list into VehicleTypeDecisionService priority chain"
```

---

### Task 7: MonitorAlert 新增黑名单告警类型

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/MonitorAlert.java`
- Modify: `parking-system/src/main/java/com/jushan/system/service/MonitorAlertService.java`

**Description:** 新增 TYPE_BLACKLIST_ENTRY 告警常量和 createBlacklistEntryAlert 方法。

- [x] **Step 1: MonitorAlert 新增常量**

在 `MonitorAlert.java` 中新增：
```java
/** 告警类型：黑名单车辆入场 */
public static final String TYPE_BLACKLIST_ENTRY = "BLACKLIST_ENTRY";
```

- [x] **Step 2: MonitorAlertService 新增方法**

在 `MonitorAlertService.java` 中新增：
```java
@Transactional
public void createBlacklistEntryAlert(Long tenantId, Long parkingLotId,
                                       String plateNumber, String triggerType) {
    if (existsUnacknowledged(parkingLotId, MonitorAlert.TYPE_BLACKLIST_ENTRY, plateNumber)) {
        return;
    }
    String typeDesc = resolveTriggerTypeLabel(triggerType);
    MonitorAlert alert = new MonitorAlert();
    alert.setTenantId(tenantId);
    alert.setParkingLotId(parkingLotId);
    alert.setAlertType(MonitorAlert.TYPE_BLACKLIST_ENTRY);
    alert.setSeverity(MonitorAlert.SEVERITY_WARNING);
    alert.setSourceId(plateNumber);
    alert.setMessage(String.format("黑名单车辆入场: 车牌%s, 触发类型: %s", plateNumber, typeDesc));
    alert.setAcknowledged(0);
    alert.setCreatedAt(LocalDateTime.now());
    saveAndPush(alert);
}

private String resolveTriggerTypeLabel(String triggerType) {
    return switch (triggerType) {
        case "ARREARS" -> "欠费类";
        case "MANAGEMENT" -> "管理类";
        case "OTHER" -> "其他类";
        default -> triggerType != null ? triggerType : "未知";
    };
}
```

- [x] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/MonitorAlert.java \
        parking-system/src/main/java/com/jushan/system/service/MonitorAlertService.java
git commit -m "[ADMIN-008] feat: add BLACKLIST_ENTRY alert type for blacklist vehicles"
```

---

### Task 8: EntryService 改造 — 黑白名单入场拦截

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/EntryService.java`

**Description:** 在重复入场检测后插入黑白名单拦截步骤，注入 VehicleListService。

- [x] **Step 1: 注入 VehicleListService + VehicleTypeDecisionService (parkingLotId版)**

在 `EntryService` 构造函数中新增依赖注入：
```java
private final VehicleListService vehicleListService;
```

- [x] **Step 2: handleEntry 中新增黑白名单拦截**

在 `handleEntry()` 方法中，步骤 2（重复入场检测）之后、步骤 3（创建 ParkingRecord）之前，新增：
```java
// 2e. 黑白名单入场判定（任务包 3-3 新增）
if (vehicleListService != null) {
    VehicleListDecisionVO listDecision = vehicleListService.checkEntry(
            parkingLotId, standardizedPlate);

    if (listDecision.isDenyEntry()) {
        log.warn("黑名单车辆禁止入场: plate={} lotId={} listType={} triggerType={}",
                standardizedPlate, parkingLotId, listDecision.getListType(),
                listDecision.getTriggerType());
        throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                listDecision.getReason());
    }

    if (listDecision.isAlert()) {
        // 黑名单允许入场但需告警
        log.warn("黑名单车辆允许入场但触发告警: plate={} lotId={} triggerType={}",
                standardizedPlate, parkingLotId, listDecision.getTriggerType());
        monitorAlertService.createBlacklistEntryAlert(
                payload.getTenantId(), parkingLotId, standardizedPlate,
                listDecision.getTriggerType());
    }
}
```

- [x] **Step 3: createPreOrderIfChargeable 适配 parkingLotId**

修改 `createPreOrderIfChargeable()` 中调用 `vehicleTypeDecisionService.decide()` 为带 parkingLotId 的新重载：
```java
decision = vehicleTypeDecisionService.decide(
        record.getStandardizedPlate(),
        record.getParkingLotId(),
        record.getTenantId());
```

- [x] **Step 4: Compile and commit**

```bash
mvn clean compile -pl parking-system -am
```

```bash
git add parking-system/src/main/java/com/jushan/system/service/EntryService.java
git commit -m "[ADMIN-008] feat: add blacklist/whitelist entry interception in EntryService"
```

---

### Task 9: ExitService 改造 — 黑白名单出场判定

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ExitService.java`

**Description:** 出场链路中注入 VehicleTypeDecisionService，在 resolveReleaseDecision 前做黑白名单判定。

- [x] **Step 1: 注入 VehicleTypeDecisionService**

在 `ExitService` 构造函数中新增或确认已有 `VehicleTypeDecisionService vehicleTypeDecisionService`。

- [x] **Step 2: handleExit 中新增黑白名单出场判定**

在 `handleExit()` 方法计费步骤之前新增：
```java
// 黑白名单出场判定（任务包 3-3 新增）
if (vehicleTypeDecisionService != null) {
    VehicleTypeDecisionVO exitDecision = vehicleTypeDecisionService.decide(
            standardizedPlate, parkingLotId, record.getTenantId());

    if ("WHITE".equals(exitDecision.getVehicleType())) {
        // 白名单：免费放行，feeCents 置零
        log.info("白名单车辆出场零费放行: plate={} lotId={}", standardizedPlate, parkingLotId);
        feeCents = 0;
    }

    if ("BLACK".equals(exitDecision.getVehicleType()) && !exitDecision.getAllowExit()) {
        // 黑名单禁止出场
        log.warn("黑名单车辆禁止出场: plate={} lotId={}", standardizedPlate, parkingLotId);
        throw new BusinessException(CommonErrorCode.PARAM_ERROR, exitDecision.getDecisionReason());
    }
}
```

- [x] **Step 3: Compile and commit**

```bash
mvn clean compile -pl parking-system -am
```

```bash
git add parking-system/src/main/java/com/jushan/system/service/ExitService.java
git commit -m "[ADMIN-008] feat: add blacklist/whitelist exit handling in ExitService"
```

---

### Task 10: 定时到期任务 + OverstayBlacklistScheduler 关闭

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/task/VehicleListExpiryTask.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/task/OverstayBlacklistScheduler.java`

**Description:** 每天凌晨扫描到期名单置 EXPIRED；自动拉黑开关默认关闭。

- [x] **Step 1: VehicleListExpiryTask**

```java
package com.jushan.system.task;

import com.jushan.system.cache.VehicleListCacheStore;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.mapper.VehicleListMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 名单到期定时任务。每天凌晨 2:00 扫描到期名单。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Slf4j
@Component
public class VehicleListExpiryTask {

    private static final int BATCH_SIZE = 500;

    private final VehicleListMapper vehicleListMapper;
    private final VehicleListCacheStore cacheStore;

    public VehicleListExpiryTask(VehicleListMapper vehicleListMapper, VehicleListCacheStore cacheStore) {
        this.vehicleListMapper = vehicleListMapper;
        this.cacheStore = cacheStore;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void expireVehicleList() {
        log.info("开始执行名单到期扫描");
        int totalExpired = 0;
        List<VehicleList> batch;
        do {
            batch = vehicleListMapper.selectExpired(BATCH_SIZE);
            for (VehicleList entity : batch) {
                entity.setStatus(VehicleList.STATUS_EXPIRED);
                entity.setUpdatedAt(LocalDateTime.now());
                vehicleListMapper.updateById(entity);
                cacheStore.evict(entity.getParkingLotId(), entity.getPlateNumber());
                totalExpired++;
            }
        } while (!batch.isEmpty());

        if (totalExpired > 0) {
            log.info("名单到期扫描完成: 到期 {} 条", totalExpired);
        }
    }
}
```

- [x] **Step 2: OverstayBlacklistScheduler 修改**

1. 在类上添加 `@ConditionalOnProperty`:
```java
@ConditionalOnProperty(name = "jushan.overstay-blacklist.enabled", havingValue = "true")
```
2. 修改 `@Value` 默认值为 `false`:
```java
@Value("${jushan.overstay-blacklist.enabled:false}")
```
3. Javadoc 追加：
```
 * 二期评估：需确认自动拉黑策略与现有 vehicle_list 表的联动方式。
 * 开关：jushan.overstay-blacklist.enabled，默认 false。
```

- [x] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/task/VehicleListExpiryTask.java \
        parking-system/src/main/java/com/jushan/platform/task/OverstayBlacklistScheduler.java
git commit -m "[ADMIN-008] feat: add expiry task for vehicle_list; disable auto-blacklist by default"
```

---

### Task 11: 端到端编译验证 + 最终提交

- [x] **Step 1: 全量编译**

```bash
mvn clean compile -pl parking-system -am
```

Expected: BUILD SUCCESS.

- [x] **Step 2: 运行所有相关测试**

```bash
mvn test -pl parking-system -am
```

- [x] **Step 3: 最终审视和提交**

```bash
git status
git log --oneline -15
```

---

### Implementation Order

1. Task 1 → Flyway (DB schema + migration)
2. Task 2 → Entity + Mapper + DTO/VO
3. Task 3 → CacheStore
4. Task 4 → VehicleListService (with tests)
5. Task 5 → VehicleListController
6. Task 6 → VehicleTypeDecisionService 改造
7. Task 7 → MonitorAlert 扩展
8. Task 8 → EntryService 改造
9. Task 9 → ExitService 改造
10. Task 10 → 定时任务 + 开关
11. Task 11 → 最终验证
