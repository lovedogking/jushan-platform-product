package com.smartparking.deviceaccess.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceMapper;
import com.smartparking.deviceaccess.common.exception.DeviceAlreadyExistsException;
import com.smartparking.deviceaccess.common.exception.DeviceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceRegistry 单元测试。
 * <p>
 * 纯 Mockito，mock DeviceMapper，验证 CRUD 逻辑正确性。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceRegistry Unit Tests")
class DeviceRegistryTest {

    @Mock
    private DeviceMapper deviceMapper;

    private DeviceRegistry registry;

    private static final String TEST_ID = "test-device-001";
    private static final String TEST_NAME = "测试设备";

    @BeforeEach
    void setUp() {
        registry = new DeviceRegistry(deviceMapper);
    }

    // ──────────────────── 注册 ────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Should insert device with normalized ID and defaults")
        void shouldInsertWithDefaults() {
            Device device = new Device();
            device.setDeviceId("TEST-UPPER-001");
            device.setDeviceName("大写ID设备");
            device.setProductId(1L);

            when(deviceMapper.insert(any(Device.class))).thenReturn(1);

            Device result = registry.register(device);

            assertThat(result.getDeviceId()).isEqualTo("test-upper-001");
            assertThat(result.getStatus()).isEqualTo("OFFLINE");
            verify(deviceMapper).insert(device);
        }

        @Test
        @DisplayName("Should throw DeviceAlreadyExistsException on duplicate key")
        void shouldThrowOnDuplicate() {
            Device device = new Device();
            device.setDeviceId(TEST_ID);
            device.setDeviceName(TEST_NAME);

            when(deviceMapper.insert(any(Device.class)))
                    .thenThrow(new DuplicateKeyException("Duplicate entry"));

            assertThatThrownBy(() -> registry.register(device))
                    .isInstanceOf(DeviceAlreadyExistsException.class)
                    .hasMessageContaining(TEST_ID);
        }

        @Test
        @DisplayName("Should keep provided direction and status if specified")
        void shouldKeepProvidedDirectionAndStatus() {
            Device device = new Device();
            device.setDeviceId(TEST_ID);
            device.setDeviceName(TEST_NAME);
            device.setProductId(2L);
            device.setDirection("ENTRANCE");
            device.setStatus("UNKNOWN");

            when(deviceMapper.insert(any(Device.class))).thenReturn(1);

            Device result = registry.register(device);

            assertThat(result.getDirection()).isEqualTo("ENTRANCE");
            assertThat(result.getStatus()).isEqualTo("UNKNOWN");
        }
    }

    // ──────────────────── 查询 ────────────────────

    @Nested
    @DisplayName("findByDeviceId()")
    class FindByDeviceId {

        @Test
        @DisplayName("Should return device when found")
        void shouldReturnDevice() {
            Device device = new Device();
            device.setDeviceId(TEST_ID);
            device.setDeviceName(TEST_NAME);

            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(device);

            Device result = registry.findByDeviceId(TEST_ID);

            assertThat(result).isNotNull();
            assertThat(result.getDeviceId()).isEqualTo(TEST_ID);
        }

        @Test
        @DisplayName("Should return null when not found")
        void shouldReturnNull() {
            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Device result = registry.findByDeviceId("nonexistent");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should normalize to lowercase")
        void shouldNormalizeToLowercase() {
            Device device = new Device();
            device.setDeviceId(TEST_ID);

            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(device);

            registry.findByDeviceId("TEST-UPPER");

            ArgumentCaptor<LambdaQueryWrapper<Device>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(deviceMapper).selectOne(captor.capture());
        }
    }

    @Nested
    @DisplayName("getByDeviceId()")
    class GetByDeviceId {

        @Test
        @DisplayName("Should return device when found")
        void shouldReturnDevice() {
            Device device = new Device();
            device.setDeviceId(TEST_ID);

            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(device);

            Device result = registry.getByDeviceId(TEST_ID);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should throw DeviceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> registry.getByDeviceId("nonexistent"))
                    .isInstanceOf(DeviceNotFoundException.class)
                    .hasMessageContaining("nonexistent");
        }
    }

    // ──────────────────── 列表 ────────────────────

    @Nested
    @DisplayName("list()")
    class ListDevices {

        @Test
        @DisplayName("Should return filtered results")
        void shouldReturnFilteredResults() {
            Device device = new Device();
            device.setDeviceId(TEST_ID);
            device.setDeviceName(TEST_NAME);

            when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(device));

            List<Device> results = registry.list("测试", null, null, "ONLINE", null, null, null);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty list when no matches")
        void shouldReturnEmptyList() {
            when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<Device> results = registry.list(null, (List<Long>) null, null, null, null, null, null);

            assertThat(results).isEmpty();
        }
    }

    // ──────────────────── 更新 ────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("Should throw DeviceNotFoundException when device not found")
        void shouldThrowWhenNotFound() {
            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Device updateFields = new Device();
            updateFields.setDeviceName("新名称");

            assertThatThrownBy(() -> registry.update("nonexistent", updateFields))
                    .isInstanceOf(DeviceNotFoundException.class);
        }
    }

    // ──────────────────── 删除 ────────────────────

    @Nested
    @DisplayName("deleteByDeviceId()")
    class DeleteByDeviceId {

        @Test
        @DisplayName("Should soft-delete existing device")
        void shouldSoftDelete() {
            Device device = new Device();
            device.setId(1L);
            device.setDeviceId(TEST_ID);

            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(device);
            when(deviceMapper.deleteById(1L)).thenReturn(1);

            registry.deleteByDeviceId(TEST_ID);

            verify(deviceMapper).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw DeviceNotFoundException when device not found")
        void shouldThrowWhenNotFound() {
            when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> registry.deleteByDeviceId("nonexistent"))
                    .isInstanceOf(DeviceNotFoundException.class);
        }
    }

    // ──────────────────── 存在性检查 ────────────────────

    @Nested
    @DisplayName("existsByDeviceId()")
    class ExistsByDeviceId {

        @Test
        @DisplayName("Should return true when device exists")
        void shouldReturnTrue() {
            when(deviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThat(registry.existsByDeviceId(TEST_ID)).isTrue();
        }

        @Test
        @DisplayName("Should return false when device does not exist")
        void shouldReturnFalse() {
            when(deviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThat(registry.existsByDeviceId("nonexistent")).isFalse();
        }
    }
}
