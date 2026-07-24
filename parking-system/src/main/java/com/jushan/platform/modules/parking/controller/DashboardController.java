package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.mapper.DashboardMapper;
import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;
import com.jushan.platform.modules.parking.vo.DashboardVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 仪表盘数据汇总 Controller（Phase 2 D4）。
 * <p>
 * 提供运营平台首页仪表盘的核心指标数据和趋势图表数据。
 * <p>
 * 数据隔离：基于 {@link ParkingLotScopeResolver} 统一数据范围，
 * 租户管理员仅能看到被授权车场的数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardMapper dashboardMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public DashboardController(DashboardMapper dashboardMapper,
                                ParkingLotScopeResolver scopeResolver) {
        this.dashboardMapper = dashboardMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 获取仪表盘首页数据。
     * <p>
     * 返回所有核心指标和趋势数据。
     * 根据当前用户的数据范围自动筛选。
     */
    @GetMapping
    @RequirePermission("dashboard:view")
    public R<DashboardVO> getDashboard() {
        DashboardVO vo = new DashboardVO();

        // 判断数据范围
        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        boolean hasFullAccess = (authorizedLotIds == null);

        if (hasFullAccess) {
            // 全量访问 — 无停车场过滤
            fillFullAccessData(vo);
        } else if (authorizedLotIds.isEmpty()) {
            // 无授权停车场 — 返回全零
            fillEmptyData(vo);
        } else {
            // 有限访问 — 按授权停车场过滤
            fillRestrictedData(vo, authorizedLotIds);
        }

        return R.ok(vo);
    }

    // ==================== 全量访问 ====================

    private void fillFullAccessData(DashboardVO vo) {
        // 今日收入
        vo.setTodayRevenue(defaultZero(dashboardMapper.sumTodayRevenue()));

        // 今日车流量
        Integer entryCount = defaultZero(dashboardMapper.countTodayEntry());
        Integer exitCount = defaultZero(dashboardMapper.countTodayExit());
        vo.setTodayTraffic(new DashboardVO.TodayTraffic(entryCount, exitCount, entryCount + exitCount));

        // 在场车辆
        vo.setCurrentParkedCount(defaultZero(dashboardMapper.countParking()));

        // 余位
        vo.setRemainingSpaces(defaultZero(dashboardMapper.sumRemainingSpaces()));

        // 设备状态
        Integer totalDevices = defaultZero(dashboardMapper.countEnabledDevices());
        Integer onlineDevices = defaultZero(dashboardMapper.countOnlineDevices());
        vo.setDeviceStatus(new DashboardVO.DeviceStatusSummary(
                onlineDevices, totalDevices - onlineDevices, totalDevices));

        // 未处理异常
        vo.setUnhandledAlertCount(defaultZero(dashboardMapper.countUnhandledAlerts()));

        // 趋势数据
        vo.setHourlyRevenue(buildHourlyRevenue(dashboardMapper.selectHourlyRevenue()));
        vo.setHourlyTraffic(buildHourlyTraffic(
                dashboardMapper.selectHourlyEntry(),
                dashboardMapper.selectHourlyExit()));
    }

    // ==================== 有限范围 ====================

    private void fillRestrictedData(DashboardVO vo, Set<Long> lotIds) {
        List<Long> lotIdList = new ArrayList<>(lotIds);

        // 今日收入
        vo.setTodayRevenue(defaultZero(dashboardMapper.sumTodayRevenueByLots(lotIdList)));

        // 今日车流量
        Integer entryCount = defaultZero(dashboardMapper.countTodayEntryByLots(lotIdList));
        Integer exitCount = defaultZero(dashboardMapper.countTodayExitByLots(lotIdList));
        vo.setTodayTraffic(new DashboardVO.TodayTraffic(entryCount, exitCount, entryCount + exitCount));

        // 在场车辆
        vo.setCurrentParkedCount(defaultZero(dashboardMapper.countParkingByLots(lotIdList)));

        // 余位
        vo.setRemainingSpaces(defaultZero(dashboardMapper.sumRemainingSpacesByLots(lotIdList)));

        // 设备状态
        Integer totalDevices = defaultZero(dashboardMapper.countEnabledDevicesByLots(lotIdList));
        Integer onlineDevices = defaultZero(dashboardMapper.countOnlineDevicesByLots(lotIdList));
        vo.setDeviceStatus(new DashboardVO.DeviceStatusSummary(
                onlineDevices, totalDevices - onlineDevices, totalDevices));

        // 未处理异常
        vo.setUnhandledAlertCount(defaultZero(dashboardMapper.countUnhandledAlertsByLots(lotIdList)));

        // 趋势数据
        vo.setHourlyRevenue(buildHourlyRevenue(dashboardMapper.selectHourlyRevenueByLots(lotIdList)));
        vo.setHourlyTraffic(buildHourlyTraffic(
                dashboardMapper.selectHourlyEntryByLots(lotIdList),
                dashboardMapper.selectHourlyExitByLots(lotIdList)));
    }

    // ==================== 空数据 ====================

    private void fillEmptyData(DashboardVO vo) {
        vo.setTodayRevenue(0);
        vo.setTodayTraffic(new DashboardVO.TodayTraffic(0, 0, 0));
        vo.setCurrentParkedCount(0);
        vo.setRemainingSpaces(0);
        vo.setDeviceStatus(new DashboardVO.DeviceStatusSummary(0, 0, 0));
        vo.setUnhandledAlertCount(0);

        // 填充 24 小时全零数据
        List<DashboardVO.HourlyStat> hourlyRevenue = IntStream.range(0, 24)
                .mapToObj(h -> new DashboardVO.HourlyStat(h, 0))
                .collect(Collectors.toList());
        vo.setHourlyRevenue(hourlyRevenue);

        List<DashboardVO.HourlyTrafficStat> hourlyTraffic = IntStream.range(0, 24)
                .mapToObj(h -> new DashboardVO.HourlyTrafficStat(h, 0, 0))
                .collect(Collectors.toList());
        vo.setHourlyTraffic(hourlyTraffic);
    }

    // ==================== 趋势数据组装 ====================

    /**
     * 构建 0-23 时完整收入趋势，缺失小时补 0。
     */
    private List<DashboardVO.HourlyStat> buildHourlyRevenue(List<Map<String, Object>> dbResult) {
        Map<Integer, Integer> hourMap = new HashMap<>();
        for (Map<String, Object> row : dbResult) {
            int hour = ((Number) row.get("hour")).intValue();
            int amount = ((Number) row.get("amount")).intValue();
            hourMap.put(hour, amount);
        }

        return IntStream.range(0, 24)
                .mapToObj(h -> new DashboardVO.HourlyStat(h, hourMap.getOrDefault(h, 0)))
                .collect(Collectors.toList());
    }

    /**
     * 构建 0-23 时完整车流量趋势，合并出入数据，缺失小时补 0。
     */
    private List<DashboardVO.HourlyTrafficStat> buildHourlyTraffic(
            List<Map<String, Object>> entryResult,
            List<Map<String, Object>> exitResult) {

        Map<Integer, Integer> entryMap = new HashMap<>();
        for (Map<String, Object> row : entryResult) {
            int hour = ((Number) row.get("hour")).intValue();
            int count = ((Number) row.get("entry_count")).intValue();
            entryMap.put(hour, count);
        }

        Map<Integer, Integer> exitMap = new HashMap<>();
        for (Map<String, Object> row : exitResult) {
            int hour = ((Number) row.get("hour")).intValue();
            int count = ((Number) row.get("exit_count")).intValue();
            exitMap.put(hour, count);
        }

        return IntStream.range(0, 24)
                .mapToObj(h -> new DashboardVO.HourlyTrafficStat(
                        h,
                        entryMap.getOrDefault(h, 0),
                        exitMap.getOrDefault(h, 0)))
                .collect(Collectors.toList());
    }

    // ==================== 工具方法 ====================

    private int defaultZero(Integer value) {
        return value != null ? value : 0;
    }
}
