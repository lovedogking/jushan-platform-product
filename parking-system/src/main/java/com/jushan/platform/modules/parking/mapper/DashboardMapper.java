package com.jushan.platform.modules.parking.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘数据查询 Mapper（Phase 2 D4）。
 * <p>
 * 提供聚合查询和 GROUP BY 查询，不绑定具体实体。
 * 所有查询均提供无筛选（全量）和按车场筛选两个版本。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface DashboardMapper {

    // ==================== 今日收入 ====================

    /**
     * 收入口径：排除 REFUNDED（任务包 1-2 / 6-1 共用）。
     */
    @Select("SELECT COALESCE(SUM(paid_amount), 0) FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND DATE(pay_time) = CURDATE() AND deleted_at IS NULL AND status != 'REFUNDED'")
    Integer sumTodayRevenue();

    /**
     * 收入口径：排除 REFUNDED（任务包 1-2 / 6-1 共用）。
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(paid_amount), 0) FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND DATE(pay_time) = CURDATE() AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer sumTodayRevenueByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 今日车流量（入场） ====================

    @Select("SELECT COUNT(*) FROM parking_record " +
            "WHERE DATE(entry_time) = CURDATE() AND deleted_at IS NULL")
    Integer countTodayEntry();

    @Select("<script>" +
            "SELECT COUNT(*) FROM parking_record " +
            "WHERE DATE(entry_time) = CURDATE() AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer countTodayEntryByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 今日车流量（出场） ====================

    @Select("SELECT COUNT(*) FROM parking_record " +
            "WHERE exit_time IS NOT NULL AND DATE(exit_time) = CURDATE() AND deleted_at IS NULL")
    Integer countTodayExit();

    @Select("<script>" +
            "SELECT COUNT(*) FROM parking_record " +
            "WHERE exit_time IS NOT NULL AND DATE(exit_time) = CURDATE() AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer countTodayExitByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 当前在场车辆 ====================

    @Select("SELECT COUNT(*) FROM parking_record WHERE status = 'PARKING' AND deleted_at IS NULL")
    Integer countParking();

    @Select("<script>" +
            "SELECT COUNT(*) FROM parking_record " +
            "WHERE status = 'PARKING' AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer countParkingByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 余位总数 ====================

    @Select("SELECT COALESCE(SUM(remaining_spaces), 0) FROM parking_lot WHERE deleted_at IS NULL")
    Integer sumRemainingSpaces();

    @Select("<script>" +
            "SELECT COALESCE(SUM(remaining_spaces), 0) FROM parking_lot " +
            "WHERE deleted_at IS NULL AND id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer sumRemainingSpacesByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 设备在线/离线 ====================

    @Select("SELECT COUNT(*) FROM device WHERE status = 'ENABLED' AND deleted_at IS NULL")
    Integer countEnabledDevices();

    @Select("<script>" +
            "SELECT COUNT(*) FROM device " +
            "WHERE status = 'ENABLED' AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer countEnabledDevicesByLots(@Param("lotIds") List<Long> lotIds);

    @Select("SELECT COUNT(DISTINCT d.id) FROM device d " +
            "INNER JOIN device_status_snapshot s1 ON s1.device_id = d.id " +
            "INNER JOIN (SELECT device_id, MAX(collected_at) max_time FROM device_status_snapshot GROUP BY device_id) s2 " +
            "ON s1.device_id = s2.device_id AND s1.collected_at = s2.max_time " +
            "WHERE s1.online = 1 AND d.status = 'ENABLED' AND d.deleted_at IS NULL")
    Integer countOnlineDevices();

    @Select("<script>" +
            "SELECT COUNT(DISTINCT d.id) FROM device d " +
            "INNER JOIN device_status_snapshot s1 ON s1.device_id = d.id " +
            "INNER JOIN (SELECT device_id, MAX(collected_at) max_time FROM device_status_snapshot GROUP BY device_id) s2 " +
            "ON s1.device_id = s2.device_id AND s1.collected_at = s2.max_time " +
            "WHERE s1.online = 1 AND d.status = 'ENABLED' AND d.deleted_at IS NULL " +
            "AND d.parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer countOnlineDevicesByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 未处理异常 ====================

    @Select("SELECT COUNT(*) FROM exception_record WHERE status = 'UNHANDLED' AND deleted_at IS NULL")
    Integer countUnhandledAlerts();

    @Select("<script>" +
            "SELECT COUNT(*) FROM exception_record " +
            "WHERE status = 'UNHANDLED' AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    Integer countUnhandledAlertsByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 按小时收入趋势 ====================

    /**
     * 收入口径：排除 REFUNDED（任务包 1-2 / 6-1 共用）。
     */
    @Select("SELECT HOUR(pay_time) AS hour, COALESCE(SUM(paid_amount), 0) AS amount " +
            "FROM parking_order WHERE pay_time IS NOT NULL AND DATE(pay_time) = CURDATE() AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "GROUP BY HOUR(pay_time) ORDER BY HOUR(pay_time)")
    List<Map<String, Object>> selectHourlyRevenue();

    /**
     * 收入口径：排除 REFUNDED（任务包 1-2 / 6-1 共用）。
     */
    @Select("<script>" +
            "SELECT HOUR(pay_time) AS hour, COALESCE(SUM(paid_amount), 0) AS amount " +
            "FROM parking_order WHERE pay_time IS NOT NULL AND DATE(pay_time) = CURDATE() AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY HOUR(pay_time) ORDER BY HOUR(pay_time)" +
            "</script>")
    List<Map<String, Object>> selectHourlyRevenueByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 按小时入场趋势 ====================

    @Select("SELECT HOUR(entry_time) AS hour, COUNT(*) AS entry_count " +
            "FROM parking_record WHERE DATE(entry_time) = CURDATE() AND deleted_at IS NULL " +
            "GROUP BY HOUR(entry_time) ORDER BY HOUR(entry_time)")
    List<Map<String, Object>> selectHourlyEntry();

    @Select("<script>" +
            "SELECT HOUR(entry_time) AS hour, COUNT(*) AS entry_count " +
            "FROM parking_record WHERE DATE(entry_time) = CURDATE() AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY HOUR(entry_time) ORDER BY HOUR(entry_time)" +
            "</script>")
    List<Map<String, Object>> selectHourlyEntryByLots(@Param("lotIds") List<Long> lotIds);

    // ==================== 按小时出场趋势 ====================

    @Select("SELECT HOUR(exit_time) AS hour, COUNT(*) AS exit_count " +
            "FROM parking_record WHERE exit_time IS NOT NULL AND DATE(exit_time) = CURDATE() AND deleted_at IS NULL " +
            "GROUP BY HOUR(exit_time) ORDER BY HOUR(exit_time)")
    List<Map<String, Object>> selectHourlyExit();

    @Select("<script>" +
            "SELECT HOUR(exit_time) AS hour, COUNT(*) AS exit_count " +
            "FROM parking_record WHERE exit_time IS NOT NULL AND DATE(exit_time) = CURDATE() AND deleted_at IS NULL " +
            "AND parking_lot_id IN " +
            "<foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY HOUR(exit_time) ORDER BY HOUR(exit_time)" +
            "</script>")
    List<Map<String, Object>> selectHourlyExitByLots(@Param("lotIds") List<Long> lotIds);
}
