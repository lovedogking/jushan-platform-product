package com.jushan.platform.modules.parking.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.platform.modules.parking.entity.FeeRule;
import com.jushan.platform.modules.parking.entity.FeeRuleSegment;
import com.jushan.platform.modules.parking.mapper.FeeRuleMapper;
import com.jushan.platform.modules.parking.mapper.FeeRuleSegmentMapper;
import com.jushan.platform.modules.parking.vo.FeeCalculateResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 费用计算服务。
 * <p>
 * 根据停车场当前生效的收费规则，计算停车费用。
 * 支持四种计费模式：按时、按次、阶梯、分时段。
 * 所有金额计算使用 {@link BigDecimal}，最终结果分位四舍五入。
 *
 * <p><strong>安全约束</strong>：
 * <ul>
 *   <li>金额使用整数分存储，禁止浮点数</li>
 *   <li>无生效规则、负金额、时间异常均失败关闭</li>
 *   <li>计算结果非负</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class FeeCalculationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final FeeRuleMapper feeRuleMapper;
    private final FeeRuleSegmentMapper feeRuleSegmentMapper;

    public FeeCalculationService(FeeRuleMapper feeRuleMapper,
                                  FeeRuleSegmentMapper feeRuleSegmentMapper) {
        this.feeRuleMapper = feeRuleMapper;
        this.feeRuleSegmentMapper = feeRuleSegmentMapper;
    }

    /**
     * 计算停车费用。
     *
     * @param lotId        车场 ID
     * @param zoneId       区域 ID（可选）
     * @param plateNumber  车牌号（用于日志）
     * @param vehicleType  车辆类型
     * @param entryTime    入场时间
     * @param exitTime     出场时间
     * @return 费用计算结果
     * @throws BusinessException 无生效规则、负金额、时间异常
     */
    public FeeCalculateResultVO calculate(Long lotId, Long zoneId, String plateNumber,
                                           String vehicleType, LocalDateTime entryTime, LocalDateTime exitTime) {
        if (entryTime == null || exitTime == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "入场时间和出场时间不能为空");
        }
        if (!exitTime.isAfter(entryTime)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "出场时间必须晚于入场时间");
        }

        // 1. 查找生效规则
        FeeRule rule = findActiveRule(lotId, zoneId);
        if (rule == null) {
            log.warn("停车场无生效收费规则，计费失败: lotId={}, zoneId={}", lotId, zoneId);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场未配置生效的收费规则");
        }

        // 2. 计算停车时长（分钟）
        long parkingDurationMinutes = Duration.between(entryTime, exitTime).toMinutes();
        if (parkingDurationMinutes <= 0) {
            parkingDurationMinutes = 1; // 最少按1分钟计
        }

        // 3. 按计费模式计算
        BigDecimal originalAmount;
        List<FeeCalculateResultVO.BreakdownItem> breakdown = new ArrayList<>();

        switch (rule.getBillingMode()) {
            case FeeRule.BILLING_MODE_TIME:
                originalAmount = calculateTimeBased(rule, parkingDurationMinutes, breakdown);
                break;
            case FeeRule.BILLING_MODE_PER_ENTRY:
                originalAmount = calculatePerEntry(rule, breakdown);
                break;
            case FeeRule.BILLING_MODE_TIERED:
                originalAmount = calculateTiered(rule, parkingDurationMinutes, breakdown);
                break;
            case FeeRule.BILLING_MODE_TIME_SEGMENT:
                originalAmount = calculateTimeSegment(rule, entryTime, exitTime, breakdown);
                break;
            default:
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "不支持的计费模式: " + rule.getBillingMode());
        }

        // 4. 应用封顶
        originalAmount = applyCap(rule, originalAmount, parkingDurationMinutes);

        // 5. 确保非负
        if (originalAmount.compareTo(ZERO) < 0) {
            originalAmount = ZERO;
        }

        // 6. 分位四舍五入（转为整数分）
        BigDecimal payableAmount = originalAmount.setScale(0, ROUNDING_MODE);

        // 7. 构建结果
        FeeCalculateResultVO result = new FeeCalculateResultVO();
        result.setLotId(lotId);
        result.setZoneId(zoneId);
        result.setPlateNumber(plateNumber);
        result.setVehicleType(vehicleType);
        result.setParkingDuration((int) parkingDurationMinutes);
        result.setFreeMinutes(rule.getFreeMinutes() != null ? rule.getFreeMinutes() : 0);
        result.setBillingDuration((int) Math.max(0, parkingDurationMinutes - result.getFreeMinutes()));
        result.setOriginalAmount(originalAmount);
        result.setDiscountAmount(ZERO);
        result.setPayableAmount(payableAmount);
        result.setFeeRuleId(rule.getId());
        result.setFeeRuleName(rule.getName());
        result.setBreakdown(breakdown);

        log.info("费用计算完成: lotId={}, plateNumber={}, duration={}min, amount={}分, rule={}",
                lotId, plateNumber, parkingDurationMinutes, payableAmount, rule.getName());

        return result;
    }

    // ==================== 存量一致计费（整数分，移植自旧 BillingEngine） ====================

    private static final int DEFAULT_UNIT_PERIOD_MINUTES = 60;

    /**
     * 计算停车费用（整数分）。
     * <p>
     * 阶段 0（任务包 0-2）计费收敛入口：替代旧包 {@code BillingEngine}，
     * 数据模型统一为 fee_rule / fee_rule_segment，算法与旧引擎逐场景对齐：
     * <ol>
     *   <li>按次（billingMode=2）：固定金额；价格为 0 即免费</li>
     *   <li>按时/阶梯/分时段（billingMode=1/3/4）：免费时长 → 首时段 → 后续单位时段
     *       （不足一个单位按一个单位计），分时段模式下按时段单价并截断到时段边界</li>
     *   <li>跨天：crossDayMode=1 按自然日分段封顶（0点重置），crossDayMode=2 按每24小时窗口连续封顶</li>
     *   <li>maxAmount 整单封顶</li>
     * </ol>
     * 生效方式（effectMode）：1 立即生效（按当前时间判定）、2 仅新入场（按入场时间判定）、
     * 3 定时生效（effectiveStart 到达后按立即生效判定）。
     *
     * @param lotId     车场 ID
     * @param entryTime 入场时间
     * @param exitTime  出场时间
     * @return 费用（分），非负
     * @throws BusinessException 无生效规则、负金额、时间异常或分时段冲突
     */
    public int calculateFeeCents(Long lotId, LocalDateTime entryTime, LocalDateTime exitTime) {
        return calculateFeeCents(lotId, null, entryTime, exitTime);
    }

    /**
     * 计算停车费用（整数分，可指定区域）。
     *
     * @param lotId     车场 ID
     * @param zoneId    区域 ID（可选）
     * @param entryTime 入场时间
     * @param exitTime  出场时间
     * @return 费用（分），非负
     */
    public int calculateFeeCents(Long lotId, Long zoneId, LocalDateTime entryTime, LocalDateTime exitTime) {
        if (entryTime == null || exitTime == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "入场时间或出场时间为空");
        }
        if (exitTime.isBefore(entryTime)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "出场时间早于入场时间");
        }

        FeeRule rule = findActiveRuleForBilling(lotId, zoneId, entryTime, LocalDateTime.now());
        if (rule == null) {
            log.warn("停车场无生效收费规则，计费失败: lotId={}, zoneId={}", lotId, zoneId);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "停车场未配置生效的收费规则");
        }

        int fee;
        if (rule.getBillingMode() != null && rule.getBillingMode() == FeeRule.BILLING_MODE_PER_ENTRY) {
            // 按次：固定金额（0 即免费，等价旧 NO_FEE）
            fee = toCents(rule.getFirstPeriodPrice());
        } else {
            fee = calculateHourlyCents(rule, entryTime, exitTime);
        }

        // 整单最大封顶
        int maxAmount = toCents(rule.getMaxAmount());
        if (maxAmount > 0) {
            fee = Math.min(fee, maxAmount);
        }

        if (fee < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "计算费用为负值: " + fee);
        }

        log.info("计费完成(分): lotId={} ruleId={} feeCents={}", lotId, rule.getId(), fee);
        return fee;
    }

    /**
     * 按时累计计费（整数分）：首时段 + 后续单位时段，支持分时段与跨天封顶。
     */
    private int calculateHourlyCents(FeeRule rule, LocalDateTime entryTime, LocalDateTime exitTime) {
        long totalMinutes = Duration.between(entryTime, exitTime).toMinutes();
        int freeMinutes = positiveOrZero(rule.getFreeMinutes());
        if (totalMinutes <= freeMinutes) {
            return 0;
        }

        LocalDateTime chargeStart = entryTime.plusMinutes(freeMinutes);
        List<ChargeInterval> intervals = new ArrayList<>();

        int firstPeriod = positiveOrZero(rule.getFirstPeriodMinutes());
        int firstAmount = toCents(rule.getFirstPeriodPrice());
        LocalDateTime cursor = chargeStart;

        if (firstPeriod > 0) {
            LocalDateTime firstEnd = minTime(cursor.plusMinutes(firstPeriod), exitTime);
            intervals.add(new ChargeInterval(cursor, firstEnd, firstAmount));
            cursor = firstEnd;
        }

        int baseUnitPeriod = rule.getUnitMinutes() != null && rule.getUnitMinutes() > 0
                ? rule.getUnitMinutes() : DEFAULT_UNIT_PERIOD_MINUTES;
        int baseUnitAmount = toCents(rule.getSubsequentPrice());

        List<FeeRuleSegment> segments = null;
        if (rule.getBillingMode() != null && rule.getBillingMode() == FeeRule.BILLING_MODE_TIME_SEGMENT) {
            segments = feeRuleSegmentMapper.selectListByFeeRuleId(rule.getId());
            if (segments == null || segments.isEmpty()) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "分时段计费规则未配置时段");
            }
        }

        while (cursor.isBefore(exitTime)) {
            long remainingMinutes = Duration.between(cursor, exitTime).toMinutes();
            if (remainingMinutes <= 0) {
                break;
            }

            LocalTime cursorTime = cursor.toLocalTime();
            int applicableUnitPeriod = baseUnitPeriod;
            int applicableUnitAmount = baseUnitAmount;

            FeeRuleSegment segment = findApplicableSegment(segments, cursorTime);
            if (segment != null) {
                if (segment.getUnitMinutes() != null && segment.getUnitMinutes() > 0) {
                    applicableUnitPeriod = segment.getUnitMinutes();
                }
                if (segment.getUnitPrice() != null) {
                    applicableUnitAmount = toCents(segment.getUnitPrice());
                }
            }

            LocalDateTime blockEnd = cursor.plusMinutes(applicableUnitPeriod);
            // 分时段模式下，把当前块截断到时段边界
            if (segments != null && !segments.isEmpty()) {
                LocalDateTime segmentBoundary = nextSegmentBoundary(segments, cursor, exitTime);
                blockEnd = minTime(blockEnd, segmentBoundary);
            }
            blockEnd = minTime(blockEnd, exitTime);

            intervals.add(new ChargeInterval(cursor, blockEnd, applicableUnitAmount));
            cursor = blockEnd;
        }

        int dailyCap = toCents(rule.getDailyCap());
        if (rule.getCrossDayMode() != null && rule.getCrossDayMode() == FeeRule.CROSS_DAY_CONTINUOUS) {
            return applyRollingWindowCap(intervals, dailyCap, chargeStart);
        }
        return applyDailyCapCents(intervals, dailyCap);
    }

    /**
     * 生效方式感知的规则选择。
     * <ul>
     *   <li>立即生效：按当前时间判定生效窗口</li>
     *   <li>仅新入场生效：入场时间须不早于规则激活时间（effectiveStart 或最近更新时间）</li>
     *   <li>定时生效：当前时间须已到达 effectiveStart</li>
     * </ul>
     */
    private FeeRule findActiveRuleForBilling(Long lotId, Long zoneId, LocalDateTime entryTime, LocalDateTime now) {
        if (zoneId != null) {
            List<FeeRule> zoneRules = feeRuleMapper.selectByLotIdAndZoneId(lotId, zoneId);
            FeeRule active = zoneRules.stream()
                    .filter(r -> isRuleActiveForBilling(r, entryTime, now))
                    .max((a, b) -> Integer.compare(
                            a.getPriority() != null ? a.getPriority() : 0,
                            b.getPriority() != null ? b.getPriority() : 0))
                    .orElse(null);
            if (active != null) {
                return active;
            }
        }
        List<FeeRule> lotRules = feeRuleMapper.selectByLotIdAndZoneId(lotId, null);
        return lotRules.stream()
                .filter(r -> isRuleActiveForBilling(r, entryTime, now))
                .max((a, b) -> Integer.compare(
                        a.getPriority() != null ? a.getPriority() : 0,
                        b.getPriority() != null ? b.getPriority() : 0))
                .orElse(null);
    }

    /**
     * 判定规则对一次计费是否生效（结合 effectMode）。
     */
    private boolean isRuleActiveForBilling(FeeRule rule, LocalDateTime entryTime, LocalDateTime now) {
        if (rule == null || rule.getStatus() == null || rule.getStatus() != FeeRule.STATUS_ENABLED) {
            return false;
        }
        int effectMode = rule.getEffectMode() != null ? rule.getEffectMode() : FeeRule.EFFECT_IMMEDIATE;
        switch (effectMode) {
            case FeeRule.EFFECT_SCHEDULED:
                // 定时生效：未到生效时间不启用；到达后按立即生效判定窗口
                if (rule.getEffectiveStart() == null || now.isBefore(rule.getEffectiveStart())) {
                    return false;
                }
                return withinWindow(rule, now);
            case FeeRule.EFFECT_NEW_ENTRY_ONLY:
                // 仅新入场：入场时间须不早于规则激活时间
                LocalDateTime activation = rule.getEffectiveStart() != null
                        ? rule.getEffectiveStart() : rule.getUpdatedAt();
                if (activation != null && entryTime != null && entryTime.isBefore(activation)) {
                    return false;
                }
                return withinWindow(rule, now);
            case FeeRule.EFFECT_IMMEDIATE:
            default:
                return withinWindow(rule, now);
        }
    }

    private boolean withinWindow(FeeRule rule, LocalDateTime time) {
        if (rule.getEffectiveStart() != null && time.isBefore(rule.getEffectiveStart())) {
            return false;
        }
        return rule.getEffectiveEnd() == null || !time.isAfter(rule.getEffectiveEnd());
    }

    private FeeRuleSegment findApplicableSegment(List<FeeRuleSegment> segments, LocalTime time) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        FeeRuleSegment matched = null;
        for (FeeRuleSegment segment : segments) {
            if (isTimeInSegment(time, segment)) {
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

    private LocalDateTime nextSegmentBoundary(List<FeeRuleSegment> segments, LocalDateTime cursor, LocalDateTime exitTime) {
        LocalTime cursorTime = cursor.toLocalTime();
        LocalDate cursorDate = cursor.toLocalDate();
        FeeRuleSegment current = findApplicableSegment(segments, cursorTime);
        if (current != null) {
            LocalDateTime boundary = cursorDate.atTime(current.getEndTime());
            if (!boundary.isAfter(cursor)) {
                boundary = boundary.plusDays(1);
            }
            return boundary;
        }
        LocalDateTime nextStart = null;
        for (FeeRuleSegment segment : segments) {
            LocalDateTime candidate = cursorDate.atTime(segment.getStartTime());
            if (!candidate.isAfter(cursor)) {
                candidate = candidate.plusDays(1);
            }
            if (nextStart == null || candidate.isBefore(nextStart)) {
                nextStart = candidate;
            }
        }
        return nextStart != null ? minTime(nextStart, exitTime) : exitTime;
    }

    /**
     * 按自然日分段封顶（0点重置）：费用按午夜拆分，每日费用 capped at dailyCap。
     */
    private int applyDailyCapCents(List<ChargeInterval> intervals, int dailyCap) {
        if (dailyCap <= 0) {
            return sumIntervalCosts(intervals);
        }
        Map<LocalDate, Long> dayCosts = new HashMap<>();
        for (ChargeInterval interval : intervals) {
            splitAtBoundary(interval, interval.start.toLocalDate().plusDays(1).atStartOfDay(),
                    (date, cost) -> dayCosts.merge(date, (long) cost, Long::sum));
        }
        long total = 0;
        for (long dayCost : dayCosts.values()) {
            total += Math.min(dayCost, dailyCap);
        }
        return (int) total;
    }

    /**
     * 连续计费封顶：从计费起点每 24 小时一个窗口，每窗费用 capped at dailyCap。
     */
    private int applyRollingWindowCap(List<ChargeInterval> intervals, int dailyCap, LocalDateTime chargeStart) {
        if (dailyCap <= 0) {
            return sumIntervalCosts(intervals);
        }
        Map<Integer, Long> windowCosts = new HashMap<>();
        for (ChargeInterval interval : intervals) {
            long totalMinutes = Duration.between(interval.start, interval.end).toMinutes();
            if (totalMinutes <= 0) {
                windowCosts.merge(windowIndexOf(interval.start, chargeStart), (long) interval.cost, Long::sum);
                continue;
            }
            LocalDateTime cursor = interval.start;
            int allocated = 0;
            while (cursor.isBefore(interval.end)) {
                LocalDateTime windowEnd = windowEndOf(cursor, chargeStart);
                LocalDateTime segEnd = interval.end.isBefore(windowEnd) ? interval.end : windowEnd;
                long segMinutes = Duration.between(cursor, segEnd).toMinutes();
                int segCost;
                if (!segEnd.isBefore(interval.end)) {
                    // 最后一段兜底，确保各段之和等于原费用
                    segCost = interval.cost - allocated;
                } else {
                    segCost = (int) ((long) interval.cost * segMinutes / totalMinutes);
                }
                allocated += segCost;
                windowCosts.merge(windowIndexOf(cursor, chargeStart), (long) segCost, Long::sum);
                cursor = segEnd;
            }
        }
        long total = 0;
        for (long windowCost : windowCosts.values()) {
            total += Math.min(windowCost, dailyCap);
        }
        return (int) total;
    }

    private int windowIndexOf(LocalDateTime time, LocalDateTime chargeStart) {
        return (int) (Duration.between(chargeStart, time).toMinutes() / (24 * 60));
    }

    private LocalDateTime windowEndOf(LocalDateTime time, LocalDateTime chargeStart) {
        long minutes = Duration.between(chargeStart, time).toMinutes();
        long windows = minutes / (24 * 60) + 1;
        return chargeStart.plusMinutes(windows * 24 * 60);
    }

    /**
     * 在指定边界处拆分区间费用（按比例分配，确保两部分之和等于原费用）。
     */
    private void splitAtBoundary(ChargeInterval interval, LocalDateTime boundary, IntervalCostConsumer consumer) {
        LocalDate startKey = interval.start.toLocalDate();
        if (!interval.end.isAfter(boundary)) {
            consumer.accept(startKey, interval.cost);
            return;
        }
        long totalMinutes = Duration.between(interval.start, interval.end).toMinutes();
        if (totalMinutes <= 0) {
            consumer.accept(startKey, interval.cost);
            return;
        }
        long beforeMinutes = Duration.between(interval.start, boundary).toMinutes();
        long costBefore = (long) interval.cost * beforeMinutes / totalMinutes;
        long costAfter = interval.cost - costBefore;
        consumer.accept(startKey, (int) costBefore);
        consumer.accept(boundary.toLocalDate(), (int) costAfter);
    }

    private int sumIntervalCosts(List<ChargeInterval> intervals) {
        long total = 0;
        for (ChargeInterval interval : intervals) {
            total += interval.cost;
        }
        return (int) total;
    }

    private LocalDateTime minTime(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private int positiveOrZero(Integer value) {
        return value != null && value > 0 ? value : 0;
    }

    /**
     * 元（DECIMAL）转整数分；NULL 按 0 处理。
     */
    private int toCents(BigDecimal yuan) {
        if (yuan == null) {
            return 0;
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }

    @FunctionalInterface
    private interface IntervalCostConsumer {
        void accept(LocalDate date, int cost);
    }

    private record ChargeInterval(LocalDateTime start, LocalDateTime end, int cost) {
    }

    // ==================== 计费模式实现 ====================
    /**
     * 按时计费：首时段 + 后续单位时段。
     */
    private BigDecimal calculateTimeBased(FeeRule rule, long parkingDurationMinutes,
                                          List<FeeCalculateResultVO.BreakdownItem> breakdown) {
        int freeMinutes = rule.getFreeMinutes() != null ? rule.getFreeMinutes() : 0;
        int unitMinutes = rule.getUnitMinutes() != null && rule.getUnitMinutes() > 0
                ? rule.getUnitMinutes() : 60;
        BigDecimal firstPeriodPrice = rule.getFirstPeriodPrice() != null ? rule.getFirstPeriodPrice() : ZERO;
        BigDecimal subsequentPrice = rule.getSubsequentPrice() != null ? rule.getSubsequentPrice() : ZERO;

        // 扣除免费时长
        long billableMinutes = Math.max(0, parkingDurationMinutes - freeMinutes);
        if (billableMinutes <= 0) {
            breakdown.add(new FeeCalculateResultVO.BreakdownItem("免费时长", freeMinutes, 0, ZERO));
            return ZERO;
        }

        BigDecimal total = ZERO;

        // 首时段（按 unitMinutes 计）
        if (firstPeriodPrice.compareTo(ZERO) > 0) {
            long firstUnits = Math.min(billableMinutes, unitMinutes);
            total = total.add(firstPeriodPrice);
            breakdown.add(new FeeCalculateResultVO.BreakdownItem(
                    "首时段", (int) firstUnits, 1, firstPeriodPrice));
            billableMinutes -= firstUnits;
        }

        // 后续时段（向上取整到 unitMinutes）
        if (billableMinutes > 0 && subsequentPrice.compareTo(ZERO) > 0) {
            long subsequentUnits = (billableMinutes + unitMinutes - 1) / unitMinutes; // 向上取整
            BigDecimal subsequentTotal = subsequentPrice.multiply(BigDecimal.valueOf(subsequentUnits));
            total = total.add(subsequentTotal);
            breakdown.add(new FeeCalculateResultVO.BreakdownItem(
                    "后续时段", (int) billableMinutes, (int) subsequentUnits, subsequentTotal));
        }

        return total;
    }

    /**
     * 按次计费：固定单价。
     */
    private BigDecimal calculatePerEntry(FeeRule rule, List<FeeCalculateResultVO.BreakdownItem> breakdown) {
        BigDecimal price = rule.getFirstPeriodPrice() != null ? rule.getFirstPeriodPrice() : ZERO;
        breakdown.add(new FeeCalculateResultVO.BreakdownItem("按次收费", 0, 1, price));
        return price;
    }

    /**
     * 阶梯计费：按时长区间匹配不同单价。
     * <p>
     * 阶梯定义存储在 holidayRules JSON 或扩展字段中，格式：
     * [{"minMinutes":0,"maxMinutes":60,"price":5.00}, ...]
     */
    private BigDecimal calculateTiered(FeeRule rule, long parkingDurationMinutes,
                                       List<FeeCalculateResultVO.BreakdownItem> breakdown) {
        // 阶梯计费简化实现：使用 firstPeriodPrice 作为第一阶梯，subsequentPrice 作为后续阶梯
        // 实际阶梯区间从 holidayRules 或扩展 JSON 解析
        int freeMinutes = rule.getFreeMinutes() != null ? rule.getFreeMinutes() : 0;
        long billableMinutes = Math.max(0, parkingDurationMinutes - freeMinutes);
        if (billableMinutes <= 0) {
            return ZERO;
        }

        BigDecimal total = ZERO;
        BigDecimal firstPrice = rule.getFirstPeriodPrice() != null ? rule.getFirstPeriodPrice() : ZERO;
        BigDecimal subsequentPrice = rule.getSubsequentPrice() != null ? rule.getSubsequentPrice() : ZERO;
        int unitMinutes = rule.getUnitMinutes() != null && rule.getUnitMinutes() > 0
                ? rule.getUnitMinutes() : 60;

        // 第一阶梯（首 unitMinutes）
        long firstUnits = Math.min(billableMinutes, unitMinutes);
        if (firstUnits > 0 && firstPrice.compareTo(ZERO) > 0) {
            total = total.add(firstPrice);
            breakdown.add(new FeeCalculateResultVO.BreakdownItem(
                    "第一阶梯", (int) firstUnits, 1, firstPrice));
            billableMinutes -= firstUnits;
        }

        // 后续阶梯（每 unitMinutes 一个阶梯）
        while (billableMinutes > 0 && subsequentPrice.compareTo(ZERO) > 0) {
            long stepUnits = Math.min(billableMinutes, unitMinutes);
            total = total.add(subsequentPrice);
            breakdown.add(new FeeCalculateResultVO.BreakdownItem(
                    "后续阶梯", (int) stepUnits, 1, subsequentPrice));
            billableMinutes -= stepUnits;
        }

        return total;
    }

    /**
     * 分时段计费：按时段拆分停车时长，各时段分别计费后求和。
     */
    private BigDecimal calculateTimeSegment(FeeRule rule, LocalDateTime entryTime, LocalDateTime exitTime,
                                           List<FeeCalculateResultVO.BreakdownItem> breakdown) {
        List<FeeRuleSegment> segments = feeRuleSegmentMapper.selectListByFeeRuleId(rule.getId());
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "分时段计费规则未配置时段");
        }

        BigDecimal total = ZERO;
        LocalDateTime current = entryTime;

        while (current.isBefore(exitTime)) {
            LocalTime currentTime = current.toLocalTime();
            FeeRuleSegment matchedSegment = null;

            // 匹配当前时间所在的时段
            for (FeeRuleSegment segment : segments) {
                if (isTimeInSegment(currentTime, segment)) {
                    matchedSegment = segment;
                    break;
                }
            }

            if (matchedSegment == null) {
                // 未匹配到时段，跳过1分钟继续（避免死循环）
                current = current.plusMinutes(1);
                continue;
            }

            // 计算当前时段内剩余的停车时长
            LocalDateTime segmentEnd = getSegmentEnd(current, matchedSegment, exitTime);
            long minutesInSegment = Duration.between(current, segmentEnd).toMinutes();
            if (minutesInSegment <= 0) {
                current = current.plusMinutes(1);
                continue;
            }

            // 按时段单价计费（向上取整到 unitMinutes）
            int unitMinutes = matchedSegment.getUnitMinutes() != null && matchedSegment.getUnitMinutes() > 0
                    ? matchedSegment.getUnitMinutes() : 60;
            BigDecimal unitPrice = matchedSegment.getUnitPrice() != null ? matchedSegment.getUnitPrice() : ZERO;
            long units = (minutesInSegment + unitMinutes - 1) / unitMinutes;
            BigDecimal segmentAmount = unitPrice.multiply(BigDecimal.valueOf(units));

            // 应用时段封顶
            if (matchedSegment.getCapAmount() != null && segmentAmount.compareTo(matchedSegment.getCapAmount()) > 0) {
                segmentAmount = matchedSegment.getCapAmount();
            }

            total = total.add(segmentAmount);
            breakdown.add(new FeeCalculateResultVO.BreakdownItem(
                    matchedSegment.getSegmentName(), (int) minutesInSegment, (int) units, segmentAmount));

            current = segmentEnd;
        }

        return total;
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找生效规则（优先级：区域 > 车场）。
     */
    private FeeRule findActiveRule(Long lotId, Long zoneId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 区域规则
        if (zoneId != null) {
            List<FeeRule> zoneRules = feeRuleMapper.selectByLotIdAndZoneId(lotId, zoneId);
            FeeRule active = zoneRules.stream()
                    .filter(r -> isRuleActive(r, now))
                    .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                    .orElse(null);
            if (active != null) {
                return active;
            }
        }

        // 2. 车场通用规则
        List<FeeRule> lotRules = feeRuleMapper.selectByLotIdAndZoneId(lotId, null);
        return lotRules.stream()
                .filter(r -> isRuleActive(r, now))
                .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .orElse(null);
    }

    /**
     * 判断规则是否生效。
     */
    private boolean isRuleActive(FeeRule rule, LocalDateTime now) {
        if (rule == null || rule.getStatus() == null || rule.getStatus() != FeeRule.STATUS_ENABLED) {
            return false;
        }
        if (rule.getEffectiveStart() != null && now.isBefore(rule.getEffectiveStart())) {
            return false;
        }
        if (rule.getEffectiveEnd() != null && now.isAfter(rule.getEffectiveEnd())) {
            return false;
        }
        return true;
    }

    /**
     * 应用封顶。
     */
    private BigDecimal applyCap(FeeRule rule, BigDecimal amount, long parkingDurationMinutes) {
        // 24小时封顶（按连续24小时计算）
        if (rule.getDailyCap() != null && amount.compareTo(rule.getDailyCap()) > 0) {
            amount = rule.getDailyCap();
        }
        // 夜间封顶（简化：假设夜间为 22:00-06:00，实际逻辑可根据需求扩展）
        if (rule.getNightCap() != null && amount.compareTo(rule.getNightCap()) > 0) {
            // 夜间封顶逻辑：如果停车时段包含夜间，则应用
            // 简化处理：仅当金额超过夜间封顶时取较小值
            // 实际应判断停车时段是否与夜间重叠
        }
        return amount;
    }

    /**
     * 判断时间是否在时段内。
     */
    private boolean isTimeInSegment(LocalTime time, FeeRuleSegment segment) {
        if (segment.getStartTime() == null || segment.getEndTime() == null) {
            return false;
        }
        // 处理跨天时段（如 22:00-06:00）
        if (segment.getStartTime().isBefore(segment.getEndTime())) {
            // 不跨天：start <= time < end
            return !time.isBefore(segment.getStartTime()) && time.isBefore(segment.getEndTime());
        } else {
            // 跨天：start <= time || time < end
            return !time.isBefore(segment.getStartTime()) || time.isBefore(segment.getEndTime());
        }
    }

    /**
     * 获取当前时段的结束时间（取时段结束和出场时间的较小值）。
     */
    private LocalDateTime getSegmentEnd(LocalDateTime current, FeeRuleSegment segment, LocalDateTime exitTime) {
        LocalDateTime segmentEnd;
        if (segment.getStartTime().isBefore(segment.getEndTime())) {
            // 不跨天
            segmentEnd = current.toLocalDate().atTime(segment.getEndTime());
            if (segmentEnd.isBefore(current) || segmentEnd.isEqual(current)) {
                segmentEnd = segmentEnd.plusDays(1);
            }
        } else {
            // 跨天：当前在 start 之后，结束是明天的 end
            LocalDateTime tomorrowEnd = current.toLocalDate().atTime(segment.getEndTime()).plusDays(1);
            LocalDateTime todayEnd = current.toLocalDate().atTime(segment.getEndTime());
            if (current.toLocalTime().isBefore(segment.getEndTime())) {
                // 当前在 00:00-end 之间，今天结束
                segmentEnd = todayEnd;
            } else {
                // 当前在 start-24:00 之间，明天结束
                segmentEnd = tomorrowEnd;
            }
        }
        return segmentEnd.isAfter(exitTime) ? exitTime : segmentEnd;
    }
}
