package com.jushan.platform.modules.vehicle.controller;

import com.jushan.common.R;
import com.jushan.framework.config.DeviceAccessProperties;
import com.jushan.platform.modules.vehicle.dto.WhitelistSyncResponse;
import com.jushan.platform.modules.vehicle.service.WhitelistSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * 白名单同步内部 API（任务包 7-1）。
 * <p>
 * 供 Adapter 定时拉取全量白名单快照，用于现场降级场景。
 * 通过 Bearer Token 鉴权，不经过租户拦截器。
 * <p>
 * <strong>安全约束</strong>：仅 dev/test 环境或通过配置显式启用。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/internal/whitelist")
@Profile("dev")
public class InternalWhitelistController {

    private static final Logger log = LoggerFactory.getLogger(InternalWhitelistController.class);

    private final WhitelistSyncService whitelistSyncService;
    private final DeviceAccessProperties props;

    public InternalWhitelistController(WhitelistSyncService whitelistSyncService,
                                        DeviceAccessProperties props) {
        this.whitelistSyncService = whitelistSyncService;
        this.props = props;
    }

    /**
     * 全量白名单同步。
     * <p>
     * GET /api/v1/internal/whitelist/sync?parkingLotId=1
     * Authorization: Bearer <api-key>
     *
     * @param parkingLotId 停车场 ID
     * @param authHeader   Authorization 请求头
     * @return 白名单全量快照
     */
    @GetMapping("/sync")
    public R<WhitelistSyncResponse> sync(@RequestParam Long parkingLotId,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // API Key 鉴权
        String expectedApiKey = props.getWhitelistSyncApiKey();
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            log.warn("白名单同步 API Key 未配置，拒绝请求: parkingLotId={}", parkingLotId);
            return R.fail(401, "Whitelist sync API key not configured");
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!expectedApiKey.equals(token)) {
            log.warn("白名单同步 API Key 不匹配: parkingLotId={}", parkingLotId);
            return R.fail(401, "Invalid API key");
        }

        log.info("白名单同步请求: parkingLotId={}", parkingLotId);
        WhitelistSyncResponse response = whitelistSyncService.generateSyncData(parkingLotId);
        return R.ok(response);
    }
}
