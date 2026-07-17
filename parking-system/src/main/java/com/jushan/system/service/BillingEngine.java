package com.jushan.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.BillingRule;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.entity.BillingRuleSwitchLog;
import com.jushan.system.mapper.BillingRuleMapper;
import com.jushan.system.mapper.BillingRuleVersionMapper;
import com.jushan.system.mapper.BillingRuleSwitchLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费引擎（P006）。
 * <p>
 * 根据停车场当前生效的收费规则版本，计算停车费用。支持：
 * <ol>
 *   <li>NO_FEE：免费</li>
 *   <li>FIXED：固定金额</li>
 *   <li>HOURLY：首时段 + 后续单位时段，支持单日封顶、最大封顶、跨天按自然日封顶、分时段计费</li>
 * </ol>
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>金额使用整数分，禁止浮点数</li>
 *   <li>无生效规则、规则已禁用、配置解析失败、分时段冲突均失败关闭，不猜测费用</li>
 *   <li>计算结果非负；负金额或越界时抛异常</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class BillingEngine {

    private static final Logger log = LoggerFactory.getLogger(BillingEngine.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final int DEFAULT_UNIT_PERIOD_MINUTES = 60;

    private final BillingRuleMapper ruleMapper;
    private final BillingRuleVersionMapper versionMapper;
    private final BillingRuleSwitchLogMapper switchLogMapper;
    private final ObjectMapper objectMapper;

    public BillingEngine(BillingRuleMapper ruleMapper,
                         BillingRuleVersionMapper versionMapper,
                         BillingRuleSwitchLogMapper switchLogMapper,
                         ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.switchLogMapper = switchLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 计算停车费用。
     *
     * @param parkingLotId 停车场 ID
     * @param entryTime    入场时间
     * @param exitTime     出场时间
     * @return 费用（分），非负
     * @throws BusinessException 无生效规则、负金额、时间异常或计费配置冲突
     */
    public int calculateFee(Long parkingLotId, LocalDateTime entryTime, LocalDateTime exitTime) {
        BillingRuleVersion activeVersion = findActiveVersion(parkingLotId);
        if (activeVersion == null) {
            log.warn("停车场无生效收费规则，计费失败: parkingLotId={}", parkingLotId);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场未配置生效的收费规则");
        }
        BillingRule rule = ruleMapper.selectById(activeVersion.getRuleId());
        if (rule == null) {
            log.warn("生效规则对应的规则主记录不存在: ruleId={}", activeVersion.getRuleId());
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "收费规则主记录不存在");
        }
        if (!BillingRule.STATUS_ENABLED.equals(rule.getStatus())) {
            log.warn("收费规则已禁用，计费失败: ruleId={}", activeVersion.getRuleId());
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "当前收费规则已禁用");
        }
        return calculateFee(activeVersion, rule.getRuleType(), entryTime, exitTime);
    }

    /**
     * 基于指定规则版本计算费用（不校验规则启用状态，供测试和重新计算使用）。
     * 若未传入 ruleType，则根据版本关联的规则主记录查询。
     *
     * @param version     收费规则版本
     * @param ruleType    规则类型（可为 null）
     * @param entryTime   入场时间
     * @param exitTime    出场时间
     * @return 费用（分），非负
     */
    public int calculateFee(BillingRuleVersion version, String ruleType, LocalDateTime entryTime, LocalDateTime exitTime) {
        validateTimes(entryTime, exitTime);
        BillingRuleConfig config = parseConfig(version);
        String effectiveRuleType = ruleType != null ? ruleType : resolveRuleType(version);

        int fee = switch (effectiveRuleType) {
            case BillingRule.RULE_TYPE_NO_FEE -> 0;
            case BillingRule.RULE_TYPE_FIXED -> calculateFixed(config);
            case BillingRule.RULE_TYPE_HOURLY -> calculateHourly(config, entryTime, exitTime);
            default -> throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "不支持的规则类型: " + effectiveRuleType);
        };

        int maxAmount = defaultZero(config.getMaxAmount());
        if (maxAmount > 0) {
            fee = Math.min(fee, maxAmount);
        }

        if (fee < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "计算费用为负值: " + fee);
        }

        log.info("计费完成: parkingLotId={} ruleType={} feeCents={}",
                version.getParkingLotId(), ruleType, fee);
        return fee;
    }

    /**
     * 查询停车场当前生效的收费规则版本。
     * <p>
     * 支持 SCHEDULED 生效方式：若当前激活版本的父规则为 SCHEDULED 且 effect_time 尚未到达，
     * 则退回上一个激活版本。
     *
     * @param parkingLotId 停车场 ID
     * @return 生效版本，可能为 null
     */
    public BillingRuleVersion findActiveVersion(Long parkingLotId) {
        BillingRuleVersion active = versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BillingRuleVersion>()
                        .eq(BillingRuleVersion::getParkingLotId, parkingLotId)
                        .eq(BillingRuleVersion::getIsActive, 1));

        if (active == null) {
            return null;
        }

        BillingRule rule = ruleMapper.selectById(active.getRuleId());
        if (rule != null
                && BillingRule.EFFECT_SCHEDULED.equals(rule.getEffectType())
                && rule.getEffectTime() != null
                && rule.getEffectTime().isAfter(LocalDateTime.now())) {
            // SCHEDULED 规则尚未到达生效时间，回退到上一个版本
            BillingRuleVersion previous = findPreviousActiveVersion(parkingLotId, active.getRuleId());
            if (previous != null) {
                log.info("SCHEDULED 规则尚未生效，回退到版本: ruleId={} version={}",
                        previous.getRuleId(), previous.getVersion());
                return previous;
            }
            // 无上一版本（首个规则即 SCHEDULED），不返回生效规则
            log.warn("SCHEDULED 规则尚未生效且无上一版本: parkingLotId={}", parkingLotId);
            return null;
        }

        return active;
    }

    /**
     * 查找该停车场上一激活版本（通过 switch_log 回查 before_rule_id）。
     */
    private BillingRuleVersion findPreviousActiveVersion(Long parkingLotId, Long currentRuleId) {
        BillingRuleSwitchLog lastSwitch = switchLogMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BillingRuleSwitchLog>()
                        .eq(BillingRuleSwitchLog::getParkingLotId, parkingLotId)
                        .eq(BillingRuleSwitchLog::getAfterRuleId, currentRuleId)
                        .orderByDesc(BillingRuleSwitchLog::getCreatedAt)
                        .last("LIMIT 1"));
        if (lastSwitch == null || lastSwitch.getBeforeRuleId() == null) {
            return null;
        }
        BillingRule previousRule = ruleMapper.selectById(lastSwitch.getBeforeRuleId());
        if (previousRule == null) {
            return null;
        }
        return versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BillingRuleVersion>()
                        .eq(BillingRuleVersion::getRuleId, previousRule.getId())
                        .orderByDesc(BillingRuleVersion::getVersion)
                        .last("LIMIT 1"));
    }

    /**
     * 基于规则快照 JSON 计算停车费用。
     * <p>
     * 用于 NEW_ENTRY_ONLY 生效方式，已在场车辆按入场时的规则快照计费。
     *
     * @param ruleSnapshotJson 入场时保存的规则快照 JSON（仅计费必需字段）
     * @param entryTime        入场时间
     * @param exitTime         出场时间
     * @return 费用（分），非负
     */
    public int calculateFeeFromSnapshot(String ruleSnapshotJson, LocalDateTime entryTime, LocalDateTime exitTime) {
        if (ruleSnapshotJson == null || ruleSnapshotJson.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "规则快照为空");
        }
        BillingRuleConfig config;
        try {
            config = objectMapper.readValue(ruleSnapshotJson, BillingRuleConfig.class);
        } catch (Exception e) {
            log.warn("规则快照 JSON 解析失败: {}", ruleSnapshotJson, e);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "规则快照解析失败");
        }
        return calculateByConfig(config, entryTime, exitTime);
    }

    /**
     * 基于 BillingRuleConfig 对象直接计算费用（不查库）。
     */
    public int calculateByConfig(BillingRuleConfig config, LocalDateTime entryTime, LocalDateTime exitTime) {
        validateTimes(entryTime, exitTime);

        int fee = switch (resolveRuleTypeFromConfig(config)) {
            case BillingRule.RULE_TYPE_NO_FEE -> 0;
            case BillingRule.RULE_TYPE_FIXED -> calculateFixed(config);
            case BillingRule.RULE_TYPE_HOURLY -> calculateHourly(config, entryTime, exitTime);
            default -> throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "不支持的规则类型");
        };

        int maxAmount = defaultZero(config.getMaxAmount());
        if (maxAmount > 0) {
            fee = Math.min(fee, maxAmount);
        }

        if (fee < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "计算费用为负值: " + fee);
        }

        return fee;
    }

    /**
     * 从计费配置推断规则类型。
     */
    private String resolveRuleTypeFromConfig(BillingRuleConfig config) {
        if (config.getFixedAmount() != null && config.getFixedAmount() > 0) {
            return BillingRule.RULE_TYPE_FIXED;
        }
        if (config.getFirstAmount() != null && config.getFirstAmount() > 0) {
            return BillingRule.RULE_TYPE_HOURLY;
        }
        if (config.getUnitAmount() != null && config.getUnitAmount() > 0) {
            return BillingRule.RULE_TYPE_HOURLY;
        }
        return BillingRule.RULE_TYPE_NO_FEE;
    }

    /**
     * 构建规则快照 JSON（仅计费必需字段，避免大 JSON）。
     * <p>
     * 用于 NEW_ENTRY_ONLY 生效方式入场时保存。
     */
    public String buildSnapshot(BillingRuleVersion version, String ruleType) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("ruleType", ruleType);
        BillingRuleConfig config = parseConfig(version);
        snapshot.put("freeMinutes", config.getFreeMinutes() != null ? config.getFreeMinutes() : 0);
        snapshot.put("firstPeriod", config.getFirstPeriod() != null ? config.getFirstPeriod() : 0);
        snapshot.put("firstAmount", config.getFirstAmount() != null ? config.getFirstAmount() : 0);
        snapshot.put("unitPeriod", config.getUnitPeriod() != null ? config.getUnitPeriod() : 0);
        snapshot.put("unitAmount", config.getUnitAmount() != null ? config.getUnitAmount() : 0);
        snapshot.put("dailyCap", config.getDailyCap() != null ? config.getDailyCap() : 0);
        snapshot.put("maxAmount", config.getMaxAmount() != null ? config.getMaxAmount() : 0);
        if (config.getFixedAmount() != null) {
            snapshot.put("fixedAmount", config.getFixedAmount());
        }
        if (config.getTimeSegments() != null && !config.getTimeSegments().isEmpty()) {
            snapshot.put("timeSegments", config.getTimeSegments());
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "规则快照序列化失败");
        }
    }

    private String resolveRuleType(BillingRuleVersion version) {
        BillingRule rule = ruleMapper.selectById(version.getRuleId());
        if (rule == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "收费规则主记录不存在");
        }
        return rule.getRuleType();
    }

    private void validateTimes(LocalDateTime entryTime, LocalDateTime exitTime) {
        if (entryTime == null || exitTime == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "入场时间或出场时间为空");
        }
        if (exitTime.isBefore(entryTime)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "出场时间早于入场时间");
        }
    }

    private BillingRuleConfig parseConfig(BillingRuleVersion version) {
        String configJson = version.getConfig();
        if (configJson == null || configJson.isBlank()) {
            // 兼容旧版本：无 JSON 配置时从列字段构造
            BillingRuleConfig fallback = new BillingRuleConfig();
            fallback.setFreeMinutes(version.getFreeMinutes());
            fallback.setFirstPeriod(version.getFirstPeriod());
            fallback.setFirstAmount(version.getFirstAmount());
            fallback.setUnitPeriod(version.getUnitPeriod());
            fallback.setUnitAmount(version.getUnitAmount());
            fallback.setDailyCap(version.getDailyCap());
            fallback.setMaxAmount(version.getMaxAmount());
            return fallback;
        }
        try {
            return objectMapper.readValue(configJson, BillingRuleConfig.class);
        } catch (Exception e) {
            log.warn("计费配置 JSON 解析失败: versionId={}, config={}", version.getId(), configJson, e);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "计费配置解析失败");
        }
    }

    private int calculateFixed(BillingRuleConfig config) {
        int fixed = config.getFixedAmount() != null ? config.getFixedAmount() : 0;
        if (fixed == 0 && config.getFirstAmount() != null) {
            fixed = config.getFirstAmount();
        }
        if (fixed < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "固定金额不能为负");
        }
        return fixed;
    }

    private int calculateHourly(BillingRuleConfig config, LocalDateTime entryTime, LocalDateTime exitTime) {
        long totalMinutes = Duration.between(entryTime, exitTime).toMinutes();
        int freeMinutes = defaultZero(config.getFreeMinutes());
        if (totalMinutes <= freeMinutes) {
            return 0;
        }

        LocalDateTime chargeStart = entryTime.plusMinutes(freeMinutes);
        List<ChargeInterval> intervals = new ArrayList<>();

        int firstPeriod = defaultZero(config.getFirstPeriod());
        int firstAmount = defaultZero(config.getFirstAmount());
        LocalDateTime cursor = chargeStart;

        if (firstPeriod > 0) {
            LocalDateTime firstEnd = min(cursor.plusMinutes(firstPeriod), exitTime);
            intervals.add(new ChargeInterval(cursor, firstEnd, firstAmount));
            cursor = firstEnd;
        }

        int baseUnitPeriod = positiveOrDefault(config.getUnitPeriod(), DEFAULT_UNIT_PERIOD_MINUTES);
        int baseUnitAmount = defaultZero(config.getUnitAmount());

        while (cursor.isBefore(exitTime)) {
            long remainingMinutes = Duration.between(cursor, exitTime).toMinutes();
            if (remainingMinutes <= 0) {
                break;
            }

            LocalTime cursorTime = cursor.toLocalTime();
            int applicableUnitPeriod = baseUnitPeriod;
            int applicableUnitAmount = baseUnitAmount;

            TimeSegmentConfig segment = findApplicableSegment(config.getTimeSegments(), cursorTime);
            if (segment != null) {
                if (segment.getUnitPeriod() != null && segment.getUnitPeriod() > 0) {
                    applicableUnitPeriod = segment.getUnitPeriod();
                }
                if (segment.getUnitAmount() != null) {
                    applicableUnitAmount = segment.getUnitAmount();
                }
            }

            LocalDateTime blockEnd = cursor.plusMinutes(applicableUnitPeriod);
            // 如果处于分时段规则中，把当前块截断到当前时段边界
            if (config.getTimeSegments() != null && !config.getTimeSegments().isEmpty()) {
                LocalDateTime segmentBoundary = nextSegmentBoundary(config.getTimeSegments(), cursor, exitTime);
                blockEnd = min(blockEnd, segmentBoundary);
            }
            blockEnd = min(blockEnd, exitTime);

            intervals.add(new ChargeInterval(cursor, blockEnd, applicableUnitAmount));
            cursor = blockEnd;
        }

        int fee = applyDailyCap(intervals, defaultZero(config.getDailyCap()));
        return fee;
    }

    private TimeSegmentConfig findApplicableSegment(List<TimeSegmentConfig> segments, LocalTime time) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        TimeSegmentConfig matched = null;
        for (TimeSegmentConfig segment : segments) {
            if (isTimeInSegment(segment, time)) {
                if (matched != null) {
                    log.warn("分时段计费规则冲突: time={}, segments=[{}, {}]", time,
                            matched.getStartTime() + "-" + matched.getEndTime(),
                            segment.getStartTime() + "-" + segment.getEndTime());
                    throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "分时段计费规则存在冲突");
                }
                matched = segment;
            }
        }
        return matched;
    }

    private boolean isTimeInSegment(TimeSegmentConfig segment, LocalTime time) {
        LocalTime start = parseTime(segment.getStartTime());
        LocalTime end = parseTime(segment.getEndTime());
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        // 跨午夜时段，如 20:00-08:00
        return !time.isBefore(start) || time.isBefore(end);
    }

    private LocalDateTime nextSegmentBoundary(List<TimeSegmentConfig> segments, LocalDateTime cursor, LocalDateTime exitTime) {
        LocalTime cursorTime = cursor.toLocalTime();
        LocalDate cursorDate = cursor.toLocalDate();
        TimeSegmentConfig current = findApplicableSegment(segments, cursorTime);
        if (current != null) {
            LocalTime end = parseTime(current.getEndTime());
            LocalDateTime boundary = cursorDate.atTime(end);
            if (!boundary.isAfter(cursor)) {
                boundary = boundary.plusDays(1);
            }
            return boundary;
        }
        // 当前不在任何时段，找到下一个时段起点
        LocalDateTime nextStart = null;
        for (TimeSegmentConfig segment : segments) {
            LocalTime start = parseTime(segment.getStartTime());
            LocalDateTime candidate = cursorDate.atTime(start);
            if (!candidate.isAfter(cursor)) {
                candidate = candidate.plusDays(1);
            }
            if (nextStart == null || candidate.isBefore(nextStart)) {
                nextStart = candidate;
            }
        }
        return nextStart != null ? min(nextStart, exitTime) : exitTime;
    }

    private LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "分时段时间格式为空");
        }
        try {
            return LocalTime.parse(time.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "非法分时段时间格式: " + time);
        }
    }

    private int applyDailyCap(List<ChargeInterval> intervals, int dailyCap) {
        if (dailyCap <= 0) {
            return sumIntervalCosts(intervals);
        }
        Map<LocalDate, Long> dayCosts = new HashMap<>();
        for (ChargeInterval interval : intervals) {
            splitAtMidnight(interval, (date, cost) -> dayCosts.merge(date, (long) cost, Long::sum));
        }
        long total = 0;
        for (long dayCost : dayCosts.values()) {
            total += Math.min(dayCost, dailyCap);
        }
        return (int) total;
    }

    private void splitAtMidnight(ChargeInterval interval, DayCostConsumer consumer) {
        LocalDate startDate = interval.start.toLocalDate();
        LocalDate endDate = interval.end.toLocalDate();
        if (startDate.equals(endDate)) {
            consumer.accept(startDate, interval.cost);
            return;
        }
        LocalDateTime midnight = startDate.plusDays(1).atStartOfDay();
        long totalMinutes = Duration.between(interval.start, interval.end).toMinutes();
        if (totalMinutes <= 0) {
            consumer.accept(startDate, interval.cost);
            return;
        }
        long beforeMidnight = Duration.between(interval.start, midnight).toMinutes();
        // 按比例分配，确保两部分之和等于原费用
        long costBefore = (long) interval.cost * beforeMidnight / totalMinutes;
        long costAfter = interval.cost - costBefore;
        consumer.accept(startDate, (int) costBefore);
        consumer.accept(endDate, (int) costAfter);
    }

    private int sumIntervalCosts(List<ChargeInterval> intervals) {
        long total = 0;
        for (ChargeInterval interval : intervals) {
            total += interval.cost;
        }
        return (int) total;
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private int defaultZero(Integer value) {
        return value != null && value > 0 ? value : 0;
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    @FunctionalInterface
    private interface DayCostConsumer {
        void accept(LocalDate date, int cost);
    }

    private record ChargeInterval(LocalDateTime start, LocalDateTime end, int cost) {
    }
}
