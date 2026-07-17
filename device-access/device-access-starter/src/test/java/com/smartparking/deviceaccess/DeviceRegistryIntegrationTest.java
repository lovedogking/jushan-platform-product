package com.smartparking.deviceaccess;

import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceMapper;
import com.smartparking.deviceaccess.common.entity.DeviceRelation;
import com.smartparking.deviceaccess.common.exception.DeviceAlreadyExistsException;
import com.smartparking.deviceaccess.common.exception.DeviceNotFoundException;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import com.smartparking.deviceaccess.registry.DeviceRelationRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * DeviceRegistry 集成测试。
 * <p>
 * 使用已有 MySQL 容器（localhost:3306），测试完整 CRUD 流程。
 * 每个测试方法使用唯一 deviceId（带 UUID 后缀），完全隔离。
 * 测试结束后通过原始 JDBC 硬删除清理数据（绕过 MyBatis Plus @TableLogic）。
 * <p>
 * 前提：device-access-mysql 容器必须已启动。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.MethodName.class)
@DisplayName("DeviceRegistry Integration Tests")
class DeviceRegistryIntegrationTest {

    @Autowired
    private DeviceRegistry deviceRegistry;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DeviceProductRegistry productRegistry;

    @Autowired
    private DeviceRelationRegistry relationRegistry;

    private String id1;
    private String id2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        id1 = "test-" + suffix + "-1";
        id2 = "test-" + suffix + "-2";
    }

    @AfterAll
    static void cleanUpAll() {
        // 硬删除所有测试残留数据（绕过 MyBatis Plus @TableLogic）
        try (Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/device_access?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai",
                "root", "deviceaccess");
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM t_device WHERE device_id LIKE 'test-%'")) {
            int deleted = ps.executeUpdate();
            System.out.println("Cleaned up " + deleted + " test devices");
        } catch (Exception e) {
            System.err.println("Cleanup failed: " + e.getMessage());
        }
    }

    private Device newDevice(String id, String name) {
        Device d = new Device();
        d.setDeviceId(id);
        d.setDeviceName(name);
        d.setProductId(1L);  // 产品ID=1: ZHENSHI C5H
        return d;
    }

    // ──────────────────── 注册 ────────────────────

    @Test
    @DisplayName("Should register a new device")
    void shouldRegisterDevice() {
        Device saved = deviceRegistry.register(newDevice(id1, "集成测试设备"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDeviceId()).isEqualTo(id1);
        assertThat(saved.getDeviceName()).isEqualTo("集成测试设备");
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo("OFFLINE");
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("Should normalize deviceId to lowercase")
    void shouldNormalizeDeviceId() {
        Device d = new Device();
        d.setDeviceId("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        d.setDeviceName("大写测试");
        d.setProductId(1L);

        Device saved = deviceRegistry.register(d);
        assertThat(saved.getDeviceId()).isEqualTo(d.getDeviceId().toLowerCase());
    }

    @Test
    @DisplayName("Should throw DeviceAlreadyExistsException on duplicate")
    void shouldRejectDuplicate() {
        deviceRegistry.register(newDevice(id1, "第一次"));

        assertThatThrownBy(() -> deviceRegistry.register(newDevice(id1, "第二次")))
                .isInstanceOf(DeviceAlreadyExistsException.class)
                .hasMessageContaining(id1);
    }

    // ──────────────────── 查询 ────────────────────

    @Test
    @DisplayName("Should find device by ID or return null")
    void shouldFindByDeviceId() {
        deviceRegistry.register(newDevice(id1, "查询测试"));

        Device found = deviceRegistry.findByDeviceId(id1);
        assertThat(found).isNotNull();
        assertThat(found.getDeviceName()).isEqualTo("查询测试");

        Device notFound = deviceRegistry.findByDeviceId("nonexistent");
        assertThat(notFound).isNull();
    }

    @Test
    @DisplayName("Should get device by ID or throw")
    void shouldGetByDeviceIdOrThrow() {
        deviceRegistry.register(newDevice(id1, "get测试"));

        Device found = deviceRegistry.getByDeviceId(id1);
        assertThat(found).isNotNull();

        assertThatThrownBy(() -> deviceRegistry.getByDeviceId("nonexistent"))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    // ──────────────────── 列表 ────────────────────

    @Test
    @DisplayName("Should list devices with filters")
    void shouldListWithFilters() {
        deviceRegistry.register(newDevice(id1, "设备A"));
        Device d2 = newDevice(id2, "设备B");
        d2.setStatus("ONLINE");
        deviceRegistry.register(d2);

        // 全量
        List<Device> all = deviceRegistry.list(null, null, null, null, null, null, null);
        assertThat(all).extracting(Device::getDeviceId).contains(id1, id2);

        // keyword
        List<Device> byKeyword = deviceRegistry.list("设备A", null, null, null, null, null, null);
        assertThat(byKeyword).hasSize(1);
        assertThat(byKeyword.get(0).getDeviceId()).isEqualTo(id1);

        // status（加 keyword 前缀过滤，避免真实设备数据污染）
        List<Device> byStatus = deviceRegistry.list("test-", null, null, "ONLINE", null, null, null);
        assertThat(byStatus).hasSize(1);
        assertThat(byStatus.get(0).getDeviceId()).isEqualTo(id2);

        // 无匹配
        List<Device> empty = deviceRegistry.list("不存在", null, null, null, null, null, null);
        assertThat(empty).isEmpty();
    }

    // ──────────────────── 更新 ────────────────────

    @Test
    @DisplayName("Should update device fields")
    void shouldUpdateDevice() {
        deviceRegistry.register(newDevice(id1, "原始名称"));

        Device updateFields = new Device();
        updateFields.setDeviceName("更新后的名称");
        updateFields.setRemark("新备注");

        Device updated = deviceRegistry.update(id1, updateFields);
        assertThat(updated.getDeviceName()).isEqualTo("更新后的名称");
        assertThat(updated.getRemark()).isEqualTo("新备注");
        assertThat(updated.getProductId()).isEqualTo(1L); // 未改动
    }

    @Test
    @DisplayName("Should throw DeviceNotFoundException when updating nonexistent")
    void shouldThrowOnUpdateNonexistent() {
        Device updateFields = new Device();
        updateFields.setDeviceName("新名称");

        assertThatThrownBy(() -> deviceRegistry.update("nonexistent", updateFields))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    // ──────────────────── 删除 ────────────────────

    @Test
    @DisplayName("Should soft-delete device")
    void shouldSoftDelete() {
        deviceRegistry.register(newDevice(id1, "待删除设备"));
        deviceRegistry.deleteByDeviceId(id1);

        // 软删除后 find 查不到
        Device found = deviceRegistry.findByDeviceId(id1);
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("Should throw DeviceNotFoundException when deleting nonexistent")
    void shouldThrowOnDeleteNonexistent() {
        assertThatThrownBy(() -> deviceRegistry.deleteByDeviceId("nonexistent"))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    // ──────────────────── 端到端 ────────────────────

    @Test
    @DisplayName("E2E: register → list → get → update → delete")
    void shouldCompleteFullLifecycle() {
        // Register
        Device registered = deviceRegistry.register(newDevice(id1, "E2E设备"));
        assertThat(registered.getId()).isNotNull();

        // List
        List<Device> list = deviceRegistry.list("E2E", null, null, null, null, null, null);
        assertThat(list).hasSize(1);

        // Get
        Device detail = deviceRegistry.getByDeviceId(id1);
        assertThat(detail.getDeviceName()).isEqualTo("E2E设备");

        // Update
        Device update = new Device();
        update.setDeviceName("E2E设备-已更新");
        Device updated = deviceRegistry.update(id1, update);
        assertThat(updated.getDeviceName()).isEqualTo("E2E设备-已更新");

        // Delete
        deviceRegistry.deleteByDeviceId(id1);
        assertThat(deviceRegistry.findByDeviceId(id1)).isNull();
    }

    // ═══════════════════════════════════════════
    // v0.3: 设备关系生命周期集成测试
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("Relation lifecycle: create → disable → enable → delete")
    void shouldCompleteRelationLifecycle() {
        // 注册两个 CAMERA 设备
        Device main = deviceRegistry.register(newDevice(id1, "主摄像头"));
        Device aux = deviceRegistry.register(newDevice(id2, "辅助摄像头"));

        // 1. 创建 AUX_CAMERA 关系
        DeviceRelation relation = relationRegistry.create(
                main.getDeviceId(), "AUX_CAMERA", aux.getDeviceId(), "测试辅助");
        assertThat(relation.getId()).isNotNull();
        assertThat(relation.getEnabled()).isTrue();
        assertThat(relation.getSourceDeviceId()).isEqualTo(main.getDeviceId());
        assertThat(relation.getTargetDeviceId()).isEqualTo(aux.getDeviceId());

        // 验证双向查询 — OUTBOUND (main 作为 source)
        List<DeviceRelation> mainRelations = relationRegistry.getRelations(main.getDeviceId());
        assertThat(mainRelations).hasSize(1);
        assertThat(mainRelations.get(0).getTargetDevice().getDeviceId()).isEqualTo(aux.getDeviceId());

        // 验证双向查询 — INBOUND (aux 作为 target)
        List<DeviceRelation> auxRelations = relationRegistry.getRelations(aux.getDeviceId());
        assertThat(auxRelations).hasSize(1);
        assertThat(auxRelations.get(0).getTargetDevice().getDeviceId()).isEqualTo(main.getDeviceId());

        // 2. 停用关系
        Long relId = relation.getId();
        relationRegistry.disable(relId);
        DeviceRelation disabled = relationRegistry.getRelations(main.getDeviceId()).get(0);
        assertThat(disabled.getEnabled()).isFalse();

        // 3. 启用关系
        relationRegistry.enable(relId);
        DeviceRelation enabled = relationRegistry.getRelations(main.getDeviceId()).get(0);
        assertThat(enabled.getEnabled()).isTrue();

        // 4. 删除关系
        relationRegistry.delete(relId);
        List<DeviceRelation> afterDelete = relationRegistry.getRelations(main.getDeviceId());
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @DisplayName("Should reject creating duplicate relation")
    void shouldRejectDuplicateRelation() {
        Device main = deviceRegistry.register(newDevice(id1, "主摄像头"));
        Device aux = deviceRegistry.register(newDevice(id2, "辅助摄像头"));
        relationRegistry.create(main.getDeviceId(), "AUX_CAMERA", aux.getDeviceId(), "");

        assertThatThrownBy(() ->
                relationRegistry.create(main.getDeviceId(), "AUX_CAMERA", aux.getDeviceId(), ""))
                .isInstanceOf(com.smartparking.deviceaccess.common.exception.RelationAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Should reject RS485_DISPLAY when target is CAMERA")
    void shouldRejectRs485DisplayForCamera() {
        Device main = deviceRegistry.register(newDevice(id1, "主摄像头"));
        Device aux = deviceRegistry.register(newDevice(id2, "辅助摄像头"));

        assertThatThrownBy(() ->
                relationRegistry.create(main.getDeviceId(), "RS485_DISPLAY", aux.getDeviceId(), ""))
                .isInstanceOf(com.smartparking.deviceaccess.common.exception.InvalidRelationException.class)
                .hasMessageContaining("DISPLAY");
    }
}
