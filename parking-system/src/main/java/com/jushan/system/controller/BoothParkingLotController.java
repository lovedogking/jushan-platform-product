package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.mapper.EmployeeParkingLotMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗亭端车场列表 Controller（Phase 2 D6）。
 * <p>
 * 返回当前岗亭管理员被授权访问的车场列表。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/booth/parking-lots")
public class BoothParkingLotController {

    private static final Logger log = LoggerFactory.getLogger(BoothParkingLotController.class);

    private final ParkingLotMapper parkingLotMapper;
    private final EmployeeParkingLotMapper employeeParkingLotMapper;

    public BoothParkingLotController(ParkingLotMapper parkingLotMapper,
                                      EmployeeParkingLotMapper employeeParkingLotMapper) {
        this.parkingLotMapper = parkingLotMapper;
        this.employeeParkingLotMapper = employeeParkingLotMapper;
    }

    /**
     * 获取当前岗亭管理员被授权访问的车场列表。
     * <p>
     * 返回 carousel：id、name、status（ENABLED/DISABLED）。
     * 仅返回启用状态的车场。
     */
    @GetMapping
    @RequirePermission("booth:operate")
    public R<List<Map<String, Object>>> listParkingLots() {
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx == null || ctx.userId() == null) {
            return R.fail(com.jushan.common.CommonErrorCode.UNAUTHORIZED.getCode(), "未登录");
        }

        // 查询当前用户在 employee_parking_lot 中的授权
        List<com.jushan.system.entity.EmployeeParkingLot> auths = employeeParkingLotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jushan.system.entity.EmployeeParkingLot>()
                        .eq(com.jushan.system.entity.EmployeeParkingLot::getEmployeeId, ctx.userId()));

        if (auths.isEmpty()) {
            log.warn("岗亭用户无授权车场: userId={}", ctx.userId());
            return R.ok(Collections.emptyList());
        }

        Set<Long> lotIds = auths.stream()
                .map(com.jushan.system.entity.EmployeeParkingLot::getParkingLotId)
                .collect(Collectors.toSet());

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
