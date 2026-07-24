package com.jushan.platform.modules.miniapp.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序停车场信息控制器（Phase 3 E2）。
 * <p>
 * 提供停车场列表查询、附近车场余位查询能力。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mini/parking-lots")
public class MiniParkingLotController {

    /** 地球半径（米） */
    private static final double EARTH_RADIUS = 6371000.0;

    private final ParkingLotMapper parkingLotMapper;

    public MiniParkingLotController(ParkingLotMapper parkingLotMapper) {
        this.parkingLotMapper = parkingLotMapper;
    }

    /**
     * 获取全部车场列表（未授权定位时使用）。
     * <p>
     * 返回所有启用状态的车场，包含余位信息。
     */
    @GetMapping
    @RequirePermission("miniapp:view")
    public R<List<Map<String, Object>>> listParkingLots() {
        List<ParkingLot> lots = parkingLotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingLot>()
                        .eq(ParkingLot::getStatus, "ENABLED")
                        .orderByAsc(ParkingLot::getId));

        List<Map<String, Object>> result = lots.stream().map(this::toLotMap).collect(Collectors.toList());
        return R.ok(result);
    }

    /**
     * 获取附近车场余位（需授权定位）。
     * <p>
     * 根据经纬度和搜索半径查询附近已启用的车场。
     * 距离使用 Haversine 公式计算。
     *
     * @param latitude  当前纬度
     * @param longitude 当前经度
     * @param radius    搜索半径（米），默认 5000 米
     * @return 附近车场列表（含余位和距离），按距离升序排列
     */
    @GetMapping("/nearby")
    @RequirePermission("miniapp:view")
    public R<List<Map<String, Object>>> listNearbyParkingLots(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(defaultValue = "5000") int radius) {

        if (latitude == null || longitude == null) {
            return R.fail(4002, "经纬度不能为空");
        }

        // 限定半径范围：最小 500 米，最大 50000 米
        if (radius < 500) radius = 500;
        if (radius > 50000) radius = 50000;

        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();

        // 查询所有启用状态且有经纬度的车场
        List<ParkingLot> allLots = parkingLotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingLot>()
                        .eq(ParkingLot::getStatus, "ENABLED")
                        .isNotNull(ParkingLot::getLatitude)
                        .isNotNull(ParkingLot::getLongitude));

        // 计算距离并过滤
        List<Map<String, Object>> nearby = new ArrayList<>();
        for (ParkingLot lot : allLots) {
            if (lot.getLatitude() == null || lot.getLongitude() == null) {
                continue;
            }
            double distance = haversine(lat, lng,
                    lot.getLatitude().doubleValue(), lot.getLongitude().doubleValue());
            if (distance <= radius) {
                Map<String, Object> map = toLotMap(lot);
                map.put("distance", (int) Math.round(distance));
                nearby.add(map);
            }
        }

        // 按距离升序排列
        nearby.sort(Comparator.comparingInt(m -> (Integer) m.get("distance")));

        log.debug("附近车场查询: lat={} lng={} radius={} found={}", latitude, longitude, radius, nearby.size());
        return R.ok(nearby);
    }

    /**
     * 将 ParkingLot 实体转换为前端展示的 Map。
     */
    private Map<String, Object> toLotMap(ParkingLot lot) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", lot.getId());
        map.put("name", lot.getName());
        map.put("address", lot.getAddress() != null ? lot.getAddress() : "");
        map.put("totalSpaces", lot.getTotalSpaces() != null ? lot.getTotalSpaces() : 0);
        map.put("remainingSpaces", lot.getRemainingSpaces() != null ? lot.getRemainingSpaces() : 0);
        map.put("currentVehicles", lot.getCurrentVehicles() != null ? lot.getCurrentVehicles() : 0);
        map.put("longitude", lot.getLongitude() != null ? lot.getLongitude().doubleValue() : null);
        map.put("latitude", lot.getLatitude() != null ? lot.getLatitude().doubleValue() : null);
        return map;
    }

    /**
     * Haversine 公式计算两点间距离（米）。
     */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
