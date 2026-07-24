package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.device.entity.DeviceCommandAudit;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.device.mapper.DeviceCommandAuditMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.device.service.DeviceService;
import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;
import com.jushan.platform.modules.parking.vo.ManualGateRecordAdminVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 手动开闸记录管理 Controller（Phase 2 D3）。
 * <p>
 * 提供运营端手动开闸记录的分页查询。
 * 数据来源于 {@code device_command_audit} 表中 command_type = OPEN_GATE 且 source = MANUAL 的记录。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/manual-gate-records")
public class ManualGateRecordAdminController {

    private static final Logger log = LoggerFactory.getLogger(ManualGateRecordAdminController.class);

    private final DeviceCommandAuditMapper auditMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public ManualGateRecordAdminController(DeviceCommandAuditMapper auditMapper,
                                            ParkingLotMapper parkingLotMapper,
                                            ParkingLaneMapper laneMapper,
                                            ParkingLotScopeResolver scopeResolver) {
        this.auditMapper = auditMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 分页查询手动开闸记录。
     * <p>
     * 筛选条件：时间范围、车场、操作人。
     * 默认查询 command_type = OPEN_GATE 且 source = MANUAL 的记录，按操作时间倒序。
     *
     * @param startTime     操作时间起
     * @param endTime       操作时间止
     * @param parkingLotId  车场 ID
     * @param operatorName  操作人名称（模糊匹配）
     * @param page          页码
     * @param size          每页大小
     */
    @GetMapping
    @RequirePermission("device:audit")
    public R<IPage<ManualGateRecordAdminVO>> pageList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 数据范围
        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedLotIds != null && authorizedLotIds.isEmpty()) {
            return R.ok(new Page<ManualGateRecordAdminVO>(page, size)
                    .setRecords(Collections.emptyList()).setTotal(0));
        }

        if (parkingLotId != null) {
            scopeResolver.validateAccess(parkingLotId);
        }

        QueryWrapper<DeviceCommandAudit> wrapper = new QueryWrapper<>();

        // 只查手动开闸记录
        wrapper.eq("command_type", DeviceService.COMMAND_TYPE_OPEN_GATE);
        wrapper.eq("source", DeviceService.SOURCE_MANUAL);

        if (startTime != null) {
            wrapper.ge("issued_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("issued_at", endTime);
        }
        if (parkingLotId != null) {
            wrapper.eq("parking_lot_id", parkingLotId);
        }
        if (operatorName != null && !operatorName.isBlank()) {
            wrapper.like("operator_name", operatorName.trim());
        }
        if (authorizedLotIds != null) {
            wrapper.in("parking_lot_id", authorizedLotIds);
        }

        wrapper.orderByDesc("issued_at");

        IPage<DeviceCommandAudit> auditPage = auditMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<ManualGateRecordAdminVO> voList = convertToVOList(auditPage.getRecords());

        IPage<ManualGateRecordAdminVO> result = new Page<>(auditPage.getCurrent(),
                auditPage.getSize(), auditPage.getTotal());
        result.setRecords(voList);

        return R.ok(result);
    }

    // ==================== 批量 VO 转换 ====================

    private List<ManualGateRecordAdminVO> convertToVOList(List<DeviceCommandAudit> audits) {
        if (audits == null || audits.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集关联 ID
        Set<Long> lotIds = new HashSet<>();
        Set<Long> laneIds = new HashSet<>();

        for (DeviceCommandAudit a : audits) {
            if (a.getParkingLotId() != null) lotIds.add(a.getParkingLotId());
            if (a.getLaneId() != null) laneIds.add(a.getLaneId());
        }

        // 批量查询
        Map<Long, ParkingLot> lotMap = batchQueryMap(lotIds,
                parkingLotMapper::selectBatchIds, ParkingLot::getId);
        Map<Long, ParkingLane> laneMap = batchQueryMap(laneIds,
                laneMapper::selectBatchIds, ParkingLane::getId);

        return audits.stream().map(a -> {
            ManualGateRecordAdminVO vo = new ManualGateRecordAdminVO();
            vo.setId(a.getId());
            vo.setOperatorName(a.getOperatorName());
            vo.setOperationTime(a.getIssuedAt() != null ? a.getIssuedAt() : a.getCreatedAt());
            vo.setParkingLotId(a.getParkingLotId());

            ParkingLot lot = lotMap.get(a.getParkingLotId());
            vo.setParkingLotName(lot != null ? lot.getName() : null);

            ParkingLane lane = laneMap.get(a.getLaneId());
            vo.setLaneName(lane != null
                    ? (lane.getName() != null ? lane.getName() : lane.getLaneNo())
                    : null);

            vo.setReason(a.getReason());
            vo.setPlateNumber(a.getPlateNumber());
            vo.setFeeCents(a.getFeeCents());
            vo.setCommandStatus(a.getStatus());
            vo.setSource(a.getSource());
            vo.setCommandType(a.getCommandType());
            vo.setCreatedAt(a.getCreatedAt());

            return vo;
        }).collect(Collectors.toList());
    }

    private <T> Map<Long, T> batchQueryMap(Set<Long> ids,
                                            Function<List<Long>, List<T>> batchQuery,
                                            Function<T, Long> idExtractor) {
        if (ids.isEmpty()) return Collections.emptyMap();
        List<T> entities = batchQuery.apply(new ArrayList<>(ids));
        return entities.stream().collect(Collectors.toMap(idExtractor, Function.identity(),
                (a, b) -> a));
    }
}
