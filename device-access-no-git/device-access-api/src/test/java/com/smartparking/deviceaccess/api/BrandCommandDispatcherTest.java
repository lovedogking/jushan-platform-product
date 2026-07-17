
package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.adapter.xinlutong.XinlutongMessageHandler;
import com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler;
import com.smartparking.deviceaccess.api.dto.*;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * BrandCommandDispatcher 单元测试。
 * <p>
 * 验证按品牌路由到具体 Coordinator/Handler 的逻辑。
 * v0.4 新增。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BrandCommandDispatcher Tests")
class BrandCommandDispatcherTest {

    @Mock
    private ZhenshiDeviceCoordinator zhenshiCoordinator;

    @Mock
    private XinlutongDeviceCoordinator xinlutongCoordinator;

    @Mock
    private ZhenshiMessageHandler zhenshiHandler;

    @Mock
    private XinlutongMessageHandler xinlutongHandler;

    @InjectMocks
    private BrandCommandDispatcher dispatcher;

    private static DeviceProduct product(String brand) {
        DeviceProduct p = new DeviceProduct();
        p.setBrand(brand);
        p.setModel("TEST");
        return p;
    }

    @Test
    @DisplayName("syncTime: ZHENSHI -> zhenshiCoordinator")
    void syncTimeRoutesToZhenshi() {
        CommandResultDTO expected = CommandResultDTO.builder().success(true).build();
        when(zhenshiCoordinator.syncTime("dev-001")).thenReturn(expected);

        CommandResultDTO result = dispatcher.syncTime("dev-001", product("ZHENSHI"));

        assertThat(result).isEqualTo(expected);
        verify(zhenshiCoordinator).syncTime("dev-001");
        verifyNoInteractions(xinlutongCoordinator);
    }

    @Test
    @DisplayName("syncTime: 信路通 -> xinlutongCoordinator")
    void syncTimeRoutesToXinlutong() {
        CommandResultDTO expected = CommandResultDTO.builder().success(true).build();
        when(xinlutongCoordinator.syncTime("dev-001")).thenReturn(expected);

        CommandResultDTO result = dispatcher.syncTime("dev-001", product("信路通"));

        assertThat(result).isEqualTo(expected);
        verify(xinlutongCoordinator).syncTime("dev-001");
        verifyNoInteractions(zhenshiCoordinator);
    }

    @Test
    @DisplayName("openGate: 信路通 -> xinlutongCoordinator")
    void openGateRoutesToXinlutong() {
        CommandResultDTO expected = CommandResultDTO.builder().success(true).message("Gate opened").build();
        when(xinlutongCoordinator.openGate("dev-001")).thenReturn(expected);

        CommandResultDTO result = dispatcher.openGate("dev-001", product("信路通"));

        assertThat(result).isEqualTo(expected);
        verify(xinlutongCoordinator).openGate("dev-001");
    }

    @Test
    @DisplayName("openGate: ZHENSHI -> zhenshiCoordinator")
    void openGateRoutesToZhenshi() {
        CommandResultDTO expected = CommandResultDTO.builder().success(true).message("Gate opened").build();
        when(zhenshiCoordinator.openGate("dev-001")).thenReturn(expected);

        CommandResultDTO result = dispatcher.openGate("dev-001", product("ZHENSHI"));

        assertThat(result).isEqualTo(expected);
        verify(zhenshiCoordinator).openGate("dev-001");
    }

    @Test
    @DisplayName("openGate: unknown brand throws UnsupportedOperationException")
    void openGateRejectsUnknown() {
        assertThatThrownBy(() -> dispatcher.openGate("dev-001", product("UNKNOWN")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Gate control not supported");
        verifyNoInteractions(xinlutongCoordinator);
    }

    @Test
    @DisplayName("closeGate: 信路通 -> xinlutongCoordinator")
    void closeGateRoutesToXinlutong() {
        CommandResultDTO expected = CommandResultDTO.builder().success(true).message("Gate closed").build();
        when(xinlutongCoordinator.closeGate("dev-001")).thenReturn(expected);

        CommandResultDTO result = dispatcher.closeGate("dev-001", product("信路通"));

        assertThat(result).isEqualTo(expected);
        verify(xinlutongCoordinator).closeGate("dev-001");
    }

    @Test
    @DisplayName("closeGate: ZHENSHI -> zhenshiCoordinator")
    void closeGateRoutesToZhenshi() {
        CommandResultDTO expected = CommandResultDTO.builder().success(true).message("Gate closed").build();
        when(zhenshiCoordinator.closeGate("dev-001")).thenReturn(expected);

        CommandResultDTO result = dispatcher.closeGate("dev-001", product("ZHENSHI"));

        assertThat(result).isEqualTo(expected);
        verify(zhenshiCoordinator).closeGate("dev-001");
    }

    @Test
    @DisplayName("isDeviceOnline: ZHENSHI -> zhenshiHandler")
    void isDeviceOnlineRoutesToZhenshi() {
        when(zhenshiHandler.isDeviceOnline("dev-001")).thenReturn(true);

        assertThat(dispatcher.isDeviceOnline("dev-001", product("ZHENSHI"))).isTrue();
        verify(zhenshiHandler).isDeviceOnline("dev-001");
        verifyNoInteractions(xinlutongHandler);
    }

    @Test
    @DisplayName("isDeviceOnline: 信路通 -> xinlutongHandler")
    void isDeviceOnlineRoutesToXinlutong() {
        when(xinlutongHandler.isDeviceOnline("dev-001")).thenReturn(false);

        assertThat(dispatcher.isDeviceOnline("dev-001", product("信路通"))).isFalse();
        verify(xinlutongHandler).isDeviceOnline("dev-001");
        verifyNoInteractions(zhenshiHandler);
    }

    @Test
    @DisplayName("displayText: ZHENSHI -> zhenshiCoordinator")
    void displayTextRoutesToZhenshi() {
        DisplayResult expected = DisplayResult.builder().success(true).build();
        when(zhenshiCoordinator.displayText("dev-001", "hello", DisplayDirection.HORIZONTAL))
                .thenReturn(expected);

        DisplayResult result = dispatcher.displayText("dev-001", product("ZHENSHI"),
                "hello", DisplayDirection.HORIZONTAL);

        assertThat(result).isEqualTo(expected);
        verify(zhenshiCoordinator).displayText("dev-001", "hello", DisplayDirection.HORIZONTAL);
    }

    @Test
    @DisplayName("displayText: 信路通 -> xinlutongCoordinator")
    void displayTextRoutesToXinlutong() {
        DisplayResult expected = DisplayResult.builder().success(true).build();
        when(xinlutongCoordinator.displayText("dev-001", "hello", DisplayDirection.HORIZONTAL))
                .thenReturn(expected);

        DisplayResult result = dispatcher.displayText("dev-001", product("信路通"),
                "hello", DisplayDirection.HORIZONTAL);

        assertThat(result).isEqualTo(expected);
        verify(xinlutongCoordinator).displayText("dev-001", "hello", DisplayDirection.HORIZONTAL);
    }

    @Test
    @DisplayName("saveDisplay: ZHENSHI -> zhenshiCoordinator")
    void saveDisplayRoutesToZhenshi() {
        DisplayResult expected = DisplayResult.builder().success(true).build();
        when(zhenshiCoordinator.saveDisplay("dev-001", "hello", DisplayDirection.HORIZONTAL))
                .thenReturn(expected);

        DisplayResult result = dispatcher.saveDisplay("dev-001", product("ZHENSHI"),
                "hello", DisplayDirection.HORIZONTAL);

        assertThat(result).isEqualTo(expected);
        verify(zhenshiCoordinator).saveDisplay("dev-001", "hello", DisplayDirection.HORIZONTAL);
    }

    @Test
    @DisplayName("saveDisplay: 信路通 -> xinlutongCoordinator")
    void saveDisplayRoutesToXinlutong() {
        DisplayResult expected = DisplayResult.builder().success(true).build();
        when(xinlutongCoordinator.saveDisplay("dev-001", "hello", DisplayDirection.HORIZONTAL))
                .thenReturn(expected);

        DisplayResult result = dispatcher.saveDisplay("dev-001", product("信路通"),
                "hello", DisplayDirection.HORIZONTAL);

        assertThat(result).isEqualTo(expected);
        verify(xinlutongCoordinator).saveDisplay("dev-001", "hello", DisplayDirection.HORIZONTAL);
    }

    @Test
    @DisplayName("controlPeripheral: ZHENSHI -> zhenshiCoordinator")
    void controlPeripheralRoutesToZhenshi() {
        PeripheralControlRequest req = new PeripheralControlRequest();
        req.setAction("ENABLE");
        PeripheralControlResult expected = PeripheralControlResult.builder()
                .success(true).action("ENABLE").message("Display enabled").build();
        when(zhenshiCoordinator.controlPeripheral("dev-001", req)).thenReturn(expected);

        PeripheralControlResult result = dispatcher.controlPeripheral("dev-001", product("ZHENSHI"), req);

        assertThat(result).isEqualTo(expected);
        verify(zhenshiCoordinator).controlPeripheral("dev-001", req);
    }

    @Test
    @DisplayName("controlPeripheral: 信路通 -> xinlutongCoordinator")
    void controlPeripheralRoutesToXinlutong() {
        PeripheralControlRequest req = new PeripheralControlRequest();
        req.setAction("ENABLE");
        PeripheralControlResult expected = PeripheralControlResult.builder()
                .success(true).action("ENABLE").message("Display enabled").build();
        when(xinlutongCoordinator.controlPeripheral("dev-001", req)).thenReturn(expected);

        PeripheralControlResult result = dispatcher.controlPeripheral("dev-001", product("信路通"), req);

        assertThat(result).isEqualTo(expected);
        verify(xinlutongCoordinator).controlPeripheral("dev-001", req);
    }

    @Test
    @DisplayName("unknown brand returns false for isDeviceOnline and throws for commands")
    void unknownBrand() {
        assertThat(dispatcher.isDeviceOnline("dev-001", product("UNKNOWN"))).isFalse();

        assertThatThrownBy(() -> dispatcher.syncTime("dev-001", product("UNKNOWN")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
