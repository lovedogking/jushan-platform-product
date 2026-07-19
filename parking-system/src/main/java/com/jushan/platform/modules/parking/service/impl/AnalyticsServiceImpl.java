package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.platform.modules.parking.dto.AnalyticsQueryCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.parking.service.AnalyticsService;
import com.jushan.platform.modules.parking.vo.AnalyticsOverviewVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ParkingSessionMapper parkingSessionMapper;

    public AnalyticsServiceImpl(ParkingSessionMapper parkingSessionMapper) {
        this.parkingSessionMapper = parkingSessionMapper;
    }

    @Override
    public AnalyticsOverviewVO getOverview(AnalyticsQueryCmd cmd) {
        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();
        LocalDateTime[] range = resolveTimeRange(cmd);

        // 入场总数（entry_time 在范围内）
        Long entryCount = countByEntryTimeRange(range[0], range[1], cmd.getLotId());
        vo.setEntryCount(entryCount);

        // 出场总数（exit_time 在范围内）
        Long exitCount = countByExitTimeRange(range[0], range[1], cmd.getLotId());
        vo.setExitCount(exitCount);

        // 当前在场
        Long currentInCount = countCurrentIn(cmd.getLotId());
        vo.setCurrentInCount(currentInCount);

        // 入场触发方式统计
        Map<String, Long> triggerStats = countByEntryTrigger(range[0], range[1], cmd.getLotId());
        vo.setEntryTriggerStats(triggerStats);

        // 趋势数据
        List<AnalyticsOverviewVO.TrendPoint> trend = buildTrend(range[0], range[1], cmd);
        vo.setTrendData(trend);

        // 营收一期占位
        vo.setRevenue(null);

        return vo;
    }

    private LocalDateTime[] resolveTimeRange(AnalyticsQueryCmd cmd) {
        LocalDate today = LocalDate.now();
        LocalDateTime start, end;
        switch (cmd.getPeriod() != null ? cmd.getPeriod() : "today") {
            case "today":
                start = today.atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "month":
                start = today.withDayOfMonth(1).atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "year":
                start = today.withDayOfYear(1).atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "custom":
                start = cmd.getStartDate() != null ? cmd.getStartDate().atStartOfDay() : today.atStartOfDay();
                end = (cmd.getEndDate() != null ? cmd.getEndDate() : today).atTime(LocalTime.MAX);
                break;
            default:
                start = today.atStartOfDay();
                end = today.atTime(LocalTime.MAX);
        }
        return new LocalDateTime[]{start, end};
    }

    private Long countByEntryTimeRange(LocalDateTime start, LocalDateTime end, Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.between(ParkingSession::getEntryTime, start, end);
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        return parkingSessionMapper.selectCount(qw);
    }

    private Long countByExitTimeRange(LocalDateTime start, LocalDateTime end, Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.between(ParkingSession::getExitTime, start, end);
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        return parkingSessionMapper.selectCount(qw);
    }

    private Long countCurrentIn(Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.eq(ParkingSession::getStatus, "IN");
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        return parkingSessionMapper.selectCount(qw);
    }

    private Map<String, Long> countByEntryTrigger(LocalDateTime start, LocalDateTime end, Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.between(ParkingSession::getEntryTime, start, end);
        qw.isNotNull(ParkingSession::getEntryTrigger);
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        List<ParkingSession> sessions = parkingSessionMapper.selectList(qw);
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("whitelist_auto", 0L);
        stats.put("manual_open", 0L);
        stats.put("always_open_period", 0L);
        stats.put("manual_entry", 0L);
        for (ParkingSession s : sessions) {
            String trigger = s.getEntryTrigger();
            if (trigger != null) {
                stats.merge(trigger, 1L, Long::sum);
            }
        }
        return stats;
    }

    private List<AnalyticsOverviewVO.TrendPoint> buildTrend(LocalDateTime start, LocalDateTime end, AnalyticsQueryCmd cmd) {
        String period = cmd.getPeriod() != null ? cmd.getPeriod() : "today";
        ChronoUnit unit;
        DateTimeFormatter fmt;
        if ("today".equals(period)) {
            unit = ChronoUnit.HOURS;
            fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        } else if ("month".equals(period)) {
            unit = ChronoUnit.DAYS;
            fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        } else {
            unit = ChronoUnit.MONTHS;
            fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        }

        // 查询范围内所有 session
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.between(ParkingSession::getEntryTime, start, end)
                .or().between(ParkingSession::getExitTime, start, end));
        if (cmd.getLotId() != null && cmd.getLotId() > 0) qw.eq(ParkingSession::getParkingLotId, cmd.getLotId());
        List<ParkingSession> sessions = parkingSessionMapper.selectList(qw);

        // 构建时段列表
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime cursor = start.truncatedTo(unit);
        while (!cursor.isAfter(end)) {
            slots.add(cursor);
            cursor = cursor.plus(1, unit);
        }

        return slots.stream().map(slot -> {
            LocalDateTime slotEnd = slot.plus(1, unit);
            long entry = sessions.stream().filter(s -> {
                LocalDateTime et = s.getEntryTime();
                return et != null && !et.isBefore(slot) && et.isBefore(slotEnd);
            }).count();
            long exit = sessions.stream().filter(s -> {
                LocalDateTime xt = s.getExitTime();
                return xt != null && !xt.isBefore(slot) && xt.isBefore(slotEnd);
            }).count();
            return new AnalyticsOverviewVO.TrendPoint(slot.format(fmt), entry, exit);
        }).collect(Collectors.toList());
    }
}
