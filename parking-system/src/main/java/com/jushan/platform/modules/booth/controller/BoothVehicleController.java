package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 岗亭端车辆查询控制器。
 * <p>
 * 提供在场车辆列表和历史通行记录查询。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/booth/vehicles")
public class BoothVehicleController {

    /**
     * 在场车辆查询（暂用空实现，Phase 5 对接真实数据）。
     */
    @GetMapping("/present")
    @RequirePermission("booth:view")
    public R<Map<String, Object>> presentVehicles(
            @RequestParam Long parkingLotId,
            @RequestParam(defaultValue = "entryTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0);
        result.put("size", size);
        result.put("current", page);
        return R.ok(result);
    }

    /**
     * 历史通行记录查询（暂用空实现，Phase 5 对接真实数据）。
     */
    @GetMapping("/history")
    @RequirePermission("booth:view")
    public R<Map<String, Object>> historyRecords(
            @RequestParam Long parkingLotId,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0);
        result.put("size", size);
        result.put("current", page);
        return R.ok(result);
    }
}
