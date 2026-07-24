package com.jushan.platform.modules.booth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.parking.vo.ParkingSessionVO;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗亭端车辆查询控制器。
 * <p>
 * 提供在场车辆列表和历史通行记录查询，数据来源于 parking_session。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/booth/vehicles")
public class BoothVehicleController {

    private final ParkingSessionService parkingSessionService;
    private final ParkingLaneMapper parkingLaneMapper;

    public BoothVehicleController(ParkingSessionService parkingSessionService,
                                  ParkingLaneMapper parkingLaneMapper) {
        this.parkingSessionService = parkingSessionService;
        this.parkingLaneMapper = parkingLaneMapper;
    }

    /**
     * 在场车辆查询（parking_session status=IN）。
     */
    @GetMapping("/present")
    @RequirePermission("booth:view")
    public R<Map<String, Object>> presentVehicles(
            @RequestParam Long parkingLotId,
            @RequestParam(defaultValue = "entryTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        IPage<ParkingSessionVO> result = parkingSessionService.pageList(
                new Page<>(page, size), parkingLotId, null, "IN");

        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::toPresentVehicle)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("size", result.getSize());
        data.put("current", result.getCurrent());
        return R.ok(data);
    }

    /**
     * 历史通行记录查询（parking_session 全状态分页）。
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

        IPage<ParkingSessionVO> result = parkingSessionService.pageList(
                new Page<>(page, size), parkingLotId, plateNumber, null);

        Set<Long> laneIds = result.getRecords().stream()
                .map(ParkingSessionVO::getLaneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> laneNames = laneIds.isEmpty()
                ? Collections.emptyMap()
                : parkingLaneMapper.selectBatchIds(laneIds).stream()
                        .collect(Collectors.toMap(ParkingLane::getId, ParkingLane::getName));

        List<Map<String, Object>> records = result.getRecords().stream()
                .map(vo -> toHistoryRecord(vo, laneNames))
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("size", result.getSize());
        data.put("current", result.getCurrent());
        return R.ok(data);
    }

    private Map<String, Object> toPresentVehicle(ParkingSessionVO vo) {
        Map<String, Object> map = new HashMap<>();
        map.put("plateNumber", vo.getPlateNumber());
        map.put("entryTime", format(vo.getEntryTime()));
        long duration = vo.getDurationMinutes() != null ? vo.getDurationMinutes()
                : java.time.Duration.between(vo.getEntryTime(), LocalDateTime.now()).toMinutes();
        map.put("durationMinutes", duration);
        map.put("vehicleType", vo.getVehicleType());
        map.put("isMonthlyPass", "MONTHLY".equals(vo.getVehicleType()));
        map.put("isFixedSpace", "FIXED_SPACE".equals(vo.getVehicleType()));
        map.put("parkingRecordId", vo.getId());
        return map;
    }

    private Map<String, Object> toHistoryRecord(ParkingSessionVO vo, Map<Long, String> laneNames) {
        Map<String, Object> map = new HashMap<>();
        map.put("plateNumber", vo.getPlateNumber());
        map.put("entryTime", format(vo.getEntryTime()));
        map.put("exitTime", format(vo.getExitTime()));
        map.put("feeAmount", vo.getFeeAmount());
        map.put("entryImage", vo.getEntryImage());
        String paymentStatus = null;
        if (vo.getExitTime() != null) {
            paymentStatus = vo.getPaidAmount() != null && vo.getPaidAmount().compareTo(BigDecimal.ZERO) > 0
                    ? "PAID" : "UNPAID";
        }
        map.put("paymentStatus", paymentStatus);
        map.put("laneName", vo.getLaneId() != null ? laneNames.getOrDefault(vo.getLaneId(), "") : "");
        return map;
    }

    private static String format(LocalDateTime time) {
        return time != null ? time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null;
    }
}
