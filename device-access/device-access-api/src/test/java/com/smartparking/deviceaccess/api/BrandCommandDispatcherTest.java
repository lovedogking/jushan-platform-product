package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.api.dto.CommandResultDTO;
import com.smartparking.deviceaccess.api.dto.DisplayResult;
import com.smartparking.deviceaccess.api.dto.LockGateRequest;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * BrandCommandDispatcher 单元测试。
 * <p>
 * 验证按品牌路由到具体 DeviceCoordinator 的逻辑（含品牌别名映射）。
 * v0.4 新增；v0.5 重写以匹配异步接口并补充芊熠品牌用例。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BrandCommandDispatcher Tests")
class BrandCommandDispatcherTest {

    @Mock
    private ZhenshiDeviceCoordinator zhenshiCoordinator;

    @Mock
    private XinlutongDeviceCoordinator xinlutongCoordinator;

    @Mock
    private QianyiDeviceCoordinator qianyiCoordinator;

    private BrandCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(zhenshiCoordinator.getBrand()).thenReturn("ZHENSHI");
        lenient().when(xinlutongCoordinator.getBrand()).thenReturn("XINLUTONG");
        lenient().when(qianyiCoordinator.getBrand()).thenReturn("QIANYI");
        dispatcher = new BrandCommandDispatcher(
                List.of(zhenshiCoordinator, xinlutongCoordinator, qianyiCoordinator));
        // 构造函数会调用各 coordinator 的 getBrand() 收集路由表，清除这些调用记录以便后续 verifyNoInteractions
        clearInvocations(zhenshiCoordinator, xinlutongCoordinator, qianyiCoordinator);
    }

    private static DeviceProduct product(String brand) {
        DeviceProduct p = new DeviceProduct();
        p.setBrand(brand);
        p.setModel("TEST");
        return p;
    }

    // ──────────────────── 既有品牌路由 ────────────────────

    @Test
    @DisplayName("openGate: ZHENSHI -> zhenshiCoordinator")
    void openGateRoutesToZhenshi() {
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(zhenshiCoordinator.openGate("dev-001")).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.openGate("dev-001", product("ZHENSHI"));

        assertThat(result).isSameAs(expected);
        verify(zhenshiCoordinator).openGate("dev-001");
        verifyNoInteractions(xinlutongCoordinator, qianyiCoordinator);
    }

    @Test
    @DisplayName("openGate: 信路通 -> xinlutongCoordinator")
    void openGateRoutesToXinlutong() {
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(xinlutongCoordinator.openGate("dev-001")).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.openGate("dev-001", product("信路通"));

        assertThat(result).isSameAs(expected);
        verify(xinlutongCoordinator).openGate("dev-001");
        verifyNoInteractions(zhenshiCoordinator, qianyiCoordinator);
    }

    // ──────────────────── 芊熠品牌路由（v0.5） ────────────────────

    @Test
    @DisplayName("openGate: 芊熠 -> qianyiCoordinator")
    void openGateRoutesToQianyi() {
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(qianyiCoordinator.openGate("dev-001")).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.openGate("dev-001", product("芊熠"));

        assertThat(result).isSameAs(expected);
        verify(qianyiCoordinator).openGate("dev-001");
        verifyNoInteractions(zhenshiCoordinator, xinlutongCoordinator);
    }

    @Test
    @DisplayName("openGate: QIANYI（英文别名）-> qianyiCoordinator")
    void openGateRoutesToQianyiEnglishAlias() {
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(qianyiCoordinator.openGate("dev-001")).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.openGate("dev-001", product("QIANYI"));

        assertThat(result).isSameAs(expected);
        verify(qianyiCoordinator).openGate("dev-001");
    }

    @Test
    @DisplayName("closeGate: 芊熠 -> qianyiCoordinator")
    void closeGateRoutesToQianyi() {
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(qianyiCoordinator.closeGate("dev-001")).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.closeGate("dev-001", product("芊熠"));

        assertThat(result).isSameAs(expected);
        verify(qianyiCoordinator).closeGate("dev-001");
    }

    @Test
    @DisplayName("lockGate: 芊熠 -> qianyiCoordinator")
    void lockGateRoutesToQianyi() {
        LockGateRequest req = new LockGateRequest();
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(qianyiCoordinator.lockGate("dev-001", req)).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.lockGate("dev-001", product("芊熠"), req);

        assertThat(result).isSameAs(expected);
        verify(qianyiCoordinator).lockGate("dev-001", req);
    }

    @Test
    @DisplayName("syncTime: 芊熠 -> qianyiCoordinator")
    void syncTimeRoutesToQianyi() {
        CompletableFuture<CommandResultDTO> expected =
                CompletableFuture.completedFuture(CommandResultDTO.builder().success(true).build());
        when(qianyiCoordinator.syncTime("dev-001")).thenReturn(expected);

        CompletableFuture<CommandResultDTO> result = dispatcher.syncTime("dev-001", product("芊熠"));

        assertThat(result).isSameAs(expected);
        verify(qianyiCoordinator).syncTime("dev-001");
    }

    @Test
    @DisplayName("displayText: 芊熠 -> qianyiCoordinator")
    void displayTextRoutesToQianyi() {
        CompletableFuture<DisplayResult> expected =
                CompletableFuture.completedFuture(DisplayResult.builder().success(true).build());
        when(qianyiCoordinator.displayText("dev-001", "hello", DisplayDirection.HORIZONTAL))
                .thenReturn(expected);

        CompletableFuture<DisplayResult> result = dispatcher.displayText("dev-001", product("芊熠"),
                "hello", DisplayDirection.HORIZONTAL);

        assertThat(result).isSameAs(expected);
        verify(qianyiCoordinator).displayText("dev-001", "hello", DisplayDirection.HORIZONTAL);
    }

    @Test
    @DisplayName("isDeviceOnline: 芊熠 -> qianyiCoordinator")
    void isDeviceOnlineRoutesToQianyi() {
        when(qianyiCoordinator.isDeviceOnline("dev-001")).thenReturn(true);

        assertThat(dispatcher.isDeviceOnline("dev-001", product("芊熠"))).isTrue();
        verify(qianyiCoordinator).isDeviceOnline("dev-001");
        verifyNoInteractions(zhenshiCoordinator, xinlutongCoordinator);
    }

    // ──────────────────── 未知品牌 ────────────────────

    @Test
    @DisplayName("unknown brand throws UnsupportedOperationException")
    void unknownBrandThrows() {
        assertThatThrownBy(() -> dispatcher.openGate("dev-001", product("UNKNOWN")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unknown brand");

        assertThatThrownBy(() -> dispatcher.isDeviceOnline("dev-001", product("UNKNOWN")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unknown brand");
    }
}
