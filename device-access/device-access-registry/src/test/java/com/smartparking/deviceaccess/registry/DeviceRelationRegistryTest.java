package com.smartparking.deviceaccess.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.entity.DeviceRelation;
import com.smartparking.deviceaccess.common.entity.DeviceRelationMapper;
import com.smartparking.deviceaccess.common.exception.DeviceNotFoundException;
import com.smartparking.deviceaccess.common.exception.InvalidRelationException;
import com.smartparking.deviceaccess.common.exception.RelationAlreadyExistsException;
import com.smartparking.deviceaccess.common.exception.RelationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceRelationRegistry 单元测试。
 * <p>
 * v0.3 新增。覆盖创建/启用/停用/删除/双向查询 + 校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceRelationRegistry Unit Tests")
class DeviceRelationRegistryTest {

    @Mock
    private DeviceRelationMapper relationMapper;

    @Mock
    private DeviceRegistry deviceRegistry;

    @Mock
    private DeviceProductRegistry productRegistry;

    private DeviceRelationRegistry registry;

    private static final String SRC = "cam-main-001";
    private static final String TGT = "cam-aux-001";

    @BeforeEach
    void setUp() {
        registry = new DeviceRelationRegistry(relationMapper, deviceRegistry, productRegistry);
    }

    private Device createDevice(String deviceId, Long productId) {
        Device d = new Device();
        d.setDeviceId(deviceId);
        d.setProductId(productId);
        d.setDeviceName("Device " + deviceId);
        return d;
    }

    private DeviceProduct createProduct(Long id, String deviceType) {
        DeviceProduct p = new DeviceProduct();
        p.setId(id);
        p.setDeviceType(deviceType);
        p.setProductName("Product " + id);
        return p;
    }

    // ──────────────────── 创建 ────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Should create relation successfully")
        void shouldCreateRelation() {
            Device source = createDevice(SRC, 1L);
            Device target = createDevice(TGT, 1L);
            when(deviceRegistry.findByDeviceId(SRC)).thenReturn(source);
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(target);
            when(productRegistry.getById(1L)).thenReturn(createProduct(1L, "CAMERA"));
            when(relationMapper.insert(any(DeviceRelation.class))).thenReturn(1);

            DeviceRelation result = registry.create(SRC, "AUX_CAMERA", TGT, "test remark");

            assertThat(result.getSourceDeviceId()).isEqualTo(SRC);
            assertThat(result.getTargetDeviceId()).isEqualTo(TGT);
            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getRemark()).isEqualTo("test remark");
        }

        @Test
        @DisplayName("Should throw InvalidRelationException when relating to self")
        void shouldRejectSelfRelation() {
            assertThatThrownBy(() -> registry.create(SRC, "AUX_CAMERA", SRC, ""))
                    .isInstanceOf(InvalidRelationException.class)
                    .hasMessageContaining("itself");
        }

        @Test
        @DisplayName("Should throw DeviceNotFoundException when source not found")
        void shouldThrowWhenSourceNotFound() {
            when(deviceRegistry.findByDeviceId(SRC)).thenReturn(null);

            assertThatThrownBy(() -> registry.create(SRC, "AUX_CAMERA", TGT, ""))
                    .isInstanceOf(DeviceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw DeviceNotFoundException when target not found")
        void shouldThrowWhenTargetNotFound() {
            when(deviceRegistry.findByDeviceId(SRC)).thenReturn(createDevice(SRC, 1L));
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(null);

            assertThatThrownBy(() -> registry.create(SRC, "AUX_CAMERA", TGT, ""))
                    .isInstanceOf(DeviceNotFoundException.class);
        }

        @Test
        @DisplayName("Should reject AUX_CAMERA when devices are not both CAMERA")
        void shouldRejectAuxCameraForNonCamera() {
            when(deviceRegistry.findByDeviceId(SRC)).thenReturn(createDevice(SRC, 4L));
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(createDevice(TGT, 1L));
            when(productRegistry.getById(4L)).thenReturn(createProduct(4L, "DISPLAY"));
            when(productRegistry.getById(1L)).thenReturn(createProduct(1L, "CAMERA"));

            assertThatThrownBy(() -> registry.create(SRC, "AUX_CAMERA", TGT, ""))
                    .isInstanceOf(InvalidRelationException.class)
                    .hasMessageContaining("CAMERA type");
        }

        @Test
        @DisplayName("Should reject RS485_DISPLAY when target is not DISPLAY")
        void shouldRejectRs485ForNonDisplay() {
            when(deviceRegistry.findByDeviceId(SRC)).thenReturn(createDevice(SRC, 1L));
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(createDevice(TGT, 1L));
            when(productRegistry.getById(1L)).thenReturn(createProduct(1L, "CAMERA"));

            assertThatThrownBy(() -> registry.create(SRC, "RS485_DISPLAY", TGT, ""))
                    .isInstanceOf(InvalidRelationException.class)
                    .hasMessageContaining("DISPLAY type");
        }

        @Test
        @DisplayName("Should throw RelationAlreadyExistsException on duplicate")
        void shouldThrowOnDuplicate() {
            Device source = createDevice(SRC, 1L);
            Device target = createDevice(TGT, 1L);
            when(deviceRegistry.findByDeviceId(SRC)).thenReturn(source);
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(target);
            when(productRegistry.getById(1L)).thenReturn(createProduct(1L, "CAMERA"));
            when(relationMapper.insert(any(DeviceRelation.class)))
                    .thenThrow(new DuplicateKeyException("Duplicate entry"));

            assertThatThrownBy(() -> registry.create(SRC, "AUX_CAMERA", TGT, ""))
                    .isInstanceOf(RelationAlreadyExistsException.class);
        }
    }

    // ──────────────────── 启用/停用 ────────────────────

    @Nested
    @DisplayName("enable() / disable()")
    class EnableDisable {

        @Test
        @DisplayName("Should enable a disabled relation")
        void shouldEnable() {
            DeviceRelation rel = new DeviceRelation();
            rel.setId(1L);
            rel.setEnabled(false);
            when(relationMapper.selectById(1L)).thenReturn(rel);
            when(relationMapper.updateById(any(DeviceRelation.class))).thenReturn(1);

            registry.enable(1L);

            assertThat(rel.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should disable an enabled relation")
        void shouldDisable() {
            DeviceRelation rel = new DeviceRelation();
            rel.setId(1L);
            rel.setEnabled(true);
            when(relationMapper.selectById(1L)).thenReturn(rel);
            when(relationMapper.updateById(any(DeviceRelation.class))).thenReturn(1);

            registry.disable(1L);

            assertThat(rel.getEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should throw RelationNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(relationMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> registry.enable(99L))
                    .isInstanceOf(RelationNotFoundException.class);
            assertThatThrownBy(() -> registry.disable(99L))
                    .isInstanceOf(RelationNotFoundException.class);
        }
    }

    // ──────────────────── 删除 ────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("Should delete relation")
        void shouldDelete() {
            DeviceRelation rel = new DeviceRelation();
            rel.setId(1L);
            when(relationMapper.selectById(1L)).thenReturn(rel);
            when(relationMapper.deleteById(1L)).thenReturn(1);

            registry.delete(1L);

            verify(relationMapper).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw RelationNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(relationMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> registry.delete(99L))
                    .isInstanceOf(RelationNotFoundException.class);
        }
    }

    // ──────────────────── 双向查询 ────────────────────

    @Nested
    @DisplayName("getRelations() — bidirectional")
    class GetRelations {

        @Test
        @DisplayName("Should return OUTBOUND relations")
        void shouldReturnOutbound() {
            DeviceRelation rel = new DeviceRelation();
            rel.setId(1L);
            rel.setSourceDeviceId(SRC);
            rel.setTargetDeviceId(TGT);
            rel.setRelationType("AUX_CAMERA");
            rel.setEnabled(true);
            when(relationMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(rel))  // outbound
                    .thenReturn(List.of());     // inbound (empty)
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(createDevice(TGT, 1L));

            List<DeviceRelation> result = registry.getRelations(SRC);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTargetDevice().getDeviceId()).isEqualTo(TGT);
        }

        @Test
        @DisplayName("Should return INBOUND relations")
        void shouldReturnInbound() {
            DeviceRelation rel = new DeviceRelation();
            rel.setId(2L);
            rel.setSourceDeviceId("other-cam");
            rel.setTargetDeviceId(SRC);
            rel.setRelationType("AUX_CAMERA");
            rel.setEnabled(true);
            when(relationMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of())         // outbound (empty)
                    .thenReturn(List.of(rel));    // inbound
            when(deviceRegistry.findByDeviceId("other-cam")).thenReturn(createDevice("other-cam", 1L));

            List<DeviceRelation> result = registry.getRelations(SRC);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTargetDevice().getDeviceId()).isEqualTo("other-cam");
        }
    }

    // ──────────────────── 按类型查询已启用 ────────────────────

    @Nested
    @DisplayName("getEnabledTargets()")
    class GetEnabledTargets {

        @Test
        @DisplayName("Should return only enabled targets of given type")
        void shouldReturnEnabledTargets() {
            DeviceRelation rel = new DeviceRelation();
            rel.setTargetDeviceId(TGT);
            when(relationMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(rel));
            when(deviceRegistry.findByDeviceId(TGT)).thenReturn(createDevice(TGT, 1L));

            List<Device> targets = registry.getEnabledTargets(SRC, "AUX_CAMERA");

            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).getDeviceId()).isEqualTo(TGT);
        }
    }
}
