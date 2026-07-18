package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.RecognitionEventLogMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.service.ParkingLotScopeResolver;
import com.jushan.system.vo.ParkingRecordAdminVO;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通行记录管理 Controller（Phase 2 D1）。
 * <p>
 * 提供运营端通行记录分页查询和 Excel 导出功能。
 * <p>
 * 数据隔离：
 * <ul>
 *   <li>租户管理员仅能查看本车场记录</li>
 *   <li>平台用户可查看全部记录</li>
 *   <li>基于 {@link ParkingLotScopeResolver} 统一数据范围</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/parking-records")
public class ParkingRecordAdminController {

    private static final Logger log = LoggerFactory.getLogger(ParkingRecordAdminController.class);

    private static final int EXPORT_MAX_LIMIT = 10000;

    private static final Map<String, String> STATUS_LABEL = Map.of(
            ParkingRecord.STATUS_PARKING, "在场",
            ParkingRecord.STATUS_COMPLETED, "已离场",
            ParkingRecord.STATUS_CANCELLED, "已作废"
    );

    private static final Map<String, String> PAY_CHANNEL_LABEL = Map.of(
            ParkingOrder.PAY_CHANNEL_PYUN, "P云",
            ParkingOrder.PAY_CHANNEL_WECHAT, "微信",
            ParkingOrder.PAY_CHANNEL_ALIPAY, "支付宝",
            ParkingOrder.PAY_CHANNEL_CASH, "现金",
            ParkingOrder.PAY_CHANNEL_BALANCE, "余额"
    );

    private final ParkingRecordMapper recordMapper;
    private final ParkingOrderMapper orderMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final SysUserMapper userMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public ParkingRecordAdminController(ParkingRecordMapper recordMapper,
                                         ParkingOrderMapper orderMapper,
                                         ParkingLotMapper parkingLotMapper,
                                         ParkingLaneMapper laneMapper,
                                         RecognitionEventLogMapper eventLogMapper,
                                         SysUserMapper userMapper,
                                         ParkingLotScopeResolver scopeResolver) {
        this.recordMapper = recordMapper;
        this.orderMapper = orderMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.eventLogMapper = eventLogMapper;
        this.userMapper = userMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 分页查询通行记录。
     *
     * @param plateNumber  车牌号（模糊匹配）
     * @param parkingLotId 停车场 ID
     * @param laneId       入场通道 ID
     * @param startTime    入场时间起
     * @param endTime      入场时间止
     * @param status       状态（PARKING/COMPLETED）
     * @param page         页码
     * @param size         每页大小
     */
    @GetMapping
    @RequirePermission("record:view")
    public R<IPage<ParkingRecordAdminVO>> pageList(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) Long laneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer tempPlateFlag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 数据范围
        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedLotIds != null && authorizedLotIds.isEmpty()) {
            return R.ok(new Page<ParkingRecordAdminVO>(page, size)
                    .setRecords(Collections.emptyList()).setTotal(0));
        }

        if (parkingLotId != null) {
            scopeResolver.validateAccess(parkingLotId);
        }

        QueryWrapper<ParkingRecord> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (plateNumber != null && !plateNumber.isBlank()) {
            wrapper.like("standardized_plate", plateNumber.trim().toUpperCase());
        }
        if (parkingLotId != null) {
            wrapper.eq("parking_lot_id", parkingLotId);
        }
        if (laneId != null) {
            wrapper.eq("lane_id", laneId);
        }
        if (startTime != null) {
            wrapper.ge("entry_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("entry_time", endTime);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (tempPlateFlag != null) {
            wrapper.eq("temp_plate_flag", tempPlateFlag);
        }
        if (authorizedLotIds != null) {
            wrapper.in("parking_lot_id", authorizedLotIds);
        }

        wrapper.orderByDesc("entry_time");

        IPage<ParkingRecord> recordPage = recordMapper.selectPage(new Page<>(page, size), wrapper);

        List<ParkingRecordAdminVO> voList = convertToVOList(recordPage.getRecords());

        IPage<ParkingRecordAdminVO> result = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        result.setRecords(voList);

        return R.ok(result);
    }

    /**
     * Excel 导出通行记录。
     * <p>
     * 单次导出最多 10,000 条，超过时提示缩小筛选范围。
     */
    @GetMapping("/export")
    @RequirePermission("record:view")
    @BusinessLog(value = "导出通行记录", module = "parking_record", operationType = "EXPORT")
    public void exportRecords(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) Long laneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws IOException {

        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedLotIds != null && authorizedLotIds.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限导出通行记录\"}");
            return;
        }

        if (parkingLotId != null) {
            scopeResolver.validateAccess(parkingLotId);
        }

        QueryWrapper<ParkingRecord> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (plateNumber != null && !plateNumber.isBlank()) {
            wrapper.like("standardized_plate", plateNumber.trim().toUpperCase());
        }
        if (parkingLotId != null) {
            wrapper.eq("parking_lot_id", parkingLotId);
        }
        if (laneId != null) {
            wrapper.eq("lane_id", laneId);
        }
        if (startTime != null) {
            wrapper.ge("entry_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("entry_time", endTime);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (authorizedLotIds != null) {
            wrapper.in("parking_lot_id", authorizedLotIds);
        }
        wrapper.orderByDesc("entry_time");

        Long total = recordMapper.selectCount(wrapper);
        if (total > EXPORT_MAX_LIMIT) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"message\":\"导出数据超过 " + EXPORT_MAX_LIMIT + " 条，请缩小筛选范围\"}");
            return;
        }

        List<ParkingRecord> records = recordMapper.selectList(wrapper.last("LIMIT " + EXPORT_MAX_LIMIT));
        List<ParkingRecordAdminVO> voList = convertToVOList(records);

        // xlsx 导出
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("通行记录导出");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] headers = {"车牌号", "入场时间", "出场时间", "停车时长", "应收金额(元)", "实付金额(元)", "支付方式", "状态", "入场通道", "出口通道", "操作人", "放行原因"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CellStyle yuanStyle = wb.createCellStyle();
        yuanStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

        int rowIdx = 1;
        for (ParkingRecordAdminVO vo : voList) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(vo.getPlateNumber() != null ? vo.getPlateNumber() : "");
            row.createCell(1).setCellValue(formatDateTime(vo.getEntryTime()));
            row.createCell(2).setCellValue(formatDateTime(vo.getExitTime()));
            row.createCell(3).setCellValue(formatDuration(vo.getParkingDurationMinutes()));
            Cell feeCell = row.createCell(4);
            feeCell.setCellValue(vo.getFeeAmount() != null ? vo.getFeeAmount() / 100.0 : 0.0);
            feeCell.setCellStyle(yuanStyle);
            Cell paidCell = row.createCell(5);
            paidCell.setCellValue(vo.getPaidAmount() != null ? vo.getPaidAmount() / 100.0 : 0.0);
            paidCell.setCellStyle(yuanStyle);
            row.createCell(6).setCellValue(vo.getPayChannelLabel() != null ? vo.getPayChannelLabel() : "");
            row.createCell(7).setCellValue(vo.getStatusLabel() != null ? vo.getStatusLabel() : "");
            row.createCell(8).setCellValue(vo.getEntryLaneName() != null ? vo.getEntryLaneName() : "");
            row.createCell(9).setCellValue(vo.getExitLaneName() != null ? vo.getExitLaneName() : "");
            row.createCell(10).setCellValue(vo.getOperatorName() != null ? vo.getOperatorName() : "");
            row.createCell(11).setCellValue(vo.getReleaseReason() != null ? vo.getReleaseReason() : "");
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        String fileName = URLEncoder.encode("通行记录导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
        response.getOutputStream().flush();
    }

    // ==================== 批量 VO 转换 ====================

    /**
     * 批量将 ParkingRecord 转换为 VO，使用批量查询避免 N+1。
     */
    private List<ParkingRecordAdminVO> convertToVOList(List<ParkingRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 收集所有关联 ID
        Set<Long> lotIds = new HashSet<>();
        Set<Long> laneIds = new HashSet<>();
        Set<Long> recordIds = new HashSet<>();
        Set<Long> exitEventIds = new HashSet<>();

        for (ParkingRecord r : records) {
            if (r.getParkingLotId() != null) lotIds.add(r.getParkingLotId());
            if (r.getLaneId() != null) laneIds.add(r.getLaneId());
            recordIds.add(r.getId());
            if (r.getExitEventId() != null) exitEventIds.add(r.getExitEventId());
        }

        // 2. 批量查询关联数据
        Map<Long, ParkingLot> lotMap = batchQueryMap(lotIds, parkingLotMapper::selectBatchIds,
                ParkingLot::getId);
        Map<Long, ParkingLane> laneMap = batchQueryMap(laneIds, laneMapper::selectBatchIds,
                ParkingLane::getId);

        // 3. 批量查询订单（按 parking_record_id）
        List<ParkingOrder> orders = recordIds.isEmpty()
                ? Collections.emptyList()
                : orderMapper.selectList(
                    new QueryWrapper<ParkingOrder>()
                            .in("parking_record_id", recordIds)
                            .eq("order_type", ParkingOrder.ORDER_TYPE_PARKING)
                            .isNull("deleted_at"));
        Map<Long, List<ParkingOrder>> orderMap = orders.stream()
                .collect(Collectors.groupingBy(ParkingOrder::getParkingRecordId));

        // 4. 批量查询出场事件（获取出口通道）
        Map<Long, RecognitionEventLog> exitEventMap = batchQueryMap(exitEventIds,
                eventLogMapper::selectBatchIds, RecognitionEventLog::getId);
        // 出场事件中的 laneId → 通道名称
        Set<Long> exitLaneIds = exitEventMap.values().stream()
                .map(RecognitionEventLog::getLaneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        exitLaneIds.removeAll(laneIds); // 避免重复查询已加载的入口通道
        Map<Long, ParkingLane> exitLaneMap = batchQueryMap(exitLaneIds,
                laneMapper::selectBatchIds, ParkingLane::getId);
        // 合并通道映射
        Map<Long, ParkingLane> allLaneMap = new HashMap<>(laneMap);
        allLaneMap.putAll(exitLaneMap);

        // 5. 批量查询操作人
        Set<Long> operatorIds = orders.stream()
                .map(ParkingOrder::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = batchQueryMap(operatorIds,
                userMapper::selectBatchIds, SysUser::getId);

        // 6. 组装 VO
        return records.stream().map(record -> {
            ParkingRecordAdminVO vo = new ParkingRecordAdminVO();
            vo.setId(record.getId());
            vo.setPlateNumber(record.getStandardizedPlate());
            vo.setParkingLotId(record.getParkingLotId());

            // 车场名称
            ParkingLot lot = lotMap.get(record.getParkingLotId());
            vo.setParkingLotName(lot != null ? lot.getName() : null);

            // 入场通道
            vo.setEntryLaneId(record.getLaneId());
            ParkingLane entryLane = laneMap.get(record.getLaneId());
            vo.setEntryLaneName(entryLane != null
                    ? (entryLane.getName() != null ? entryLane.getName() : entryLane.getLaneNo())
                    : null);

            // 出场通道（从出场事件获取）
            if (record.getExitEventId() != null) {
                RecognitionEventLog exitEvent = exitEventMap.get(record.getExitEventId());
                if (exitEvent != null && exitEvent.getLaneId() != null) {
                    ParkingLane exitLane = allLaneMap.get(exitEvent.getLaneId());
                    vo.setExitLaneName(exitLane != null
                            ? (exitLane.getName() != null ? exitLane.getName() : exitLane.getLaneNo())
                            : null);
                }
            }

            vo.setEntryTime(record.getEntryTime());
            vo.setExitTime(record.getExitTime());
            vo.setStatus(record.getStatus());
            vo.setStatusLabel(STATUS_LABEL.getOrDefault(record.getStatus(), record.getStatus()));
            vo.setTempPlateFlag(record.getTempPlateFlag());

            // 停车时长
            if (record.getEntryTime() != null && record.getExitTime() != null) {
                vo.setParkingDurationMinutes((int) Duration.between(record.getEntryTime(), record.getExitTime()).toMinutes());
            }

            // 关联订单信息（取第一条有效订单）
            List<ParkingOrder> recordOrders = orderMap.get(record.getId());
            if (recordOrders != null && !recordOrders.isEmpty()) {
                ParkingOrder order = recordOrders.get(0);
                vo.setFeeAmount(order.getPayableAmount());
                vo.setPaidAmount(order.getPaidAmount());
                vo.setPayChannel(order.getPayChannel());
                vo.setPayChannelLabel(PAY_CHANNEL_LABEL.getOrDefault(order.getPayChannel(), order.getPayChannel()));

                // 操作人
                if (order.getOperatorId() != null) {
                    SysUser user = userMap.get(order.getOperatorId());
                    vo.setOperatorName(user != null
                            ? (user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                            : null);
                }
            }

            vo.setCreatedAt(record.getCreatedAt());
            vo.setUpdatedAt(record.getUpdatedAt());

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 批量查询并转换为 ID → Entity 映射。
     */
    private <T> Map<Long, T> batchQueryMap(Set<Long> ids,
                                            Function<List<Long>, List<T>> batchQuery,
                                            Function<T, Long> idExtractor) {
        if (ids.isEmpty()) return Collections.emptyMap();
        List<T> entities = batchQuery.apply(new ArrayList<>(ids));
        return entities.stream().collect(Collectors.toMap(idExtractor, Function.identity(),
                (a, b) -> a));
    }

    // ==================== 导出工具方法 ====================

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return "";
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours > 0) {
            return hours + "小时" + mins + "分钟";
        }
        return mins + "分钟";
    }

}
