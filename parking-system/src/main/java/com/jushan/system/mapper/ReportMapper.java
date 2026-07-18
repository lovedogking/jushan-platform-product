package com.jushan.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表数据查询 Mapper（包 6-1）。
 * <p>
 * 收入报表 / 车流量报表的聚合查询。
 * 收入口径：paid_amount 求和，排除 REFUNDED 状态。
 * <p>
 * 所有查询支持按车场过滤（授权数据范围）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ReportMapper {

    // ==================== 收入报表 ====================

    /**
     * 指定时间段总收入（分），排除已退款订单。
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>" +
            "</script>")
    Map<String, Object> sumRevenue(@Param("startTime") String startTime,
                                   @Param("endTime") String endTime,
                                   @Param("lotIds") Set<Long> lotIds);

    /**
     * 按天汇总收入（用于日视图/Daily视图）。
     */
    @Select("<script>" +
            "SELECT DATE(pay_time) AS period, " +
            "       COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY DATE(pay_time) ORDER BY period" +
            "</script>")
    List<Map<String, Object>> revenueByDay(@Param("startTime") String startTime,
                                           @Param("endTime") String endTime,
                                           @Param("lotIds") Set<Long> lotIds);

    /**
     * 按月汇总收入（用于月视图/Monthly视图）。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(pay_time, '%Y-%m') AS period, " +
            "       COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY DATE_FORMAT(pay_time, '%Y-%m') ORDER BY period" +
            "</script>")
    List<Map<String, Object>> revenueByMonth(@Param("startTime") String startTime,
                                             @Param("endTime") String endTime,
                                             @Param("lotIds") Set<Long> lotIds);

    /**
     * 按年汇总收入（用于年视图/Yearly视图）。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(pay_time, '%Y') AS period, " +
            "       COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY DATE_FORMAT(pay_time, '%Y') ORDER BY period" +
            "</script>")
    List<Map<String, Object>> revenueByYear(@Param("startTime") String startTime,
                                            @Param("endTime") String endTime,
                                            @Param("lotIds") Set<Long> lotIds);

    // ==================== 车流量报表 ====================

    /**
     * 按天统计入场/出场量。
     */
    @Select("<script>" +
            "SELECT d.period, COALESCE(e.entry_count, 0) AS entry_count, COALESCE(x.exit_count, 0) AS exit_count " +
            "FROM (SELECT DISTINCT DATE(entry_time) AS period FROM parking_record " +
            "      WHERE entry_time >= #{startTime} AND entry_time &lt; #{endTime} AND deleted_at IS NULL " +
            "      <if test='lotIds != null and lotIds.size() > 0'>" +
            "        AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "      </if>) d " +
            "LEFT JOIN (SELECT DATE(entry_time) AS period, COUNT(*) AS entry_count FROM parking_record " +
            "           WHERE entry_time >= #{startTime} AND entry_time &lt; #{endTime} AND deleted_at IS NULL " +
            "           <if test='lotIds != null and lotIds.size() > 0'>" +
            "             AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "           </if> " +
            "           GROUP BY DATE(entry_time)) e ON d.period = e.period " +
            "LEFT JOIN (SELECT DATE(exit_time) AS period, COUNT(*) AS exit_count FROM parking_record " +
            "           WHERE exit_time IS NOT NULL AND exit_time >= #{startTime} AND exit_time &lt; #{endTime} AND deleted_at IS NULL " +
            "           <if test='lotIds != null and lotIds.size() > 0'>" +
            "             AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "           </if> " +
            "           GROUP BY DATE(exit_time)) x ON d.period = x.period " +
            "ORDER BY d.period" +
            "</script>")
    List<Map<String, Object>> trafficByDay(@Param("startTime") String startTime,
                                           @Param("endTime") String endTime,
                                           @Param("lotIds") Set<Long> lotIds);

    /**
     * 按小时统计入场量（用于识别高峰时段）。
     */
    @Select("<script>" +
            "SELECT HOUR(entry_time) AS hour, COUNT(*) AS entry_count " +
            "FROM parking_record " +
            "WHERE entry_time >= #{startTime} AND entry_time &lt; #{endTime} AND deleted_at IS NULL " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY HOUR(entry_time) ORDER BY hour" +
            "</script>")
    List<Map<String, Object>> hourlyEntry(@Param("startTime") String startTime,
                                          @Param("endTime") String endTime,
                                          @Param("lotIds") Set<Long> lotIds);

    /**
     * 按小时统计出场量。
     */
    @Select("<script>" +
            "SELECT HOUR(exit_time) AS hour, COUNT(*) AS exit_count " +
            "FROM parking_record " +
            "WHERE exit_time IS NOT NULL AND exit_time >= #{startTime} AND exit_time &lt; #{endTime} AND deleted_at IS NULL " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY HOUR(exit_time) ORDER BY hour" +
            "</script>")
    List<Map<String, Object>> hourlyExit(@Param("startTime") String startTime,
                                         @Param("endTime") String endTime,
                                         @Param("lotIds") Set<Long> lotIds);

    /**
     * 查询时间段内各车场的峰时段。
     */
    @Select("<script>" +
            "SELECT t.parking_lot_id AS lot_id, t.hr AS peak_hour, t.cnt AS peak_count FROM (" +
            "  SELECT r.parking_lot_id, HOUR(r.entry_time) AS hr, COUNT(*) AS cnt, " +
            "         ROW_NUMBER() OVER (PARTITION BY r.parking_lot_id ORDER BY COUNT(*) DESC) AS rn " +
            "  FROM parking_record r " +
            "  WHERE r.entry_time >= #{startTime} AND r.entry_time &lt; #{endTime} AND r.deleted_at IS NULL " +
            "  <if test='lotIds != null and lotIds.size() > 0'>" +
            "    AND r.parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "  </if> " +
            "  GROUP BY r.parking_lot_id, HOUR(r.entry_time)" +
            ") t WHERE t.rn = 1" +
            "</script>")
    List<Map<String, Object>> peakHours(@Param("startTime") String startTime,
                                        @Param("endTime") String endTime,
                                        @Param("lotIds") Set<Long> lotIds);
}
