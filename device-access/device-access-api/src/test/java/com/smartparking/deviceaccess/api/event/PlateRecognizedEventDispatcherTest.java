package com.smartparking.deviceaccess.api.event;

import com.smartparking.deviceaccess.api.image.ImageStorageService;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.event.DeviceEvent;
import com.smartparking.deviceaccess.event.EventPublisher;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlateRecognizedEventDispatcher Unit Tests")
class PlateRecognizedEventDispatcherTest {

    @Mock
    private DeviceRegistry deviceRegistry;

    @Mock
    private DeviceProductRegistry productRegistry;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private PlateRecognizedEventDispatcher dispatcher;

    @Test
    @DisplayName("Should skip when device not registered")
    void shouldSkipWhenDeviceNotRegistered() {
        String sn = "unregistered-sn";
        when(deviceRegistry.findByDeviceId(sn)).thenReturn(null);

        dispatcher.onPlateRecognized(new PlateRecognizedData(
                sn, "A12345", 98, 1, 2, "/img/full.jpg", null, 1234567890000L
        ));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should dispatch event with enriched business fields")
    void shouldDispatchWithBusinessFields() {
        String sn = "test-sn-001";

        Device device = new Device();
        device.setDeviceId(sn);
        device.setProductId(1L);
        device.setTenantId("tenant_001");
        device.setParkingLotId("park_001");
        device.setLaneId("lane_001");
        device.setPlatformDeviceId("dev_camera_001");

        DeviceProduct product = new DeviceProduct();
        product.setId(1L);
        product.setBrand("ZHENSHI");

        when(deviceRegistry.findByDeviceId(sn)).thenReturn(device);
        when(productRegistry.getById(1L)).thenReturn(product);

        dispatcher.onPlateRecognized(new PlateRecognizedData(
                sn, "A12345", 98, 1, 2, "/img/full.jpg", null, 1234567890000L
        ));

        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(eventPublisher).publish(captor.capture());

        DeviceEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("PLATE_RECOGNIZED");
        assertThat(event.getDeviceSn()).isEqualTo(sn);
        assertThat(event.getVendor()).isEqualTo("ZHENSHI");
        assertThat(event.getTenantId()).isEqualTo("tenant_001");
        assertThat(event.getParkingLotId()).isEqualTo("park_001");
        assertThat(event.getLaneId()).isEqualTo("lane_001");
        assertThat(event.getPlatformDeviceId()).isEqualTo("dev_camera_001");
        assertThat(event.getEventId()).startsWith("evt_");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getReceivedAt()).isNotNull();
        assertThat(event.getPayload().get("plateNo")).isEqualTo("A12345");
        assertThat(event.getPayload().get("confidence")).isEqualTo(98);
        assertThat(event.getPayload().get("direction")).isEqualTo(1);
        assertThat(event.getPayload().get("plateColor")).isEqualTo(2);
        assertThat(event.getPayload().get("imagePath")).isEqualTo("/img/full.jpg");
    }

    @Test
    @DisplayName("Should rewrite qianyi local paths to accessible URLs")
    void shouldRewriteQianyiImageUrls() {
        String sn = "qianyi-sn-001";

        Device device = new Device();
        device.setDeviceId(sn);
        device.setProductId(2L);

        DeviceProduct product = new DeviceProduct();
        product.setId(2L);
        product.setBrand("芊熠");

        when(deviceRegistry.findByDeviceId(sn)).thenReturn(device);
        when(productRegistry.getById(2L)).thenReturn(product);
        when(imageStorageService.buildFullImageUrl(sn, 1562566751L))
                .thenReturn("http://localhost:8082/images/20190708/qianyi-sn-001/1562566751.jpg");
        when(imageStorageService.buildPlateImageUrl(sn, 1562566751L))
                .thenReturn("http://localhost:8082/images/20190708/qianyi-sn-001/1562566751_plate.jpg");

        dispatcher.onPlateRecognized(new PlateRecognizedData(
                sn, "京A12345", 28, null, null,
                "/picture/A000001/0001aa00000d/20190708/14/x.jpg",
                "/picture/A000001/0001aa00000d/20190708/14/x_plate.jpg",
                1562566751_000L
        ));

        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(eventPublisher).publish(captor.capture());

        DeviceEvent event = captor.getValue();
        // 相机本地路径被替换为可访问 URL
        assertThat(event.getPayload().get("imagePath"))
                .isEqualTo("http://localhost:8082/images/20190708/qianyi-sn-001/1562566751.jpg");
        assertThat(event.getPayload().get("plateImagePath"))
                .isEqualTo("http://localhost:8082/images/20190708/qianyi-sn-001/1562566751_plate.jpg");
    }

    @Test
    @DisplayName("Should handle null product gracefully")
    void shouldHandleNullProduct() {
        String sn = "test-sn-002";

        Device device = new Device();
        device.setDeviceId(sn);
        device.setProductId(null); // no product

        when(deviceRegistry.findByDeviceId(sn)).thenReturn(device);

        dispatcher.onPlateRecognized(new PlateRecognizedData(
                sn, "B67890", null, null, null, null, null, null
        ));

        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(eventPublisher).publish(captor.capture());

        DeviceEvent event = captor.getValue();
        assertThat(event.getVendor()).isEqualTo("UNKNOWN");
        assertThat(event.getPayload().get("plateNo")).isEqualTo("B67890");
        // null fields should not be in payload
        assertThat(event.getPayload()).containsKey("plateNo");
        assertThat(event.getPayload()).doesNotContainKey("confidence");
        assertThat(event.getOccurredAt()).isNull();
    }

    @Test
    @DisplayName("Should not throw when registry throws exception")
    void shouldNotThrowWhenRegistryThrows() {
        String sn = "error-sn";
        when(deviceRegistry.findByDeviceId(sn))
                .thenThrow(new RuntimeException("DB error"));

        // should not throw
        dispatcher.onPlateRecognized(new PlateRecognizedData(
                sn, "C99999", 50, null, null, null, null, null
        ));

        verifyNoInteractions(eventPublisher);
    }
}
