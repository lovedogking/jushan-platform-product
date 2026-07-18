package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.service.ParkingLotScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗亭端车场列表 Controller。
 * <p>
 * 返回当前岗亭管理员被授权访问的车场列表（跨租户）。
 * 岗亭管理员的停车场授权通过 sys_admin_account_parking_lot 多对多表管理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/booth/parking-lots")
public class BoothParkingLotController {

    private static final Logger log = LoggerFactory.getLogger(BoothParkingLotController.class);

    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public BoothParkingLotController(ParkingLotMapper parkingLotMapper,
                                      ParkingLotScopeResolver scopeResolver) {
        this.parkingLotMapper = parkingLotMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 获取当前岗亭管理员被授权访问的车场列表（跨租户）。
     * <p>
     * 返回：id、name、status（ENABLED/DISABLED）。仅返回启用状态的车场。
     */
    @GetMapping
    @RequirePermission("booth:operate")
    public R<List<Map<String, Object>>> listParkingLots() {
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx == null || ctx.userId() == null) {
            return R.fail(com.jushan.common.CommonErrorCode.UNAUTHORIZED.getCode(), "未登录");
        }

        // 通过 ParkingLotScopeResolver 获取授权的停车场 ID（支持跨租户）
        Set<Long> lotIds = scopeResolver.resolveAuthorizedIds();

        if (lotIds == null || lotIds.isEmpty()) {
            log.warn("岗亭用户无授权车场: userId={}", ctx.userId());
            return R.ok(Collections.emptyList());
        }

        // 查询停车场信息，仅返回启用状态
        List<ParkingLot> lots = parkingLotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingLot>()
                        .in(ParkingLot::getId, lotIds)
                        .eq(ParkingLot::getStatus, "ENABLED"));

        List<Map<String, Object>> result = lots.stream().map(lot -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", lot.getId());
            item.put("name", lot.getName());
            item.put("status", lot.getStatus());
            return item;
        }).collect(Collectors.toList());

        return R.ok(result);
    }
}
