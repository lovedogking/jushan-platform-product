package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * 内部开闸关闸测试控制器（仅联调/开发环境使用）。
 * <p>
 * <strong>已废弃</strong>：生产远程开闸请使用 {@code RemoteGateController}（Phase 1 Track B2）。
 * 该控制器硬编码 tenantId=1 和设备 SN，不符合多租户设计，仅供联调测试。
 *
 * @author Jushan Platform
 * @since 1.0.0
 * @deprecated 仅用于开发/联调环境。生产远程开闸使用 RemoteGateController（带 {@code @RequirePermission} 鉴权）。
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/internal/gate")
@Profile("dev")
public class InternalGateController {

    private static final Logger log = LoggerFactory.getLogger(InternalGateController.class);

    private final DeviceService deviceService;

    public InternalGateController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 开闸。
     * GET /api/v1/internal/gate/open/{deviceId}?reason=test
     */
    @GetMapping("/open/{deviceId}")
    public R<String> openGate(@PathVariable Long deviceId,
                              @RequestParam(defaultValue = "test") String reason) {
        log.info("内部开闸测试: deviceId={}, reason={}", deviceId, reason);
        TenantContext.Snapshot prev = TenantContext.get();
        try {
            TenantContext.set(new TenantContext.Snapshot(1L, 0L, "platform", null, null));
            deviceService.openGate(deviceId, reason);
            return R.ok("开闸命令已发送");
        } finally {
            if (prev != null) TenantContext.set(prev); else TenantContext.clear();
        }
    }

    /**
     * 关闸。
     * GET /api/v1/internal/gate/close/{deviceId}?reason=test
     */
    @GetMapping("/close/{deviceId}")
    public R<String> closeGate(@PathVariable Long deviceId,
                               @RequestParam(defaultValue = "test") String reason) {
        log.info("内部关闸测试: deviceId={}, reason={}", deviceId, reason);
        TenantContext.Snapshot prev = TenantContext.get();
        try {
            TenantContext.set(new TenantContext.Snapshot(1L, 0L, "platform", null, null));
            deviceService.closeGate(deviceId, reason);
            return R.ok("关闸命令已发送");
        } finally {
            if (prev != null) TenantContext.set(prev); else TenantContext.clear();
        }
    }

}
