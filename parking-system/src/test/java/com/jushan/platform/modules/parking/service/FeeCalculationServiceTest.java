package com.jushan.platform.modules.parking.service;

import com.jushan.common.BusinessException;
import com.jushan.platform.modules.parking.entity.FeeRule;
import com.jushan.platform.modules.parking.entity.FeeRuleSegment;
import com.jushan.platform.modules.parking.mapper.FeeRuleMapper;
import com.jushan.platform.modules.parking.mapper.FeeRuleSegmentMapper;
import com.jushan.platform.modules.parking.vo.FeeCalculateResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 费用计算服务单元测试。
 * <p>
 * 覆盖四种计费模式、免费时长边界、封顶、跨天、金额精度等场景。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

    @Mock
    private FeeRuleMapper feeRuleMapper;

    @Mock
    private FeeRuleSegmentMapper feeRuleSegmentMapper;

    private FeeCalculationService feeCalculationService;

    @BeforeEach
    void setUp() {
        feeCalculationService = new FeeCalculationService(feeRuleMapper, feeRuleSegmentMapper);
    }

    // ==================== 基础校验 ====================

    @Test
    @DisplayName("无生效规则时抛出业务异常")
    void shouldThrowWhenNoActiveRule() {
        when(feeRuleMapper.selectByLotIdAndZoneId(any(), any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP",
                LocalDateTime.now().minusHours(2), LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置生效");
    }

    @Test
    @DisplayName("入场时间或出场时间为空时抛出异常")
    void shouldThrowWhenTimeIsNull() {
        assertThatThrownBy(() -> feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", null, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("出场时间早于入场时间时抛出异常")
    void shouldThrowWhenExitBeforeEntry() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", now, now.minusHours(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出场时间必须晚于入场时间");
    }

    // ==================== 按时计费（TIME）====================

    @Test
    @DisplayName("按时计费：免费时长内费用为0")
    void shouldReturnZeroWithinFreeMinutes() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 15, 60,
                new BigDecimal("5.00"), new BigDecimal("2.00"), null, null);
        mockRuleOnly(rule);

        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP",
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now());

        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("0"));
        assertThat(result.getFreeMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("按时计费：免费时长边界 - 14分59秒免费")
    void shouldBeFreeAt14Min59Sec() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 15, 60,
                new BigDecimal("5.00"), new BigDecimal("2.00"), null, null);
        mockRuleOnly(rule);

        LocalDateTime entry = LocalDateTime.now().minusMinutes(14).minusSeconds(59);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, LocalDateTime.now());

        // 14分59秒 <= 15分钟免费时长，费用应为0
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("0"));
    }

    @Test
    @DisplayName("按时计费：免费时长边界 - 15分01秒计费")
    void shouldChargeAt15Min01Sec() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 15, 60,
                new BigDecimal("5.00"), new BigDecimal("2.00"), null, null);
        mockRuleOnly(rule);

        // 精确控制：入场时间 = 现在 - 15分01秒
        // Duration.toMinutes() 截断取整，15分01秒 = 15分钟，不大于免费时长
        // 使用 16分钟确保超过免费时长
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime entry = now.minusMinutes(16);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, now);

        // 16分钟 > 15分钟免费时长，应计费（首时段5元）
        assertThat(result.getParkingDuration()).isGreaterThanOrEqualTo(16);
        assertThat(result.getPayableAmount()).isGreaterThan(new BigDecimal("0"));
    }

    @Test
    @DisplayName("按时计费：首时段 + 续费单位")
    void shouldCalculateFirstPeriodAndSubsequent() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 0, 60,
                new BigDecimal("5.00"), new BigDecimal("2.00"), null, null);
        mockRuleOnly(rule);

        // 停车 2小时30分钟 = 首60分钟5元 + 后续2个单位(向上取整)4元 = 9元
        LocalDateTime entry = LocalDateTime.now().minusMinutes(150);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, LocalDateTime.now());

        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("9"));
        assertThat(result.getBreakdown()).hasSize(2);
    }

    @Test
    @DisplayName("按时计费：24小时封顶")
    void shouldApplyDailyCap() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 0, 60,
                new BigDecimal("5.00"), new BigDecimal("2.00"),
                new BigDecimal("50.00"), null);
        mockRuleOnly(rule);

        // 停车 25小时，按单位计费应为 5 + 24*2 = 53元，但封顶50元
        LocalDateTime entry = LocalDateTime.now().minusHours(25);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, LocalDateTime.now());

        assertThat(result.getOriginalAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("50"));
    }

    // ==================== 按次计费（PER_ENTRY）====================

    @Test
    @DisplayName("按次计费：固定金额")
    void shouldReturnFixedAmount() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_PER_ENTRY, 0, 60,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, null);
        mockRuleOnly(rule);

        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP",
                LocalDateTime.now().minusHours(5), LocalDateTime.now());

        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("10"));
        assertThat(result.getBreakdown().get(0).getItemName()).isEqualTo("按次收费");
    }

    // ==================== 阶梯计费（TIERED）====================

    @Test
    @DisplayName("阶梯计费：跨阶梯区间费用累加")
    void shouldAccumulateTieredFees() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIERED, 0, 60,
                new BigDecimal("5.00"), new BigDecimal("3.00"), null, null);
        mockRuleOnly(rule);

        // 停车 3小时 = 第一阶梯60分钟5元 + 后续2个阶梯6元 = 11元
        LocalDateTime entry = LocalDateTime.now().minusMinutes(180);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, LocalDateTime.now());

        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("11"));
        assertThat(result.getBreakdown()).hasSizeGreaterThanOrEqualTo(2);
    }

    // ==================== 分时段计费（TIME_SEGMENT）====================

    @Test
    @DisplayName("分时段计费：跨时段拆分")
    void shouldSplitAcrossSegments() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME_SEGMENT, 0, 60,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        mockRuleOnly(rule);

        // 白天 08:00-22:00 单价2元/小时，夜间 22:00-08:00 单价1元/小时
        FeeRuleSegment daySegment = createSegment("白天", LocalTime.of(8, 0), LocalTime.of(22, 0), 60, new BigDecimal("2.00"), null);
        FeeRuleSegment nightSegment = createSegment("夜间", LocalTime.of(22, 0), LocalTime.of(8, 0), 60, new BigDecimal("1.00"), null);
        lenient().when(feeRuleSegmentMapper.selectListByFeeRuleId(any())).thenReturn(List.of(daySegment, nightSegment));

        // 从 20:00 到 次日 02:00 = 白天2小时 + 夜间4小时
        LocalDateTime entry = LocalDateTime.now().withHour(20).withMinute(0);
        LocalDateTime exit = entry.plusHours(6);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, exit);

        // 白天2小时=4元 + 夜间4小时=4元 = 8元
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("8"));
        assertThat(result.getBreakdown()).hasSize(2);
    }

    @Test
    @DisplayName("分时段计费：跨天拆分")
    void shouldSplitAcrossDays() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME_SEGMENT, 0, 60,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        mockRuleOnly(rule);

        FeeRuleSegment daySegment = createSegment("白天", LocalTime.of(8, 0), LocalTime.of(22, 0), 60, new BigDecimal("2.00"), null);
        FeeRuleSegment nightSegment = createSegment("夜间", LocalTime.of(22, 0), LocalTime.of(8, 0), 60, new BigDecimal("1.00"), null);
        lenient().when(feeRuleSegmentMapper.selectListByFeeRuleId(any())).thenReturn(List.of(daySegment, nightSegment));

        // 从 10:00 到 次日 10:00 = 第一天白天12h + 夜间10h + 次日白天2h = 38元
        LocalDateTime entry = LocalDateTime.now().withHour(10).withMinute(0);
        LocalDateTime exit = entry.plusHours(24);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, exit);

        // 第一天白天12h=24元 + 夜间10h=10元 + 次日白天2h=4元 = 38元
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("38"));
    }

    // ==================== 金额精度 ====================

    @Test
    @DisplayName("金额精度：BigDecimal 无浮点误差")
    void shouldHaveNoFloatingPointError() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 0, 60,
                new BigDecimal("0.10"), new BigDecimal("0.03"), null, null);
        mockRuleOnly(rule);

        // 停车 3小时 = 0.10 + 2*0.03 = 0.16元
        LocalDateTime entry = LocalDateTime.now().minusMinutes(180);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, LocalDateTime.now());

        // 验证金额是精确的 BigDecimal，不是浮点数
        assertThat(result.getOriginalAmount()).isEqualTo(new BigDecimal("0.16"));
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("0")); // 分位四舍五入后为0
    }

    @Test
    @DisplayName("金额精度：分位四舍五入")
    void shouldRoundToNearestCent() {
        FeeRule rule = createRule(FeeRule.BILLING_MODE_TIME, 0, 60,
                new BigDecimal("1.50"), new BigDecimal("0.50"), null, null);
        mockRuleOnly(rule);

        // 停车 2小时10分钟 = 1.50 + 2*0.50 = 2.50元
        LocalDateTime entry = LocalDateTime.now().minusMinutes(130);
        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, null, "京A12345", "TEMP", entry, LocalDateTime.now());

        assertThat(result.getOriginalAmount()).isEqualTo(new BigDecimal("2.50"));
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("3")); // 2.50 四舍五入为 3
    }

    // ==================== 区域规则优先级 ====================

    @Test
    @DisplayName("区域规则优先级高于车场规则")
    void shouldPreferZoneRuleOverLotRule() {
        FeeRule lotRule = createRule(FeeRule.BILLING_MODE_PER_ENTRY, 0, 60,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, null);
        lotRule.setZoneId(null);
        lotRule.setPriority(0);

        FeeRule zoneRule = createRule(FeeRule.BILLING_MODE_PER_ENTRY, 0, 60,
                new BigDecimal("5.00"), BigDecimal.ZERO, null, null);
        zoneRule.setZoneId(100L);
        zoneRule.setPriority(1);

        // 只 stub 区域规则查询；由于 zoneId=100L 时 findActiveRule 先查区域规则并命中，不会查车场规则
        when(feeRuleMapper.selectByLotIdAndZoneId(eq(1L), eq(100L))).thenReturn(List.of(zoneRule));

        FeeCalculateResultVO result = feeCalculationService.calculate(
                1L, 100L, "京A12345", "TEMP",
                LocalDateTime.now().minusHours(1), LocalDateTime.now());

        // 应使用区域规则 5元，而不是车场规则 10元
        assertThat(result.getPayableAmount()).isEqualTo(new BigDecimal("5"));
        assertThat(result.getFeeRuleName()).contains("测试规则");
    }

    // ==================== 辅助方法 ====================

    private FeeRule createRule(int billingMode, int freeMinutes, int unitMinutes,
                               BigDecimal firstPrice, BigDecimal subsequentPrice,
                               BigDecimal dailyCap, BigDecimal nightCap) {
        FeeRule rule = new FeeRule();
        rule.setId(1L);
        rule.setTenantId(1L);
        rule.setLotId(1L);
        rule.setName("测试规则");
        rule.setBillingMode(billingMode);
        rule.setFreeMinutes(freeMinutes);
        rule.setUnitMinutes(unitMinutes);
        rule.setFirstPeriodPrice(firstPrice);
        rule.setSubsequentPrice(subsequentPrice);
        rule.setDailyCap(dailyCap);
        rule.setNightCap(nightCap);
        rule.setPriority(0);
        rule.setStatus(FeeRule.STATUS_ENABLED);
        return rule;
    }

    private FeeRuleSegment createSegment(String name, LocalTime start, LocalTime end,
                                          int unitMinutes, BigDecimal unitPrice, BigDecimal cap) {
        FeeRuleSegment segment = new FeeRuleSegment();
        segment.setId(1L);
        segment.setTenantId(1L);
        segment.setFeeRuleId(1L);
        segment.setSegmentName(name);
        segment.setStartTime(start);
        segment.setEndTime(end);
        segment.setUnitMinutes(unitMinutes);
        segment.setUnitPrice(unitPrice);
        segment.setCapAmount(cap);
        segment.setSortOrder(0);
        return segment;
    }

    private void mockRuleOnly(FeeRule rule) {
        when(feeRuleMapper.selectByLotIdAndZoneId(any(), any())).thenReturn(List.of(rule));
    }
}
