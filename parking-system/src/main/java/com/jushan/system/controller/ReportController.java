package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.mapper.ReportMapper;
import com.jushan.system.service.ParkingLotScopeResolver;
import com.jushan.system.vo.RevenueReportVO;
import com.jushan.system.vo.TrafficReportVO;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 报表 Controller（包 6-1：收入报表 + 车流量报表）。
 * <p>
 * 支持按日/月/年维度的收入报表和按日/小时的流量报表，以及 xlsx 导出。
 * <p>
 * 数据隔离：基于 {@link ParkingLotScopeResolver}，仅展示已授权车场数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportMapper reportMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public ReportController(ReportMapper reportMapper, ParkingLotScopeResolver scopeResolver) {
        this.reportMapper = reportMapper;
        this.scopeResolver = scopeResolver;
    }

    // ==================== 收入报表 ====================

    /**
     * 收入报表（摘要 + 按期列表）。
     *
     * @param periodType 周期类型：DAILY / MONTHLY / YEARLY
     * @param startDate  开始日期（含）
     * @param endDate    结束日期（含，统一处理为次日 00:00 做 &lt; 比较）
     * @param lotId      可选：指定车场 ID
     */
    @GetMapping("/revenue")
    @RequirePermission("dashboard:view")
    public R<RevenueReportVO> revenueReport(
            @RequestParam(defaultValue = "DAILY") String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId) {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        // 汇总
        Map<String, Object> summary = reportMapper.sumRevenue(start, end, lotIds);
        Long totalRevenue = toLong(summary.get("total_revenue"));
        Long orderCount = toLong(summary.get("order_count"));
        Long avgOrderAmount = orderCount != null && orderCount > 0 ? totalRevenue / orderCount : 0L;

        // 按期查询
        List<Map<String, Object>> rawPeriods;
        switch (periodType.toUpperCase()) {
            case "MONTHLY":
                rawPeriods = reportMapper.revenueByMonth(start, end, lotIds);
                break;
            case "YEARLY":
                rawPeriods = reportMapper.revenueByYear(start, end, lotIds);
                break;
            default:
                rawPeriods = reportMapper.revenueByDay(start, end, lotIds);
        }

        List<RevenueReportVO.PeriodStat> periods = rawPeriods.stream()
                .map(m -> new RevenueReportVO.PeriodStat(
                        String.valueOf(m.get("period")),
                        toLong(m.get("total_revenue")),
                        toLong(m.get("order_count"))))
                .collect(Collectors.toList());

        RevenueReportVO vo = new RevenueReportVO();
        vo.setTotalRevenue(totalRevenue);
        vo.setOrderCount(orderCount);
        vo.setAvgOrderAmount(avgOrderAmount);
        vo.setPeriods(periods);
        return R.ok(vo);
    }

    /**
     * 车流量报表。
     *
     * @param startDate 开始日期（含）
     * @param endDate   结束日期（含）
     * @param lotId     可选：指定车场 ID
     * @param laneId    可选：指定通道 ID（暂保留，本期后端暂不按 laneId 过滤）
     */
    @GetMapping("/traffic")
    @RequirePermission("dashboard:view")
    public R<TrafficReportVO> trafficReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long laneId) {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        // 按天
        List<Map<String, Object>> rawDaily = reportMapper.trafficByDay(start, end, lotIds);
        List<TrafficReportVO.DailyStat> dailyStats = rawDaily.stream()
                .map(m -> new TrafficReportVO.DailyStat(
                        String.valueOf(m.get("period")),
                        toLong(m.get("entry_count")),
                        toLong(m.get("exit_count"))))
                .collect(Collectors.toList());

        // 按小时
        Map<Integer, Long> entryByHour = toHourMap(reportMapper.hourlyEntry(start, end, lotIds), "entry_count");
        Map<Integer, Long> exitByHour = toHourMap(reportMapper.hourlyExit(start, end, lotIds), "exit_count");

        List<TrafficReportVO.HourlyStat> hourlyStats = IntStream.range(0, 24)
                .mapToObj(h -> new TrafficReportVO.HourlyStat(h,
                        entryByHour.getOrDefault(h, 0L),
                        exitByHour.getOrDefault(h, 0L)))
                .collect(Collectors.toList());

        // 汇总
        long totalEntry = dailyStats.stream().mapToLong(TrafficReportVO.DailyStat::getEntryCount).sum();
        long totalExit = dailyStats.stream().mapToLong(TrafficReportVO.DailyStat::getExitCount).sum();

        // 峰时
        TrafficReportVO.HourlyStat peak = hourlyStats.stream()
                .max(Comparator.comparingLong(TrafficReportVO.HourlyStat::getTotal))
                .orElse(new TrafficReportVO.HourlyStat(0, 0L, 0L));

        TrafficReportVO vo = new TrafficReportVO();
        vo.setTotalEntry(totalEntry);
        vo.setTotalExit(totalExit);
        vo.setPeakHour(peak.getHour());
        vo.setPeakCount(peak.getTotal());
        vo.setDailyStats(dailyStats);
        vo.setHourlyStats(hourlyStats);
        return R.ok(vo);
    }

    // ==================== xlsx 导出 ====================

    /**
     * 导出收入报表 xlsx。
     */
    @GetMapping("/revenue/export")
    @RequirePermission("dashboard:view")
    @BusinessLog(value = "导出收入报表", module = "report", operationType = "EXPORT")
    public void exportRevenue(
            @RequestParam(defaultValue = "DAILY") String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId,
            HttpServletResponse response) throws IOException {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        List<Map<String, Object>> rawPeriods;
        switch (periodType.toUpperCase()) {
            case "MONTHLY":
                rawPeriods = reportMapper.revenueByMonth(start, end, lotIds);
                break;
            case "YEARLY":
                rawPeriods = reportMapper.revenueByYear(start, end, lotIds);
                break;
            default:
                rawPeriods = reportMapper.revenueByDay(start, end, lotIds);
        }

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("收入报表");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(wb);
        String[] headers = {"期间", "收入(元)", "订单数", "平均订单金额(元)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CellStyle dataStyle = wb.createCellStyle();
        CreationHelper helper = wb.getCreationHelper();
        dataStyle.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));

        int rowIdx = 1;
        for (Map<String, Object> m : rawPeriods) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(String.valueOf(m.get("period")));
            double rev = toLong(m.get("total_revenue")) / 100.0;
            Cell revCell = row.createCell(1);
            revCell.setCellValue(rev);
            revCell.setCellStyle(dataStyle);
            row.createCell(2).setCellValue(toLong(m.get("order_count")));
            long cnt = toLong(m.get("order_count"));
            Cell avgCell = row.createCell(3);
            avgCell.setCellValue(cnt > 0 ? rev / cnt : 0);
            avgCell.setCellStyle(dataStyle);
        }
        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        writeXlsxResponse(response, wb, "收入报表");
    }

    /**
     * 导出车流量报表 xlsx。
     */
    @GetMapping("/traffic/export")
    @RequirePermission("dashboard:view")
    @BusinessLog(value = "导出车流量报表", module = "report", operationType = "EXPORT")
    public void exportTraffic(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId,
            HttpServletResponse response) throws IOException {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        List<Map<String, Object>> rawDaily = reportMapper.trafficByDay(start, end, lotIds);

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("车流量报表");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(wb);
        String[] headers = {"日期", "入场量", "出场量", "合计"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (Map<String, Object> m : rawDaily) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(String.valueOf(m.get("period")));
            row.createCell(1).setCellValue(toLong(m.get("entry_count")));
            row.createCell(2).setCellValue(toLong(m.get("exit_count")));
            row.createCell(3).setCellValue(toLong(m.get("entry_count")) + toLong(m.get("exit_count")));
        }
        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        writeXlsxResponse(response, wb, "车流量报表");
    }

    // ==================== 私有方法 ====================

    private Set<Long> resolveLotIds(Long requestedLotId) {
        if (requestedLotId != null) {
            scopeResolver.validateAccess(requestedLotId);
            return Set.of(requestedLotId);
        }
        Set<Long> authorized = scopeResolver.resolveAuthorizedIds();
        // null = 全量（超管），返回 null 由 Mapper 的 <if> 跳过 IN 过滤
        if (authorized == null || authorized.isEmpty()) return null;
        return authorized;
    }

    private Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private Map<Integer, Long> toHourMap(List<Map<String, Object>> rows, String valueKey) {
        Map<Integer, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            map.put(hour, toLong(row.get(valueKey)));
        }
        return map;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeXlsxResponse(HttpServletResponse response, Workbook wb, String name) throws IOException {
        String fileName = URLEncoder.encode(name + "_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx",
                StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
        response.getOutputStream().flush();
    }
}
