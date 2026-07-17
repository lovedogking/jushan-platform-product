package com.smartparking.deviceaccess.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.entity.DeviceProductMapper;
import com.smartparking.deviceaccess.common.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DeviceProductRegistry 单元测试。
 * <p>
 * v0.3 新增。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceProductRegistry Unit Tests")
class DeviceProductRegistryTest {

    @Mock
    private DeviceProductMapper productMapper;

    private DeviceProductRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DeviceProductRegistry(productMapper);
    }

    private DeviceProduct createProduct(Long id, String brand, String model, String deviceType, String protocol) {
        DeviceProduct p = new DeviceProduct();
        p.setId(id);
        p.setBrand(brand);
        p.setModel(model);
        p.setProductName(brand + " " + model);
        p.setDeviceType(deviceType);
        p.setProtocol(protocol);
        return p;
    }

    @Nested
    @DisplayName("listAll()")
    class ListAll {

        @Test
        @DisplayName("Should return all products")
        void shouldReturnAllProducts() {
            DeviceProduct p1 = createProduct(1L, "ZHENSHI", "C5H", "CAMERA", "MQTT");
            DeviceProduct p2 = createProduct(2L, "臻识", "C6H", "CAMERA", "MQTT");
            when(productMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(p1, p2));

            List<DeviceProduct> result = registry.listAll();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getBrand()).isEqualTo("ZHENSHI");
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Should return product when found")
        void shouldReturnProduct() {
            DeviceProduct p = createProduct(1L, "ZHENSHI", "C5H", "CAMERA", "MQTT");
            when(productMapper.selectById(1L)).thenReturn(p);

            DeviceProduct result = registry.getById(1L);

            assertThat(result.getBrand()).isEqualTo("ZHENSHI");
            assertThat(result.getDeviceType()).isEqualTo("CAMERA");
        }

        @Test
        @DisplayName("Should throw ProductNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(productMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> registry.getById(99L))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("listByDeviceType()")
    class ListByDeviceType {

        @Test
        @DisplayName("Should return CAMERA products only")
        void shouldReturnCameraProducts() {
            DeviceProduct camera = createProduct(1L, "ZHENSHI", "C5H", "CAMERA", "MQTT");
            when(productMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(camera));

            List<DeviceProduct> result = registry.listByDeviceType("CAMERA");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDeviceType()).isEqualTo("CAMERA");
        }
    }

    @Nested
    @DisplayName("mapByIds()")
    class MapByIds {

        @Test
        @DisplayName("Should return empty map for null input")
        void shouldReturnEmptyMapForNull() {
            Map<Long, DeviceProduct> result = registry.mapByIds(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty map for empty list")
        void shouldReturnEmptyMapForEmpty() {
            Map<Long, DeviceProduct> result = registry.mapByIds(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return map keyed by product ID")
        void shouldReturnMap() {
            DeviceProduct p1 = createProduct(1L, "ZHENSHI", "C5H", "CAMERA", "MQTT");
            DeviceProduct p2 = createProduct(4L, "通用", "LED-01", "DISPLAY", "RS485");
            when(productMapper.selectBatchIds(List.of(1L, 4L))).thenReturn(List.of(p1, p2));

            Map<Long, DeviceProduct> result = registry.mapByIds(List.of(1L, 4L));

            assertThat(result).hasSize(2);
            assertThat(result.get(1L).getDeviceType()).isEqualTo("CAMERA");
            assertThat(result.get(4L).getDeviceType()).isEqualTo("DISPLAY");
        }
    }
}
