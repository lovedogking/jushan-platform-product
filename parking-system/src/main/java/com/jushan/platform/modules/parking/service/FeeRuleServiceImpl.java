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
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.entity.ParkingLot;
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
public class FeeRuleServiceImpl extends ServiceImpl<FeeRuleMapper, FeeRule> implements FeeRuleService {

    private final FeeRuleSegmentMapper feeRuleSegmentMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final FeeRuleHistoryService feeRuleHistoryService;

    public FeeRuleServiceImpl(FeeRuleSegmentMapper feeRuleSegmentMapper,
                              ParkingLotMapper parkingLotMapper,
                              FeeRuleHistoryService feeRuleHistoryService) {
        this.feeRuleSegmentMapper = feeRuleSegmentMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.feeRuleHistoryService = feeRuleHistoryService;
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

    /**
     * 根据车场 ID 解析有效租户 ID。
     * 平台用户时从车场获取 tenantId，租户用户直接返回自身 tenantId。
     */
    private Long resolveEffectiveTenantId(Long lotId) {
        Long tenantId = resolveTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        ParkingLot lot = parkingLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车场不存在");
        }
        return lot.getTenantId();
    }

    // ==================== 创建规则 ====================

    /**
     * 创建收费规则。
     *
     * @param cmd 创建请求（含时段配置，分时段模式必填）
     * @return 规则 VO
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "创建收费规则")
    public FeeRuleVO create(com.jushan.platform.modules.parking.dto.FeeRuleCreateCmd cmd) {
        Long lotId = cmd.getLotId();
        Long zoneId = cmd.getZoneId();
        String name = cmd.getName();
        Integer billingMode = cmd.getBillingMode();
        Integer freeMinutes = cmd.getFreeMinutes();
        Integer unitMinutes = cmd.getUnitMinutes();
        java.math.BigDecimal firstPeriodPrice = cmd.getFirstPeriodPrice();
        java.math.BigDecimal subsequentPrice = cmd.getSubsequentPrice();
        java.math.BigDecimal dailyCap = cmd.getDailyCap();
        java.math.BigDecimal nightCap = cmd.getNightCap();
        Integer priority = cmd.getPriority();
        Integer status = cmd.getStatus();
        LocalDateTime effectiveStart = cmd.getEffectiveStart();
        LocalDateTime effectiveEnd = cmd.getEffectiveEnd();
        String holidayRules = cmd.getHolidayRules();
        List<FeeRuleSegment> segments = cmd.getTimeSegments();
        Long tenantId = resolveEffectiveTenantId(lotId);

        // 校验生效时间
        if (effectiveStart != null && effectiveEnd != null && !effectiveStart.isBefore(effectiveEnd)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "生效开始时间必须早于生效结束时间");
        }

        // 分时段模式校验时段配置
        if (billingMode != null && billingMode == FeeRule.BILLING_MODE_TIME_SEGMENT) {
            validateSegments(segments);
        }
        validateEffectMode(cmd.getEffectMode(), effectiveStart);
        validateCrossDayMode(cmd.getCrossDayMode());
        validateNonNegativeAmounts(firstPeriodPrice, subsequentPrice, dailyCap, cmd.getMaxAmount(), nightCap);

        FeeRule rule = new FeeRule();
        rule.setTenantId(tenantId);
        rule.setLotId(lotId);
        rule.setZoneId(zoneId);
        rule.setName(name);
        rule.setDescription(cmd.getDescription());
        rule.setBillingMode(billingMode);
        rule.setVehicleType(cmd.getVehicleType());
        rule.setPlateColor(cmd.getPlateColor());
        rule.setFreeMinutes(freeMinutes != null ? freeMinutes : 0);
        rule.setUnitMinutes(unitMinutes != null ? unitMinutes : 60);
        rule.setFirstPeriodMinutes(cmd.getFirstPeriodMinutes() != null ? cmd.getFirstPeriodMinutes() : 0);
        rule.setFirstPeriodPrice(firstPeriodPrice);
        rule.setSubsequentPrice(subsequentPrice);
        rule.setDailyCap(dailyCap);
        rule.setMaxAmount(cmd.getMaxAmount());
        rule.setNightCap(nightCap);
        rule.setCrossDayMode(cmd.getCrossDayMode() != null ? cmd.getCrossDayMode() : FeeRule.CROSS_DAY_NATURAL);
        rule.setEffectMode(cmd.getEffectMode() != null ? cmd.getEffectMode() : FeeRule.EFFECT_IMMEDIATE);
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
     * 更新收费规则（乐观锁，版本号递增）。
     */
    @Transactional(rollbackFor = Exception.class)
    @BusinessLog(module = "收费规则", value = "更新收费规则")
    public FeeRuleVO update(Long ruleId, com.jushan.platform.modules.parking.dto.FeeRuleUpdateCmd cmd) {
        String name = cmd.getName();
        Integer billingMode = cmd.getBillingMode();
        Integer freeMinutes = cmd.getFreeMinutes();
        Integer unitMinutes = cmd.getUnitMinutes();
        java.math.BigDecimal firstPeriodPrice = cmd.getFirstPeriodPrice();
        java.math.BigDecimal subsequentPrice = cmd.getSubsequentPrice();
        java.math.BigDecimal dailyCap = cmd.getDailyCap();
        java.math.BigDecimal nightCap = cmd.getNightCap();
        Integer priority = cmd.getPriority();
        Integer status = cmd.getStatus();
        LocalDateTime effectiveStart = cmd.getEffectiveStart();
        LocalDateTime effectiveEnd = cmd.getEffectiveEnd();
        String holidayRules = cmd.getHolidayRules();
        List<FeeRuleSegment> segments = cmd.getTimeSegments();
        Long tenantId = resolveTenantId();
        FeeRule existing = getAndCheck(ruleId, tenantId);
        // 平台用户从车场获取租户
        if (tenantId == null) {
            tenantId = resolveEffectiveTenantId(existing.getLotId());
        }

        // 保存修改前快照
        feeRuleHistoryService.saveSnapshot(existing);

        // 校验生效时间
        if (effectiveStart != null && effectiveEnd != null && !effectiveStart.isBefore(effectiveEnd)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "生效开始时间必须早于生效结束时间");
        }

        // 分时段模式校验时段配置
        if (billingMode != null && billingMode == FeeRule.BILLING_MODE_TIME_SEGMENT) {
            validateSegments(segments);
        }
        validateEffectMode(cmd.getEffectMode(), effectiveStart);
        validateCrossDayMode(cmd.getCrossDayMode());
        validateNonNegativeAmounts(firstPeriodPrice, subsequentPrice, dailyCap, cmd.getMaxAmount(), nightCap);

        // 乐观锁更新
        LambdaUpdateWrapper<FeeRule> wrapper = new LambdaUpdateWrapper<FeeRule>()
                .set(FeeRule::getName, name)
                .set(FeeRule::getDescription, cmd.getDescription())
                .set(FeeRule::getBillingMode, billingMode)
                .set(FeeRule::getVehicleType, cmd.getVehicleType())
                .set(FeeRule::getPlateColor, cmd.getPlateColor())
                .set(FeeRule::getFreeMinutes, freeMinutes)
                .set(FeeRule::getUnitMinutes, unitMinutes)
                .set(FeeRule::getFirstPeriodMinutes, cmd.getFirstPeriodMinutes())
                .set(FeeRule::getFirstPeriodPrice, firstPeriodPrice)
                .set(FeeRule::getSubsequentPrice, subsequentPrice)
                .set(FeeRule::getDailyCap, dailyCap)
                .set(FeeRule::getMaxAmount, cmd.getMaxAmount())
                .set(FeeRule::getNightCap, nightCap)
                .set(FeeRule::getCrossDayMode, cmd.getCrossDayMode())
                .set(FeeRule::getEffectMode, cmd.getEffectMode())
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
        FeeRule source = getAndCheck(ruleId, tenantId);
        // 平台用户从车场获取租户
        if (tenantId == null) {
            tenantId = resolveEffectiveTenantId(source.getLotId());
        }

        FeeRule copy = new FeeRule();
        copy.setTenantId(tenantId);
        copy.setLotId(source.getLotId());
        copy.setZoneId(source.getZoneId());
        copy.setName(source.getName() + " (复制)");
        copy.setDescription(source.getDescription());
        copy.setBillingMode(source.getBillingMode());
        copy.setVehicleType(source.getVehicleType());
        copy.setPlateColor(source.getPlateColor());
        copy.setFreeMinutes(source.getFreeMinutes());
        copy.setUnitMinutes(source.getUnitMinutes());
        copy.setFirstPeriodMinutes(source.getFirstPeriodMinutes());
        copy.setFirstPeriodPrice(source.getFirstPeriodPrice());
        copy.setSubsequentPrice(source.getSubsequentPrice());
        copy.setDailyCap(source.getDailyCap());
        copy.setMaxAmount(source.getMaxAmount());
        copy.setNightCap(source.getNightCap());
        copy.setCrossDayMode(source.getCrossDayMode());
        copy.setEffectMode(source.getEffectMode());
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

    /**
     * 校验生效方式（ADMIN-010）：定时生效必须提供生效开始时间。
     */
    private void validateEffectMode(Integer effectMode, LocalDateTime effectiveStart) {
        if (effectMode == null) {
            return;
        }
        if (effectMode != FeeRule.EFFECT_IMMEDIATE
                && effectMode != FeeRule.EFFECT_NEW_ENTRY_ONLY
                && effectMode != FeeRule.EFFECT_SCHEDULED) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的生效方式: " + effectMode);
        }
        if (effectMode == FeeRule.EFFECT_SCHEDULED && effectiveStart == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "定时生效必须提供定时生效时间");
        }
    }

    /**
     * 校验跨天计费规则（ADMIN-010）。
     */
    private void validateCrossDayMode(Integer crossDayMode) {
        if (crossDayMode == null) {
            return;
        }
        if (crossDayMode != FeeRule.CROSS_DAY_NATURAL && crossDayMode != FeeRule.CROSS_DAY_CONTINUOUS) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "无效的跨天计费规则: " + crossDayMode);
        }
    }

    /**
     * 校验费率合法性（ADMIN-010：非负数）。
     */
    private void validateNonNegativeAmounts(java.math.BigDecimal... amounts) {
        if (amounts == null) {
            return;
        }
        for (java.math.BigDecimal amount : amounts) {
            if (amount != null && amount.signum() < 0) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "费率不能为负数");
            }
        }
    }

    private FeeRuleVO toVO(FeeRule rule) {
        FeeRuleVO vo = new FeeRuleVO();
        vo.setId(rule.getId());
        vo.setTenantId(rule.getTenantId());
        vo.setLotId(rule.getLotId());
        vo.setZoneId(rule.getZoneId());
        vo.setName(rule.getName());
        vo.setDescription(rule.getDescription());
        vo.setBillingMode(rule.getBillingMode());
        vo.setVehicleType(rule.getVehicleType());
        vo.setPlateColor(rule.getPlateColor());
        vo.setFreeMinutes(rule.getFreeMinutes());
        vo.setUnitMinutes(rule.getUnitMinutes());
        vo.setFirstPeriodMinutes(rule.getFirstPeriodMinutes());
        vo.setFirstPeriodPrice(rule.getFirstPeriodPrice());
        vo.setSubsequentPrice(rule.getSubsequentPrice());
        vo.setDailyCap(rule.getDailyCap());
        vo.setMaxAmount(rule.getMaxAmount());
        vo.setNightCap(rule.getNightCap());
        vo.setCrossDayMode(rule.getCrossDayMode());
        vo.setEffectMode(rule.getEffectMode());
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
