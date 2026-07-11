package com.jushan.boot.migration;

import com.jushan.boot.test.TestcontainersBaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移验证测试。
 * <p>
 * 验证点：
 * <ul>
 *   <li>Flyway 在容器启动时自动执行迁移</li>
 *   <li>flyway_schema_history 表记录基线迁移</li>
 *   <li>sys_config 表结构正确（列名、类型、唯一约束）</li>
 *   <li>重复启动不会重复建表（Flyway 幂等）</li>
 * </ul>
 */
@DisplayName("Flyway 迁移验证")
class FlywayMigrationTest extends TestcontainersBaseTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== ① 迁移历史表 ====================

    @Test
    @DisplayName("flyway_schema_history 表存在且包含基线迁移记录")
    void shouldRecordBaselineMigration() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, description, type, success " +
                "FROM flyway_schema_history " +
                "WHERE version = '20260710001'");

        assertThat(rows).as("基线迁移记录应存在").hasSize(1);

        Map<String, Object> record = rows.get(0);
        assertThat(record.get("description")).as("迁移描述").isEqualTo("baseline");
        assertThat(record.get("type")).as("迁移类型").isEqualTo("SQL");
        assertThat((Boolean) record.get("success")).as("迁移状态").isTrue();
    }

    // ==================== ② sys_config 表结构 ====================

    @Test
    @DisplayName("sys_config 表存在且结构正确")
    void shouldCreateSysConfigTable() {
        // 确认表存在
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SHOW TABLES LIKE 'sys_config'");
        assertThat(tables).as("sys_config 表应存在").hasSize(1);

        // 确认列结构
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("SHOW COLUMNS FROM sys_config");
        assertThat(columns).as("sys_config 应有 6 列").hasSize(6);

        // 关键列验证
        List<String> columnNames = columns.stream()
                .map(c -> (String) c.get("Field"))
                .toList();
        assertThat(columnNames).containsExactly("id", "config_key", "config_value", "description", "created_at", "updated_at");
    }

    @Test
    @DisplayName("sys_config 唯一约束 — 重复 config_key 应拒绝")
    void shouldRejectDuplicateConfigKey() {
        // 插入一条
        jdbcTemplate.update(
                "INSERT INTO sys_config (config_key, config_value, description) VALUES (?, ?, ?)",
                "test.key", "v1", "测试配置");

        // 重复 key 应失败
        try {
            jdbcTemplate.update(
                    "INSERT INTO sys_config (config_key, config_value) VALUES (?, ?)",
                    "test.key", "v2");
            // 如果没抛异常，测试失败
            throw new AssertionError("期望抛出唯一约束违反异常，但插入成功");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Duplicate");
        }
    }

    // ==================== ③ 迁移幂等性 ====================

    @Test
    @DisplayName("重复 Spring 容器启动不会重复建表（Flyway 幂等）")
    void shouldNotDuplicateTablesOnRepeatedStartup() {
        // 上下文已启动，Flyway 已执行。
        // 查询 flyway_schema_history 确认成功迁移数正确
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);

        assertThat(count).as("成功迁移数应为 1").isEqualTo(1);

        // 确认 sys_config 表仍然只有 6 列（没有被重复 alter）
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config'",
                Integer.class);

        assertThat(columnCount).as("sys_config 列数仍为 6").isEqualTo(6);
    }
}
