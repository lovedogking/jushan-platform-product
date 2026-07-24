package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.entity.ExceptionRecord;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.account.entity.SysUser;
import com.jushan.platform.modules.parking.mapper.ExceptionRecordMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.account.mapper.SysUserMapper;
import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;
import com.jushan.platform.modules.parking.vo.ExceptionAdminVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 异常记录管理 Controller（Phase 2 D2）。
 * <p>
 * 提供运营端异常记录分页查询和标记处理功能。
 * <p>
 * 异常类型：DUP_ENTRY-重复入场, RECOGNITION_FAIL-识别失败, BLACKLIST-黑名单告警, UNPAID_INTERCEPT-未支付拦截
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/exception-records")
public class ExceptionRecordAdminController {

    private static final Logger log = LoggerFactory.getLogger(ExceptionRecordAdminController.class);

    private static final Map<String, String> EXCEPTION_TYPE_LABEL = Map.of(
            ExceptionRecord.TYPE_DUP_ENTRY, "重复入场",
            ExceptionRecord.TYPE_RECOGNITION_FAIL, "识别失败",
            ExceptionRecord.TYPE_BLACKLIST, "黑名单告警",
            ExceptionRecord.TYPE_UNPAID_INTERCEPT, "未支付拦截"
    );

    private static final Map<String, String> STATUS_LABEL = Map.of(
            ExceptionRecord.STATUS_UNHANDLED, "未处理",
            ExceptionRecord.STATUS_HANDLED, "已处理"
    );

    private final ExceptionRecordMapper exceptionRecordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final SysUserMapper userMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public ExceptionRecordAdminController(ExceptionRecordMapper exceptionRecordMapper,
                                           ParkingLotMapper parkingLotMapper,
                                           ParkingLaneMapper laneMapper,
                                           SysUserMapper userMapper,
                                           ParkingLotScopeResolver scopeResolver) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.userMapper = userMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 分页查询异常记录。
     *
     * @param exceptionType 异常类型（DUP_ENTRY / RECOGNITION_FAIL / BLACKLIST / UNPAID_INTERCEPT）
     * @param page          页码
     * @param size          每页大小
     */
    @GetMapping
    @RequirePermission("exception:view")
    public R<IPage<ExceptionAdminVO>> pageList(
            @RequestParam(required = false) String exceptionType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 数据范围
        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedLotIds != null && authorizedLotIds.isEmpty()) {
            return R.ok(new Page<ExceptionAdminVO>(page, size)
                    .setRecords(Collections.emptyList()).setTotal(0));
        }

        QueryWrapper<ExceptionRecord> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (exceptionType != null && !exceptionType.isBlank()) {
            wrapper.eq("exception_type", exceptionType);
        }
        if (authorizedLotIds != null) {
            wrapper.in("parking_lot_id", authorizedLotIds);
        }

        wrapper.orderByDesc("created_at");

        IPage<ExceptionRecord> entityPage = exceptionRecordMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<ExceptionAdminVO> voList = convertToVOList(entityPage.getRecords());

        IPage<ExceptionAdminVO> result = new Page<>(entityPage.getCurrent(),
                entityPage.getSize(), entityPage.getTotal());
        result.setRecords(voList);

        return R.ok(result);
    }

    /**
     * 标记异常记录为已处理。
     *
     * @param id 异常记录 ID
     */
    @PostMapping("/{id}/handle")
    @RequirePermission("exception:view")
    @BusinessLog(value = "处理异常记录", module = "exception_record", operationType = "UPDATE",
            operationObject = "异常记录", objectIdExpression = "#id")
    public R<Map<String, Object>> handleException(@PathVariable Long id) {
        ExceptionRecord record = exceptionRecordMapper.selectById(id);
        if (record == null || record.getDeletedAt() != null) {
            return R.fail(CommonErrorCode.NOT_FOUND.getCode(), "异常记录不存在");
        }

        // 数据范围校验
        scopeResolver.validateAccess(record.getParkingLotId());
        DataScope.validateTenantMatch(record.getTenantId(), "异常记录");

        // 状态校验
        if (ExceptionRecord.STATUS_HANDLED.equals(record.getStatus())) {
            return R.fail(CommonErrorCode.BUSINESS_ERROR.getCode(), "该记录已被处理");
        }

        record.setStatus(ExceptionRecord.STATUS_HANDLED);
        record.setHandledAt(LocalDateTime.now());
        // handler 由调用方使用 UserId，此处由 BusinessLog 记录操作日志

        int updated = exceptionRecordMapper.updateById(record);
        if (updated == 0) {
            return R.fail(CommonErrorCode.BUSINESS_ERROR.getCode(), "处理失败（记录可能已被删除）");
        }

        log.info("异常记录已处理: id={}, type={}", id, record.getExceptionType());
        return R.ok(Map.of("id", id, "status", ExceptionRecord.STATUS_HANDLED));
    }

    // ==================== 批量 VO 转换 ====================

    private List<ExceptionAdminVO> convertToVOList(List<ExceptionRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 收集关联 ID
        Set<Long> lotIds = new HashSet<>();
        Set<Long> laneIds = new HashSet<>();
        Set<Long> handlerIds = new HashSet<>();

        for (ExceptionRecord r : records) {
            if (r.getParkingLotId() != null) lotIds.add(r.getParkingLotId());
            if (r.getLaneId() != null) laneIds.add(r.getLaneId());
            if (r.getHandler() != null) handlerIds.add(r.getHandler());
        }

        // 2. 批量查询
        Map<Long, ParkingLot> lotMap = batchQueryMap(lotIds,
                parkingLotMapper::selectBatchIds, ParkingLot::getId);
        Map<Long, ParkingLane> laneMap = batchQueryMap(laneIds,
                laneMapper::selectBatchIds, ParkingLane::getId);
        Map<Long, SysUser> userMap = batchQueryMap(handlerIds,
                userMapper::selectBatchIds, SysUser::getId);

        // 3. 组装
        return records.stream().map(r -> {
            ExceptionAdminVO vo = new ExceptionAdminVO();
            vo.setId(r.getId());
            vo.setExceptionType(r.getExceptionType());
            vo.setExceptionTypeLabel(EXCEPTION_TYPE_LABEL.getOrDefault(
                    r.getExceptionType(), r.getExceptionType()));
            vo.setPlateNumber(r.getPlateNumber());
            vo.setParkingLotId(r.getParkingLotId());

            ParkingLot lot = lotMap.get(r.getParkingLotId());
            vo.setParkingLotName(lot != null ? lot.getName() : null);

            ParkingLane lane = laneMap.get(r.getLaneId());
            vo.setLaneName(lane != null
                    ? (lane.getName() != null ? lane.getName() : lane.getLaneNo())
                    : null);

            vo.setDescription(r.getDescription());
            vo.setStatus(r.getStatus());
            vo.setStatusLabel(STATUS_LABEL.getOrDefault(r.getStatus(), r.getStatus()));
            vo.setCreatedAt(r.getCreatedAt());
            vo.setHandledAt(r.getHandledAt());

            SysUser user = userMap.get(r.getHandler());
            vo.setHandlerName(user != null
                    ? (user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                    : null);

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
