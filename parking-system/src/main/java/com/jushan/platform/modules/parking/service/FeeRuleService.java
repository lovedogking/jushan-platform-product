package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.modules.parking.entity.FeeRule;
import com.jushan.platform.modules.parking.entity.FeeRuleSegment;
import com.jushan.platform.modules.parking.mapper.FeeRuleMapper;
import com.jushan.platform.modules.parking.mapper.FeeRuleSegmentMapper;
import com.jushan.platform.modules.parking.vo.FeeRuleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 收费规则服务实现。
 * <p>
 * 实现收费规则 CRUD、时段管理、规则复制、生效规则查询。
 * 规则优先级：区域规则 > 车场通用规则 > 平台默认规则。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class FeeRuleService extends ServiceImpl<FeeRuleMapper, FeeRule> {

    private final FeeRuleSegmentMapper feeRuleSegmentMapper;

    public FeeRuleService(FeeRuleSegmentMapper feeRuleSegmentMapper) {
        this.feeRuleSegmentMapper = feeRuleSegmentMapper;
    }

    /**
     * 解析当前租户ID，正确处理平台用户。
     * <p>
     * 平台用户（super_admin、platform_operator）无租户绑定，返回 null。
     * 租户用户返回其 tenantId。
     */
    private Long resolveTenantId() {
        if (TenantContext.isPlatformUser()) {
            return null;
        }
        return TenantContext.requireTenantId();
    }

    // ==================== 创建规则 ====================

    /**
     * 创建收费规则。
     *
     * @param lotId       车场 ID
     * @param zoneId      区域 ID（NULL 表示车场通用）
     * @param name        规则名称
     * @param billingMode 计费模式
     * @param freeMinutes 免费时长
     * @param unitMinutes 计费单位
     * @param firstPeriodPrice 首时段价格
     * @param subsequentPrice  后续单价
     * @param dailyCap    24小时封顶
     * @param nightCap    夜间封顶
     * @param priority    优先级
     * @param status      状态
     * @param effectiveStart 生效开始时间
     * @param effectiveEnd   生效结束时间
     * @param holidayRules   节假日规则 JSON
     * @param segments    时段配置（分时段模式必填）
     * @return 规则 VO
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "创建收费规则")
    public FeeRuleVO create(Long lotId, Long zoneId, String name, Integer billingMode,
                            Integer freeMinutes, Integer unitMinutes,
                            java.math.BigDecimal firstPeriodPrice, java.math.BigDecimal subsequentPrice,
                            java.math.BigDecimal dailyCap, java.math.BigDecimal nightCap,
                            Integer priority, Integer status,
                            LocalDateTime effectiveStart, LocalDateTime effectiveEnd,
                            String holidayRules, List<FeeRuleSegment> segments) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户请通过代操作模式进入目标租户后创建收费规则");
        }

        // 校验生效时间
        if (effectiveStart != null && effectiveEnd != null && !effectiveStart.isBefore(effectiveEnd)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "生效开始时间必须早于生效结束时间");
        }

        // 分时段模式校验时段配置
        if (billingMode != null && billingMode == FeeRule.BILLING_MODE_TIME_SEGMENT) {
            validateSegments(segments);
        }

        FeeRule rule = new FeeRule();
        rule.setTenantId(tenantId);
        rule.setLotId(lotId);
        rule.setZoneId(zoneId);
        rule.setName(name);
        rule.setBillingMode(billingMode);
        rule.setFreeMinutes(freeMinutes != null ? freeMinutes : 0);
        rule.setUnitMinutes(unitMinutes != null ? unitMinutes : 60);
        rule.setFirstPeriodPrice(firstPeriodPrice);
        rule.setSubsequentPrice(subsequentPrice);
        rule.setDailyCap(dailyCap);
        rule.setNightCap(nightCap);
        rule.setPriority(priority != null ? priority : 0);
        rule.setStatus(status != null ? status : FeeRule.STATUS_ENABLED);
        rule.setEffectiveStart(effectiveStart);
        rule.setEffectiveEnd(effectiveEnd);
        rule.setHolidayRules(holidayRules);
        rule.setVersion(0);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(rule);

        // 保存时段配置
        if (segments != null && !segments.isEmpty()) {
            for (FeeRuleSegment segment : segments) {
                segment.setTenantId(tenantId);
                segment.setFeeRuleId(rule.getId());
                feeRuleSegmentMapper.insert(segment);
            }
        }

        log.info("创建收费规则成功: ruleId={}, name={}, billingMode={}", rule.getId(), name, billingMode);
        return toVO(rule);
    }

    // ==================== 更新规则 ====================

    /**
     * 更新收费规则（创建新版本）。
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "更新收费规则")
    public FeeRuleVO update(Long ruleId, String name, Integer billingMode,
                            Integer freeMinutes, Integer unitMinutes,
                            java.math.BigDecimal firstPeriodPrice, java.math.BigDecimal subsequentPrice,
                            java.math.BigDecimal dailyCap, java.math.BigDecimal nightCap,
                            Integer priority, Integer status,
                            LocalDateTime effectiveStart, LocalDateTime effectiveEnd,
                            String holidayRules, List<FeeRuleSegment> segments) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户请通过代操作模式进入目标租户后更新收费规则");
        }
        FeeRule existing = getAndCheck(ruleId, tenantId);

        // 校验生效时间
        if (effectiveStart != null && effectiveEnd != null && !effectiveStart.isBefore(effectiveEnd)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "生效开始时间必须早于生效结束时间");
        }

        // 分时段模式校验时段配置
        if (billingMode != null && billingMode == FeeRule.BILLING_MODE_TIME_SEGMENT) {
            validateSegments(segments);
        }

        // 乐观锁更新
        LambdaUpdateWrapper<FeeRule> wrapper = new LambdaUpdateWrapper<FeeRule>()
                .set(FeeRule::getName, name)
                .set(FeeRule::getBillingMode, billingMode)
                .set(FeeRule::getFreeMinutes, freeMinutes)
                .set(FeeRule::getUnitMinutes, unitMinutes)
                .set(FeeRule::getFirstPeriodPrice, firstPeriodPrice)
                .set(FeeRule::getSubsequentPrice, subsequentPrice)
                .set(FeeRule::getDailyCap, dailyCap)
                .set(FeeRule::getNightCap, nightCap)
                .set(FeeRule::getPriority, priority)
                .set(FeeRule::getStatus, status)
                .set(FeeRule::getEffectiveStart, effectiveStart)
                .set(FeeRule::getEffectiveEnd, effectiveEnd)
                .set(FeeRule::getHolidayRules, holidayRules)
                .set(FeeRule::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1")
                .eq(FeeRule::getId, ruleId)
                .eq(FeeRule::getVersion, existing.getVersion());

        int affected = baseMapper.update(null, wrapper);
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "规则已被修改，请刷新后重试");
        }

        // 更新时段配置：先删除旧时段，再插入新时段
        if (billingMode != null && billingMode == FeeRule.BILLING_MODE_TIME_SEGMENT && segments != null) {
            feeRuleSegmentMapper.delete(
                    new LambdaQueryWrapper<FeeRuleSegment>()
                            .eq(FeeRuleSegment::getFeeRuleId, ruleId));
            for (FeeRuleSegment segment : segments) {
                segment.setTenantId(tenantId);
                segment.setFeeRuleId(ruleId);
                feeRuleSegmentMapper.insert(segment);
            }
        }

        log.info("更新收费规则成功: ruleId={}", ruleId);
        return detail(ruleId);
    }

    // ==================== 删除规则 ====================

    /**
     * 软删除收费规则，级联删除时段配置。
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "删除收费规则")
    public boolean removeById(Long ruleId) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户请通过代操作模式进入目标租户后删除收费规则");
        }
        getAndCheck(ruleId, tenantId);

        // 级联软删除时段
        feeRuleSegmentMapper.delete(
                new LambdaQueryWrapper<FeeRuleSegment>()
                        .eq(FeeRuleSegment::getFeeRuleId, ruleId));

        boolean result = super.removeById(ruleId);
        log.info("删除收费规则成功: ruleId={}", ruleId);
        return result;
    }

    // ==================== 复制规则 ====================

    /**
     * 复制收费规则（含时段配置）。
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "复制收费规则")
    public FeeRuleVO copy(Long ruleId) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户请通过代操作模式进入目标租户后复制收费规则");
        }
        FeeRule source = getAndCheck(ruleId, tenantId);

        FeeRule copy = new FeeRule();
        copy.setTenantId(tenantId);
        copy.setLotId(source.getLotId());
        copy.setZoneId(source.getZoneId());
        copy.setName(source.getName() + " (复制)");
        copy.setBillingMode(source.getBillingMode());
        copy.setFreeMinutes(source.getFreeMinutes());
        copy.setUnitMinutes(source.getUnitMinutes());
        copy.setFirstPeriodPrice(source.getFirstPeriodPrice());
        copy.setSubsequentPrice(source.getSubsequentPrice());
        copy.setDailyCap(source.getDailyCap());
        copy.setNightCap(source.getNightCap());
        copy.setPriority(source.getPriority());
        copy.setStatus(FeeRule.STATUS_ENABLED);
        copy.setEffectiveStart(source.getEffectiveStart());
        copy.setEffectiveEnd(source.getEffectiveEnd());
        copy.setHolidayRules(source.getHolidayRules());
        copy.setVersion(0);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(copy);

        // 复制时段配置
        List<FeeRuleSegment> segments = feeRuleSegmentMapper.selectListByFeeRuleId(ruleId);
        if (segments != null) {
            for (FeeRuleSegment segment : segments) {
                FeeRuleSegment segCopy = new FeeRuleSegment();
                segCopy.setTenantId(tenantId);
                segCopy.setFeeRuleId(copy.getId());
                segCopy.setSegmentName(segment.getSegmentName());
                segCopy.setStartTime(segment.getStartTime());
                segCopy.setEndTime(segment.getEndTime());
                segCopy.setUnitMinutes(segment.getUnitMinutes());
                segCopy.setUnitPrice(segment.getUnitPrice());
                segCopy.setCapAmount(segment.getCapAmount());
                segCopy.setSortOrder(segment.getSortOrder());
                feeRuleSegmentMapper.insert(segCopy);
            }
        }

        log.info("复制收费规则成功: sourceRuleId={}, newRuleId={}", ruleId, copy.getId());
        return toVO(copy);
    }

    // ==================== 查询 ====================

    /**
     * 查询规则详情（含时段列表）。
     */
    public FeeRuleVO detail(Long ruleId) {
        Long tenantId = resolveTenantId();
        FeeRule rule = getAndCheck(ruleId, tenantId);
        return toVO(rule);
    }

    /**
     * 分页查询收费规则列表。
     */
    public IPage<FeeRuleVO> pageList(long current, long size, Long lotId, Long zoneId,
                                      Integer billingMode, Integer status) {
        Long tenantId = resolveTenantId();
        LambdaQueryWrapper<FeeRule> wrapper = new LambdaQueryWrapper<FeeRule>()
                .eq(tenantId != null, FeeRule::getTenantId, tenantId)
                .orderByDesc(FeeRule::getCreatedAt);

        if (lotId != null) {
            wrapper.eq(FeeRule::getLotId, lotId);
        }
        if (zoneId != null) {
            wrapper.eq(FeeRule::getZoneId, zoneId);
        }
        if (billingMode != null) {
            wrapper.eq(FeeRule::getBillingMode, billingMode);
        }
        if (status != null) {
            wrapper.eq(FeeRule::getStatus, status);
        }

        IPage<FeeRule> entityPage = baseMapper.selectPage(new Page<>(current, size), wrapper);
        List<FeeRuleVO> records = entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        Page<FeeRuleVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    /**
     * 按车场查询生效规则列表。
     */
    public List<FeeRuleVO> listActiveByLotId(Long lotId) {
        return baseMapper.selectActiveByLotId(lotId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取生效规则（按优先级：区域 > 车场 > 平台默认）。
     */
    public FeeRuleVO getActiveRule(Long lotId, Long zoneId) {
        // 1. 先查区域规则
        if (zoneId != null) {
            List<FeeRule> zoneRules = baseMapper.selectByLotIdAndZoneId(lotId, zoneId);
            FeeRule active = zoneRules.stream()
                    .filter(this::isActive)
                    .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                    .orElse(null);
            if (active != null) {
                return toVO(active);
            }
        }
        // 2. 再查车场通用规则
        List<FeeRule> lotRules = baseMapper.selectByLotIdAndZoneId(lotId, null);
        FeeRule active = lotRules.stream()
                .filter(this::isActive)
                .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .orElse(null);
        if (active != null) {
            return toVO(active);
        }
        // 3. 无匹配规则
        return null;
    }

    // ==================== 状态管理 ====================

    /**
     * 更新规则状态。
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "更新收费规则状态")
    public void updateStatus(Long ruleId, Integer status) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户请通过代操作模式进入目标租户后变更收费规则状态");
        }
        FeeRule rule = getAndCheck(ruleId, tenantId);

        if (status != FeeRule.STATUS_ENABLED && status != FeeRule.STATUS_DISABLED) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的状态值: " + status);
        }

        if (Objects.equals(rule.getStatus(), status)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "规则已是该状态");
        }

        LambdaUpdateWrapper<FeeRule> wrapper = new LambdaUpdateWrapper<FeeRule>()
                .set(FeeRule::getStatus, status)
                .set(FeeRule::getUpdatedAt, LocalDateTime.now())
                .eq(FeeRule::getId, ruleId)
                .eq(FeeRule::getVersion, rule.getVersion());

        boolean updated = baseMapper.update(null, wrapper) > 0;
        if (!updated) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "规则状态已变更，请刷新后重试");
        }

        log.info("更新收费规则状态成功: ruleId={}, {} -> {}", ruleId, rule.getStatus(), status);
    }

    // ==================== 内部方法 ====================

    private FeeRule getAndCheck(Long ruleId, Long tenantId) {
        FeeRule rule = baseMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "收费规则不存在");
        }
        // 平台用户可访问所有租户数据
        if (tenantId == null) {
            return rule;
        }
        if (!Objects.equals(tenantId, rule.getTenantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问其他租户数据");
        }
        return rule;
    }

    /**
     * 判断规则是否生效。
     */
    private boolean isActive(FeeRule rule) {
        if (rule == null || rule.getStatus() == null || rule.getStatus() != FeeRule.STATUS_ENABLED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (rule.getEffectiveStart() != null && now.isBefore(rule.getEffectiveStart())) {
            return false;
        }
        if (rule.getEffectiveEnd() != null && now.isAfter(rule.getEffectiveEnd())) {
            return false;
        }
        return true;
    }

    /**
     * 校验时段配置不重叠且覆盖全天。
     */
    private void validateSegments(List<FeeRuleSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "分时段模式下时段配置不能为空");
        }

        // 校验时段不重叠
        for (int i = 0; i < segments.size(); i++) {
            for (int j = i + 1; j < segments.size(); j++) {
                FeeRuleSegment a = segments.get(i);
                FeeRuleSegment b = segments.get(j);
                if (a.getStartTime() != null && a.getEndTime() != null
                        && b.getStartTime() != null && b.getEndTime() != null) {
                    // 重叠判断：a.start < b.end && b.start < a.end
                    if (a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime())) {
                        throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                                "时段配置存在重叠: " + a.getSegmentName() + " 与 " + b.getSegmentName());
                    }
                }
            }
        }

        // 校验覆盖 00:00-24:00（可选，建议但不强制）
        // 注：24:00 用 LocalTime.MAX 或 23:59:59 表示
    }

    private FeeRuleVO toVO(FeeRule rule) {
        FeeRuleVO vo = new FeeRuleVO();
        vo.setId(rule.getId());
        vo.setTenantId(rule.getTenantId());
        vo.setLotId(rule.getLotId());
        vo.setZoneId(rule.getZoneId());
        vo.setName(rule.getName());
        vo.setBillingMode(rule.getBillingMode());
        vo.setFreeMinutes(rule.getFreeMinutes());
        vo.setUnitMinutes(rule.getUnitMinutes());
        vo.setFirstPeriodPrice(rule.getFirstPeriodPrice());
        vo.setSubsequentPrice(rule.getSubsequentPrice());
        vo.setDailyCap(rule.getDailyCap());
        vo.setNightCap(rule.getNightCap());
        vo.setPriority(rule.getPriority());
        vo.setStatus(rule.getStatus());
        vo.setEffectiveStart(rule.getEffectiveStart());
        vo.setEffectiveEnd(rule.getEffectiveEnd());
        vo.setHolidayRules(rule.getHolidayRules());
        vo.setVersion(rule.getVersion());
        vo.setCreatedAt(rule.getCreatedAt());
        vo.setUpdatedAt(rule.getUpdatedAt());

        // 查询时段列表
        if (rule.getBillingMode() != null && rule.getBillingMode() == FeeRule.BILLING_MODE_TIME_SEGMENT) {
            List<FeeRuleSegment> segments = feeRuleSegmentMapper.selectListByFeeRuleId(rule.getId());
            if (segments != null) {
                vo.setTimeSegments(segments.stream().map(this::toSegmentVO).collect(Collectors.toList()));
            }
        }

        return vo;
    }

    private com.jushan.platform.modules.parking.vo.FeeRuleSegmentVO toSegmentVO(FeeRuleSegment segment) {
        com.jushan.platform.modules.parking.vo.FeeRuleSegmentVO vo = new com.jushan.platform.modules.parking.vo.FeeRuleSegmentVO();
        vo.setId(segment.getId());
        vo.setFeeRuleId(segment.getFeeRuleId());
        vo.setSegmentName(segment.getSegmentName());
        vo.setStartTime(segment.getStartTime());
        vo.setEndTime(segment.getEndTime());
        vo.setUnitMinutes(segment.getUnitMinutes());
        vo.setUnitPrice(segment.getUnitPrice());
        vo.setCapAmount(segment.getCapAmount());
        vo.setSortOrder(segment.getSortOrder());
        return vo;
    }
}
