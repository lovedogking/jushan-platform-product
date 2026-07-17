package com.jushan.boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.BusinessException;
import com.jushan.system.entity.BillingRule;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.mapper.BillingRuleMapper;
import com.jushan.system.mapper.BillingRuleVersionMapper;
import com.jushan.system.mapper.BillingRuleSwitchLogMapper;
import com.jushan.system.service.BillingEngine;
import com.jushan.system.service.BillingRuleConfig;
import com.jushan.system.service.TimeSegmentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 计费引擎单元测试（P004 依赖 P006 计费能力）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class BillingEngineTest {

    @Mock
    private BillingRuleMapper ruleMapper;

    @Mock
    private BillingRuleVersionMapper versionMapper;

    @Mock
    private BillingRuleSwitchLogMapper switchLogMapper;

    private BillingEngine billingEngine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        billingEngine = new BillingEngine(ruleMapper, versionMapper, switchLogMapper, objectMapper);
    }

    @Test
    @DisplayName("无生效规则时抛出业务异常")
    void shouldThrowWhenNoActiveRule() {
        when(versionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> billingEngine.calculateFee(1L,
                LocalDateTime.now().minusHours(2), LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置生效");
    }

    @Test
    @DisplayName("NO_FEE 规则费用为 0")
    void shouldReturnZeroForNoFeeRule() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_NO_FEE);
        BillingRuleVersion version = version(0, 0, 0, 0, 0, 0, 0);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusHours(5), LocalDateTime.now());

        assertThat(fee).isZero();
    }

    @Test
    @DisplayName("FIXED 规则返回固定金额")
    void shouldReturnFixedAmount() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_FIXED);
        BillingRuleVersion version = version(0, 0, 0, 0, 0, 0, 0);
        version.setFirstAmount(500);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusHours(3), LocalDateTime.now());

        assertThat(fee).isEqualTo(500);
    }

    @Test
    @DisplayName("HOURLY 规则在免费时段内费用为 0")
    void shouldReturnZeroWithinFreeMinutes() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = version(30, 60, 500, 30, 200, 0, 0);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusMinutes(20), LocalDateTime.now());

        assertThat(fee).isZero();
    }

    @Test
    @DisplayName("HOURLY 规则收取首时段 + 后续单位费用")
    void shouldChargeFirstPeriodAndUnits() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = version(15, 60, 500, 30, 200, 0, 0);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        // 入场 2 小时 20 分钟前，免费 15 分钟，计费 125 分钟
        // 首 60 分钟 500 分，剩余 65 分钟按 30 分钟单位向上取整 = 3 单位 = 600 分
        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusMinutes(140), LocalDateTime.now());

        assertThat(fee).isEqualTo(1100);
    }

    @Test
    @DisplayName("HOURLY 规则受最大金额封顶")
    void shouldCapAtMaxAmount() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = version(0, 60, 500, 30, 200, 0, 800);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusHours(10), LocalDateTime.now());

        assertThat(fee).isEqualTo(800);
    }

    @Test
    @DisplayName("跨天停车按自然日单日封顶")
    void shouldApplyDailyCapPerCalendarDay() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = version(0, 60, 500, 60, 300, 1000, 5000);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 11, 14, 0);
        int fee = billingEngine.calculateFee(1L, entry, exit);

        assertThat(fee).isEqualTo(2000);
    }

    @Test
    @DisplayName("分时段计费按所在时段单价计算")
    void shouldApplyTimeSegmentRates() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = versionWithTimeSegments(0, 60, 500, 60, 200,
                new Segment("08:00", "20:00", 300),
                new Segment("20:00", "08:00", 100));
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 10, 21, 0);

        int fee = billingEngine.calculateFee(1L, entry, exit);

        assertThat(fee).isEqualTo(3900);
    }

    @Test
    @DisplayName("分时段规则冲突时失败关闭")
    void shouldFailCloseOnConflictingTimeSegments() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = versionWithTimeSegments(0, 0, 0, 60, 200,
                new Segment("08:00", "20:00", 300),
                new Segment("10:00", "22:00", 100));
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 11, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 10, 12, 0);

        assertThatThrownBy(() -> billingEngine.calculateFee(1L, entry, exit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分时段计费规则存在冲突");
    }

    @Test
    @DisplayName("出场时间早于入场时间时抛异常")
    void shouldRejectExitBeforeEntry() {
        BillingRule rule = rule(BillingRule.RULE_TYPE_HOURLY);
        BillingRuleVersion version = version(0, 60, 500, 30, 200, 0, 0);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        LocalDateTime entry = LocalDateTime.now();
        LocalDateTime exit = entry.minusHours(1);

        assertThatThrownBy(() -> billingEngine.calculateFee(1L, entry, exit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出场时间早于入场时间");
    }

    @Test
    @DisplayName("直接基于版本计算时不校验规则启用状态")
    void shouldCalculateByVersionWithoutRuleCheck() {
        BillingRuleVersion version = version(0, 60, 500, 30, 200, 0, 0);

        LocalDateTime entry = LocalDateTime.of(2026, 7, 12, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 12, 11, 0, 0);
        int fee = billingEngine.calculateFee(version, BillingRule.RULE_TYPE_HOURLY, entry, exit);

        assertThat(fee).isEqualTo(500);
    }

    // ==================== 1-3: Snapshot 快照计费 ====================

    @Test
    @DisplayName("基于规则快照 JSON 计算费用 —— HOURLY 首时段+后续单位")
    void shouldCalculateFromSnapshotHourly() {
        BillingRuleConfig config = config(15, 60, 500, 30, 200, 0, 0);
        String snapshot = toJson(config);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 10, 10, 20);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot, entry, exit);

        assertThat(fee).isEqualTo(1100);
    }

    @Test
    @DisplayName("基于规则快照 JSON 计算费用 —— FIXED 固定金额")
    void shouldCalculateFromSnapshotFixed() {
        BillingRuleConfig config = new BillingRuleConfig();
        config.setFixedAmount(1000);
        String snapshot = toJson(config);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot,
                LocalDateTime.now().minusHours(3), LocalDateTime.now());

        assertThat(fee).isEqualTo(1000);
    }

    @Test
    @DisplayName("基于规则快照 JSON 计算费用 —— 免费时长内为 0")
    void shouldReturnZeroWithinFreeMinutesFromSnapshot() {
        BillingRuleConfig config = config(30, 60, 500, 30, 200, 0, 0);
        String snapshot = toJson(config);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot,
                LocalDateTime.now().minusMinutes(20), LocalDateTime.now());

        assertThat(fee).isZero();
    }

    @Test
    @DisplayName("基于规则快照 JSON —— 跨天单日封顶")
    void shouldApplyDailyCapFromSnapshot() {
        BillingRuleConfig config = config(0, 60, 500, 60, 300, 1000, 5000);
        String snapshot = toJson(config);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 11, 14, 0);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot, entry, exit);

        assertThat(fee).isEqualTo(2000);
    }

    @Test
    @DisplayName("基于规则快照 JSON —— 分时段计费")
    void shouldApplyTimeSegmentsFromSnapshot() {
        BillingRuleConfig config = new BillingRuleConfig();
        config.setFirstPeriod(60);
        config.setFirstAmount(500);
        config.setUnitPeriod(60);
        config.setUnitAmount(200);
        config.setTimeSegments(List.of(
                segment("08:00", "20:00", 300),
                segment("20:00", "08:00", 100)));
        String snapshot = toJson(config);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 10, 21, 0);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot, entry, exit);

        assertThat(fee).isEqualTo(3900);
    }

    @Test
    @DisplayName("快照为空时抛异常")
    void shouldThrowWhenSnapshotIsBlank() {
        assertThatThrownBy(() -> billingEngine.calculateFeeFromSnapshot("",
                LocalDateTime.now().minusHours(1), LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规则快照为空");
    }

    // ==================== 1-3: 生效方式组合场景 ====================

    @Test
    @DisplayName("IMMEDIATE：已在场车辆按新规则计费（现行行为）")
    void shouldUseCurrentRuleForImmediate() {
        BillingRule rule = ruleWithEffect(BillingRule.RULE_TYPE_HOURLY, BillingRule.EFFECT_IMMEDIATE, null);
        BillingRuleVersion version = version(0, 60, 800, 30, 300, 0, 0);
        // 规则变更后，已在场车辆出场按新规则 800 分/首小时计算
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusMinutes(40), LocalDateTime.now());
        assertThat(fee).isEqualTo(800);
    }

    @Test
    @DisplayName("NEW_ENTRY_ONLY：使用入场快照计算，不受新规则影响")
    void shouldUseSnapshotForNewEntryOnly() {
        BillingRuleConfig oldConfig = config(0, 60, 500, 30, 200, 0, 0);
        String snapshot = toJson(oldConfig);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 10, 9, 0);
        // 已在场车辆用旧快照，应为 500 分
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot, entry, exit);
        assertThat(fee).isEqualTo(500);
    }

    @Test
    @DisplayName("NEW_ENTRY_ONLY：新入场车辆使用新规则（无快照）")
    void shouldUseNewRuleForNewEntry() {
        BillingRule rule = ruleWithEffect(BillingRule.RULE_TYPE_HOURLY, BillingRule.EFFECT_NEW_ENTRY_ONLY, null);
        BillingRuleVersion version = version(0, 60, 800, 30, 300, 0, 0);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusMinutes(40), LocalDateTime.now());
        assertThat(fee).isEqualTo(800);
    }

    @Test
    @DisplayName("SCHEDULED 未到生效时间：回退到上一版本")
    void shouldFallbackWhenScheduledNotYetEffective() {
        BillingRule rule = ruleWithEffect(BillingRule.RULE_TYPE_HOURLY, BillingRule.EFFECT_SCHEDULED,
                LocalDateTime.now().plusHours(1));
        BillingRuleVersion version = version(0, 60, 999, 30, 999, 0, 0);
        version.setRuleId(2L);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(2L)).thenReturn(rule);

        // switchLog 返回上一个规则
        when(switchLogMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> billingEngine.calculateFee(1L,
                LocalDateTime.now().minusMinutes(40), LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置生效");
    }

    @Test
    @DisplayName("SCHEDULED 已到生效时间：正常生效")
    void shouldApplyWhenScheduledEffective() {
        BillingRule rule = ruleWithEffect(BillingRule.RULE_TYPE_HOURLY, BillingRule.EFFECT_SCHEDULED,
                LocalDateTime.now().minusHours(1));
        BillingRuleVersion version = version(0, 60, 500, 30, 200, 0, 0);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(ruleMapper.selectById(version.getRuleId())).thenReturn(rule);

        int fee = billingEngine.calculateFee(1L,
                LocalDateTime.now().minusMinutes(40), LocalDateTime.now());
        assertThat(fee).isEqualTo(500);
    }

    // ==================== 1-3: 组合场景 ====================

    @Test
    @DisplayName("组合：HOURLY + 免费时长 + 单日封顶 + 跨天（3天）")
    void shouldHandleMultiDayWithFreeAndDailyCap() {
        BillingRuleConfig config = config(30, 60, 500, 60, 300, 1500, 0);
        String snapshot = toJson(config);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 12, 18, 0);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot, entry, exit);

        assertThat(fee).isEqualTo(4500);
    }

    @Test
    @DisplayName("组合：HOURLY + 分时段 + 单日封顶 + 跨天")
    void shouldHandleSegmentsWithDailyCapAndCrossDay() {
        BillingRuleConfig config = new BillingRuleConfig();
        config.setFreeMinutes(0);
        config.setFirstPeriod(60);
        config.setFirstAmount(500);
        config.setUnitPeriod(60);
        config.setUnitAmount(200);
        config.setDailyCap(2000);
        config.setTimeSegments(List.of(
                segment("08:00", "20:00", 300),
                segment("20:00", "08:00", 100)));
        String snapshot = toJson(config);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 11, 10, 0);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot, entry, exit);

        assertThat(fee).isEqualTo(3400);
    }

    @Test
    @DisplayName("组合：HOURLY + 最大封顶")
    void shouldApplyMaxAmountCap() {
        BillingRuleConfig config = config(0, 60, 500, 30, 200, 0, 800);
        String snapshot = toJson(config);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot,
                LocalDateTime.now().minusHours(10), LocalDateTime.now());
        assertThat(fee).isEqualTo(800);
    }

    @Test
    @DisplayName("组合：FIXED + 最大封顶")
    void shouldApplyMaxAmountToFixed() {
        BillingRuleConfig config = new BillingRuleConfig();
        config.setFixedAmount(1500);
        config.setMaxAmount(1000);
        String snapshot = toJson(config);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot,
                LocalDateTime.now().minusHours(3), LocalDateTime.now());
        assertThat(fee).isEqualTo(1000);
    }

    @Test
    @DisplayName("组合：NO_FEE 始终为 0")
    void shouldReturnZeroForNoFeeSnapshot() {
        BillingRuleConfig config = new BillingRuleConfig();
        String snapshot = toJson(config);
        int fee = billingEngine.calculateFeeFromSnapshot(snapshot,
                LocalDateTime.now().minusHours(10), LocalDateTime.now());
        assertThat(fee).isZero();
    }

    @Test
    @DisplayName("buildSnapshot 构建快照 JSON")
    void shouldBuildValidSnapshotJson() {
        BillingRuleVersion version = version(15, 60, 500, 30, 200, 1000, 5000);
        String snapshot = billingEngine.buildSnapshot(version, BillingRule.RULE_TYPE_HOURLY);
        assertThat(snapshot).isNotBlank();
        assertThat(snapshot).contains("\"freeMinutes\":15");
        assertThat(snapshot).contains("\"firstAmount\":500");
        assertThat(snapshot).contains("\"dailyCap\":1000");

        int fee = billingEngine.calculateFeeFromSnapshot(snapshot,
                LocalDateTime.now().minusHours(2), LocalDateTime.now());
        assertThat(fee).isGreaterThan(0);
    }

    @Test
    @DisplayName("calculateByConfig 直接基于配置对象计算")
    void shouldCalculateByConfigDirectly() {
        BillingRuleConfig config = config(15, 60, 500, 30, 200, 0, 0);
        LocalDateTime entry = LocalDateTime.of(2026, 7, 10, 8, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 7, 10, 10, 20);
        int fee = billingEngine.calculateByConfig(config, entry, exit);
        assertThat(fee).isEqualTo(1100);
    }

    private BillingRule rule(String ruleType) {
        BillingRule rule = new BillingRule();
        rule.setId(1L);
        rule.setRuleType(ruleType);
        rule.setStatus(BillingRule.STATUS_ENABLED);
        return rule;
    }

    private BillingRule ruleWithEffect(String ruleType, String effectType, LocalDateTime effectTime) {
        BillingRule rule = rule(ruleType);
        rule.setEffectType(effectType);
        rule.setEffectTime(effectTime);
        return rule;
    }

    private BillingRuleVersion version(int freeMinutes, int firstPeriod, int firstAmount,
                                        int unitPeriod, int unitAmount, int dailyCap, int maxAmount) {
        BillingRuleVersion version = new BillingRuleVersion();
        version.setId(1L);
        version.setRuleId(1L);
        version.setParkingLotId(1L);
        version.setIsActive(1);
        version.setFreeMinutes(freeMinutes);
        version.setFirstPeriod(firstPeriod);
        version.setFirstAmount(firstAmount);
        version.setUnitPeriod(unitPeriod);
        version.setUnitAmount(unitAmount);
        version.setDailyCap(dailyCap);
        version.setMaxAmount(maxAmount);
        return version;
    }

    private BillingRuleConfig config(int freeMinutes, int firstPeriod, int firstAmount,
                                      int unitPeriod, int unitAmount, int dailyCap, int maxAmount) {
        BillingRuleConfig c = new BillingRuleConfig();
        c.setFreeMinutes(freeMinutes);
        c.setFirstPeriod(firstPeriod);
        c.setFirstAmount(firstAmount);
        c.setUnitPeriod(unitPeriod);
        c.setUnitAmount(unitAmount);
        c.setDailyCap(dailyCap);
        c.setMaxAmount(maxAmount);
        return c;
    }

    private String toJson(BillingRuleConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BillingRuleVersion versionWithTimeSegments(int freeMinutes, int firstPeriod, int firstAmount,
                                                         int unitPeriod, int unitAmount,
                                                         Segment... segments) {
        BillingRuleVersion version = version(freeMinutes, firstPeriod, firstAmount,
                unitPeriod, unitAmount, 0, 0);
        StringBuilder config = new StringBuilder();
        config.append("{");
        config.append("\"freeMinutes\":").append(freeMinutes).append(",");
        config.append("\"firstPeriod\":").append(firstPeriod).append(",");
        config.append("\"firstAmount\":").append(firstAmount).append(",");
        config.append("\"unitPeriod\":").append(unitPeriod).append(",");
        config.append("\"unitAmount\":").append(unitAmount).append(",");
        config.append("\"timeSegments\":[");
        for (int i = 0; i < segments.length; i++) {
            Segment s = segments[i];
            config.append("{\"startTime\":\"").append(s.start()).append("\",");
            config.append("\"endTime\":\"").append(s.end()).append("\",");
            config.append("\"unitAmount\":").append(s.unitAmount()).append("}");
            if (i < segments.length - 1) config.append(",");
        }
        config.append("]}");
        version.setConfig(config.toString());
        return version;
    }

    private record Segment(String start, String end, int unitAmount) {
    }

    private TimeSegmentConfig segment(String startTime, String endTime, int unitAmount) {
        TimeSegmentConfig seg = new TimeSegmentConfig();
        seg.setStartTime(startTime);
        seg.setEndTime(endTime);
        seg.setUnitAmount(unitAmount);
        return seg;
    }
}
