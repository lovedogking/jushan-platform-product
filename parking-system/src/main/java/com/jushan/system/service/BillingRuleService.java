package com.jushan.system.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.dto.CreateBillingRuleRequest;
import com.jushan.system.dto.SwitchBillingRuleRequest;
import com.jushan.system.dto.UpdateBillingRuleRequest;
import com.jushan.system.entity.*;
import com.jushan.system.mapper.*;
import com.jushan.system.vo.BillingRuleVersionVO;
import com.jushan.system.vo.BillingRuleVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收费规则服务。
 * <p>
 * T34｜收费规则与版本 CRUD
 * <p>
 * 负责收费规则的 CRUD、版本管理和切换。
 * 所有操作从当前登录会话推导租户范围，不信任前端传入的 tenantId。
 * <p>
 * <strong>权限差异</strong>：
 * <ul>
 *   <li>创建/修改规则：客户管理员和停车场管理员</li>
 *   <li>切换规则：仅客户管理员和停车场管理员，岗亭人员无权</li>
 *   <li>禁用规则：客户管理员和停车场管理员</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class BillingRuleService {

    private static final Logger log = LoggerFactory.getLogger(BillingRuleService.class);

    /** 规则类型 */
    public static final String RULE_TYPE_HOURLY = "HOURLY";
    public static final String RULE_TYPE_FIXED = "FIXED";
    public static final String RULE_TYPE_NO_FEE = "NO_FEE";

    /** 状态 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private final BillingRuleMapper ruleMapper;
    private final BillingRuleVersionMapper versionMapper;
    private final BillingRuleSwitchLogMapper switchLogMapper;
    private final BillingRuleRecalcLogMapper recalcLogMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingRecordMapper parkingRecordMapper;
    private final ParkingLotScopeResolver scopeResolver;
    private final BillingEngine billingEngine;

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

    public BillingRuleService(BillingRuleMapper ruleMapper,
                              BillingRuleVersionMapper versionMapper,
                              BillingRuleSwitchLogMapper switchLogMapper,
                              BillingRuleRecalcLogMapper recalcLogMapper,
                              ParkingLotMapper parkingLotMapper,
                              ParkingRecordMapper parkingRecordMapper,
                              ParkingLotScopeResolver scopeResolver,
                              BillingEngine billingEngine) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.switchLogMapper = switchLogMapper;
        this.recalcLogMapper = recalcLogMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.parkingRecordMapper = parkingRecordMapper;
        this.scopeResolver = scopeResolver;
        this.billingEngine = billingEngine;
    }

    // ==================== 创建规则 ====================

    /**
     * 创建收费规则（含初始版本）。
     * <p>
     * 仅客户管理员和停车场管理员可操作。
     *
     * @param request 创建请求
     * @return 规则视图
     */
    @Transactional
    public BillingRuleVO create(CreateBillingRuleRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后创建收费规则");
        }
        Long userId = TenantContext.requireUserId();

        // 校验停车场归属和授权
        ParkingLot lot = getParkingLotWithAuth(request.getParkingLotId());

        // 校验规则名称唯一
        LambdaQueryWrapper<BillingRule> nameCheck = new LambdaQueryWrapper<BillingRule>()
                .eq(BillingRule::getParkingLotId, request.getParkingLotId())
                .eq(BillingRule::getName, request.getName().trim());
        if (ruleMapper.selectCount(nameCheck) > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该停车场下已存在同名规则");
        }

        // 校验规则类型
        validateRuleType(request.getRuleType());

        // 创建规则
        BillingRule rule = new BillingRule();
        rule.setTenantId(tenantId);
        rule.setParkingLotId(request.getParkingLotId());
        rule.setName(request.getName().trim());
        rule.setDescription(defaultString(request.getDescription(), ""));
        rule.setRuleType(request.getRuleType().trim());
        rule.setStatus(STATUS_ENABLED);
        rule.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0);
        rule.setEffectType(resolveEffectType(request.getEffectType()));
        rule.setEffectTime(parseEffectTime(request.getEffectType(), request.getEffectTime()));
        rule.setCreatedBy(userId);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);

        // 创建初始版本
        createVersion(rule, request, userId);

        // 如果设为默认，需要取消其他默认
        if (rule.getIsDefault() == 1) {
            clearOtherDefault(tenantId, request.getParkingLotId(), rule.getId());
        }

        log.info("创建收费规则成功: ruleId={}, name={}, parkingLotId={}", rule.getId(), rule.getName(), rule.getParkingLotId());
        return get(rule.getId());
    }

    // ==================== 更新规则 ====================

    /**
     * 更新收费规则配置（创建新版本）。
     * <p>
     * 已生效版本不可直接修改，变更会创建新版本。
     * 版本历史完整保留。
     *
     * @param ruleId  规则 ID
     * @param request 更新请求
     * @return 规则视图
     */
    @Transactional
    public BillingRuleVO update(Long ruleId, UpdateBillingRuleRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后修改收费规则");
        }
        Long userId = TenantContext.requireUserId();

        // 校验规则归属
        BillingRule rule = getRuleWithAuth(ruleId);

        // 校验规则类型
        if (request.getRuleType() != null) {
            validateRuleType(request.getRuleType());
        }

        // 更新规则基本信息（不更新版本相关字段）
        LambdaUpdateWrapper<BillingRule> wrapper = new LambdaUpdateWrapper<BillingRule>()
                .eq(BillingRule::getId, ruleId);

        boolean hasUpdate = false;
        if (request.getName() != null && !request.getName().equals(rule.getName())) {
            // 校验新名称唯一
            LambdaQueryWrapper<BillingRule> nameCheck = new LambdaQueryWrapper<BillingRule>()
                    .eq(BillingRule::getParkingLotId, rule.getParkingLotId())
                    .eq(BillingRule::getName, request.getName().trim())
                    .ne(BillingRule::getId, ruleId);
            if (ruleMapper.selectCount(nameCheck) > 0) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该停车场下已存在同名规则");
            }
            wrapper.set(BillingRule::getName, request.getName().trim());
            hasUpdate = true;
        }
        if (request.getDescription() != null) {
            wrapper.set(BillingRule::getDescription, request.getDescription().trim());
            hasUpdate = true;
        }
        if (request.getRuleType() != null) {
            wrapper.set(BillingRule::getRuleType, request.getRuleType().trim());
            hasUpdate = true;
        }
        if (request.getStatus() != null) {
            validateStatus(request.getStatus());
            wrapper.set(BillingRule::getStatus, request.getStatus().trim());
            hasUpdate = true;
        }
        if (request.getEffectType() != null) {
            validateEffectType(request.getEffectType());
            wrapper.set(BillingRule::getEffectType, request.getEffectType().trim());
            hasUpdate = true;
        }
        if (request.getEffectTime() != null) {
            wrapper.set(BillingRule::getEffectTime, parseEffectTime(
                    request.getEffectType() != null ? request.getEffectType() : rule.getEffectType(),
                    request.getEffectTime()));
            hasUpdate = true;
        }

        if (hasUpdate) {
            wrapper.set(BillingRule::getUpdatedBy, userId);
            wrapper.set(BillingRule::getUpdatedAt, LocalDateTime.now());
            ruleMapper.update(null, wrapper);
        }

        // 检查是否有计费配置变更，如果有则创建新版本
        if (hasBillingConfigChange(rule, request)) {
            CreateBillingRuleRequest versionRequest = new CreateBillingRuleRequest();
            versionRequest.setFreeMinutes(request.getFreeMinutes() != null ? request.getFreeMinutes() : 0);
            versionRequest.setFirstPeriod(request.getFirstPeriod() != null ? request.getFirstPeriod() : 0);
            versionRequest.setFirstAmount(request.getFirstAmount() != null ? request.getFirstAmount() : 0);
            versionRequest.setUnitPeriod(request.getUnitPeriod() != null ? request.getUnitPeriod() : 0);
            versionRequest.setUnitAmount(request.getUnitAmount() != null ? request.getUnitAmount() : 0);
            versionRequest.setDailyCap(request.getDailyCap() != null ? request.getDailyCap() : 0);
            versionRequest.setMaxAmount(request.getMaxAmount() != null ? request.getMaxAmount() : 0);

            // 重新查询最新规则
            rule = ruleMapper.selectById(ruleId);
            createVersion(rule, versionRequest, userId);
            log.info("更新收费规则配置并创建新版本: ruleId={}", ruleId);
        }

        return get(ruleId);
    }

    // ==================== 查询规则 ====================

    /**
     * 分页查询收费规则列表。
     * <p>
     * 租户用户只能查看本租户的规则。
     *
     * @param page        页码
     * @param size        每页大小
     * @param parkingLotId 停车场 ID（可选）
     * @param status      状态筛选（可选）
     * @return 分页结果
     */
    public IPage<BillingRuleVO> list(int page, int size, Long parkingLotId, String status) {
        Long tenantId = resolveTenantId();

        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<BillingRule>()
                .eq(tenantId != null, BillingRule::getTenantId, tenantId)
                .eq(parkingLotId != null, BillingRule::getParkingLotId, parkingLotId)
                .eq(status != null && !status.isBlank(), BillingRule::getStatus, status)
                .orderByDesc(BillingRule::getCreatedAt);

        // 停车场级数据范围
        Set<Long> authorizedIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedIds != null) {
            if (authorizedIds.isEmpty()) {
                IPage<BillingRuleVO> emptyPage = new Page<>(page, size);
                emptyPage.setTotal(0);
                return emptyPage;
            }
            wrapper.in(BillingRule::getParkingLotId, authorizedIds);
        }

        IPage<BillingRule> rulePage = ruleMapper.selectPage(new Page<>(page, size), wrapper);
        return rulePage.convert(this::toVO);
    }

    /**
     * 查询单个规则详情（含版本历史）。
     *
     * @param ruleId 规则 ID
     * @return 规则视图
     */
    public BillingRuleVO get(Long ruleId) {
        getRuleWithAuth(ruleId);
        return toVO(ruleMapper.selectById(ruleId));
    }

    /**
     * 查询规则的版本历史。
     *
     * @param ruleId 规则 ID
     * @return 版本列表
     */
    public List<BillingRuleVersionVO> listVersions(Long ruleId) {
        // 校验规则归属
        getRuleWithAuth(ruleId);

        LambdaQueryWrapper<BillingRuleVersion> wrapper = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getRuleId, ruleId)
                .orderByDesc(BillingRuleVersion::getVersion);

        return versionMapper.selectList(wrapper).stream()
                .map(this::toVersionVO)
                .collect(Collectors.toList());
    }

    // ==================== 切换规则 ====================

    /**
     * 切换停车场当前生效的收费规则。
     * <p>
     * <strong>T34 停止条件</strong>：
     * 仅客户管理员和停车场管理员可执行切换，岗亭人员无权。
     * 切换操作必须填写原因。
     *
     * @param parkingLotId 停车场 ID
     * @param request      切换请求
     */
    @Transactional
    public void switchRule(Long parkingLotId, SwitchBillingRuleRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户需指定租户上下文后切换收费规则");
        }
        Long userId = TenantContext.requireUserId();

        // 校验停车场归属和授权
        ParkingLot lot = getParkingLotWithAuth(parkingLotId);

        // 获取目标规则
        BillingRule targetRule = ruleMapper.selectById(request.getTargetRuleId());
        if (targetRule == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "目标规则不存在");
        }
        // 规则必须在同一停车场
        if (!targetRule.getParkingLotId().equals(parkingLotId)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "目标规则不属于该停车场");
        }
        // 规则必须启用
        if (!STATUS_ENABLED.equals(targetRule.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "目标规则未启用，无法切换");
        }

        // NEW_ENTRY_ONLY：已在场车辆不受新规则影响
        boolean effectiveApplyToExisting = request.getApplyToExisting();
        if (BillingRule.EFFECT_NEW_ENTRY_ONLY.equals(targetRule.getEffectType())) {
            effectiveApplyToExisting = false;
            log.info("NEW_ENTRY_ONLY 生效方式：已在场车辆不受新规则影响");
        }

        // SCHEDULED：不立即激活，仅记录切换日志
        if (BillingRule.EFFECT_SCHEDULED.equals(targetRule.getEffectType())) {
            if (targetRule.getEffectTime() == null) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "SCHEDULED 规则必须设置定时生效时间");
            }
            if (targetRule.getEffectTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "定时生效时间必须在当前时间之后");
            }
            // SCHEDULED：记录切换日志但不立即激活，由 BillingEngine.findActiveVersion 惰性激活
            Long beforeRuleId = getCurrentActiveRuleId(parkingLotId);
            writeSwitchLog(parkingLotId, tenantId, userId, beforeRuleId, request.getTargetRuleId(),
                    false, request.getReason());
            log.info("SCHEDULED 规则切换已记录（尚未激活）: parkingLotId={} targetRuleId={} effectTime={}",
                    parkingLotId, request.getTargetRuleId(), targetRule.getEffectTime());
            return;
        }

        // 获取当前生效规则
        LambdaQueryWrapper<BillingRuleVersion> activeWrapper = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getParkingLotId, parkingLotId)
                .eq(BillingRuleVersion::getIsActive, 1);
        BillingRuleVersion currentActive = versionMapper.selectOne(activeWrapper);
        Long beforeRuleId = currentActive != null ? getRuleIdByVersionId(currentActive.getId()) : null;

        // 查找目标规则的最新版本
        LambdaQueryWrapper<BillingRuleVersion> latestWrapper = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getRuleId, request.getTargetRuleId())
                .orderByDesc(BillingRuleVersion::getVersion)
                .last("LIMIT 1");
        BillingRuleVersion targetVersion = versionMapper.selectOne(latestWrapper);
        if (targetVersion == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "目标规则无有效版本");
        }

        // 取消当前生效状态
        if (currentActive != null) {
            LambdaUpdateWrapper<BillingRuleVersion> deactivateWrapper = new LambdaUpdateWrapper<BillingRuleVersion>()
                    .set(BillingRuleVersion::getIsActive, 0)
                    .eq(BillingRuleVersion::getId, currentActive.getId());
            versionMapper.update(null, deactivateWrapper);
        }

        // 设置新版本为生效
        LambdaUpdateWrapper<BillingRuleVersion> activateWrapper = new LambdaUpdateWrapper<BillingRuleVersion>()
                .set(BillingRuleVersion::getIsActive, 1)
                .eq(BillingRuleVersion::getId, targetVersion.getId());
        versionMapper.update(null, activateWrapper);

        // 写入切换审计日志
        writeSwitchLog(parkingLotId, tenantId, userId, beforeRuleId, request.getTargetRuleId(),
                effectiveApplyToExisting, request.getReason());

        // 若影响已在场车辆，按新规则重新计算费用并记录审计
        if (Boolean.TRUE.equals(effectiveApplyToExisting)) {
            recalcActiveRecords(parkingLotId, tenantId, targetVersion, userId);
        }

        log.info("切换收费规则成功: parkingLotId={}, {} -> {}, applyToExisting={}, reason={}",
                parkingLotId, beforeRuleId, request.getTargetRuleId(),
                effectiveApplyToExisting, request.getReason());
    }

    // ==================== 私有方法 ====================

    /**
     * 创建规则版本。
     */
    private void createVersion(BillingRule rule, CreateBillingRuleRequest request, Long userId) {
        // 获取最新版本号
        LambdaQueryWrapper<BillingRuleVersion> versionQuery = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getRuleId, rule.getId())
                .orderByDesc(BillingRuleVersion::getVersion)
                .last("LIMIT 1");
        BillingRuleVersion lastVersion = versionMapper.selectOne(versionQuery);
        int newVersion = (lastVersion != null ? lastVersion.getVersion() : 0) + 1;

        // 构建配置 JSON
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("freeMinutes", request.getFreeMinutes() != null ? request.getFreeMinutes() : 0);
        configMap.put("firstPeriod", request.getFirstPeriod() != null ? request.getFirstPeriod() : 0);
        configMap.put("firstAmount", request.getFirstAmount() != null ? request.getFirstAmount() : 0);
        configMap.put("unitPeriod", request.getUnitPeriod() != null ? request.getUnitPeriod() : 0);
        configMap.put("unitAmount", request.getUnitAmount() != null ? request.getUnitAmount() : 0);
        configMap.put("dailyCap", request.getDailyCap() != null ? request.getDailyCap() : 0);
        configMap.put("maxAmount", request.getMaxAmount() != null ? request.getMaxAmount() : 0);

        // 构建配置摘要
        String summary = buildConfigSummary(request);

        // 创建版本
        BillingRuleVersion version = new BillingRuleVersion();
        version.setRuleId(rule.getId());
        version.setTenantId(rule.getTenantId());
        version.setParkingLotId(rule.getParkingLotId());
        version.setVersion(newVersion);
        version.setIsActive(newVersion == 1 ? 1 : 0); // 首个版本自动生效
        version.setConfig(JSONUtil.toJsonStr(configMap));
        version.setConfigSummary(summary);
        version.setFreeMinutes(request.getFreeMinutes() != null ? request.getFreeMinutes() : 0);
        version.setFirstPeriod(request.getFirstPeriod() != null ? request.getFirstPeriod() : 0);
        version.setFirstAmount(request.getFirstAmount() != null ? request.getFirstAmount() : 0);
        version.setUnitPeriod(request.getUnitPeriod() != null ? request.getUnitPeriod() : 0);
        version.setUnitAmount(request.getUnitAmount() != null ? request.getUnitAmount() : 0);
        version.setDailyCap(request.getDailyCap() != null ? request.getDailyCap() : 0);
        version.setMaxAmount(request.getMaxAmount() != null ? request.getMaxAmount() : 0);
        version.setCreatedBy(userId);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
    }

    /**
     * 构建配置摘要文本。
     */
    private String buildConfigSummary(CreateBillingRuleRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getFreeMinutes() != null && request.getFreeMinutes() > 0) {
            sb.append("免费").append(request.getFreeMinutes()).append("分钟");
        }
        if (request.getFirstAmount() != null && request.getFirstAmount() > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append("首").append(request.getFirstPeriod()).append("分钟")
                    .append(request.getFirstAmount() / 100.0).append("元");
        }
        if (request.getUnitAmount() != null && request.getUnitAmount() > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append("后每").append(request.getUnitPeriod()).append("分钟")
                    .append(request.getUnitAmount() / 100.0).append("元");
        }
        if (request.getDailyCap() != null && request.getDailyCap() > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append("单日封顶").append(request.getDailyCap() / 100.0).append("元");
        }
        if (request.getMaxAmount() != null && request.getMaxAmount() > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append("最大").append(request.getMaxAmount() / 100.0).append("元");
        }
        if (sb.length() == 0) {
            sb.append("免费");
        }
        return sb.toString();
    }

    /**
     * 检查计费配置是否有变更。
     */
    private boolean hasBillingConfigChange(BillingRule rule, UpdateBillingRuleRequest request) {
        // 获取当前生效版本
        LambdaQueryWrapper<BillingRuleVersion> activeWrapper = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getRuleId, rule.getId())
                .eq(BillingRuleVersion::getIsActive, 1);
        BillingRuleVersion currentVersion = versionMapper.selectOne(activeWrapper);
        if (currentVersion == null) return false;

        return (request.getFreeMinutes() != null && !request.getFreeMinutes().equals(currentVersion.getFreeMinutes()))
                || (request.getFirstPeriod() != null && !request.getFirstPeriod().equals(currentVersion.getFirstPeriod()))
                || (request.getFirstAmount() != null && !request.getFirstAmount().equals(currentVersion.getFirstAmount()))
                || (request.getUnitPeriod() != null && !request.getUnitPeriod().equals(currentVersion.getUnitPeriod()))
                || (request.getUnitAmount() != null && !request.getUnitAmount().equals(currentVersion.getUnitAmount()))
                || (request.getDailyCap() != null && !request.getDailyCap().equals(currentVersion.getDailyCap()))
                || (request.getMaxAmount() != null && !request.getMaxAmount().equals(currentVersion.getMaxAmount()));
    }

    /**
     * 取消其他默认规则。
     */
    private void clearOtherDefault(Long tenantId, Long parkingLotId, Long excludeRuleId) {
        LambdaUpdateWrapper<BillingRule> wrapper = new LambdaUpdateWrapper<BillingRule>()
                .set(BillingRule::getIsDefault, 0)
                .eq(BillingRule::getTenantId, tenantId)
                .eq(BillingRule::getParkingLotId, parkingLotId)
                .ne(BillingRule::getId, excludeRuleId)
                .eq(BillingRule::getIsDefault, 1);
        ruleMapper.update(null, wrapper);
    }

    /**
     * 根据版本 ID 获取规则 ID。
     */
    private Long getRuleIdByVersionId(Long versionId) {
        BillingRuleVersion version = versionMapper.selectById(versionId);
        return version != null ? version.getRuleId() : null;
    }

    /**
     * 查询停车场并校验租户归属 + 停车场级授权。
     */
    private ParkingLot getParkingLotWithAuth(Long parkingLotId) {
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        DataScope.validateTenantMatch(lot.getTenantId(), "停车场");
        scopeResolver.validateAccess(parkingLotId);
        return lot;
    }

    /**
     * 查询规则并校验归属和授权。
     */
    private BillingRule getRuleWithAuth(Long ruleId) {
        BillingRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "规则不存在");
        }
        DataScope.validateTenantMatch(rule.getTenantId(), "收费规则");
        scopeResolver.validateAccess(rule.getParkingLotId());
        return rule;
    }

    /**
     * 写入规则切换审计日志。
     */
    private void writeSwitchLog(Long parkingLotId, Long tenantId, Long userId,
                                Long beforeRuleId, Long afterRuleId,
                                Boolean applyToExisting, String reason) {
        // 获取操作人名称（从 Snapshot 获取 roles 用于日志）
        String operatorInfo = "";
        try {
            TenantContext.Snapshot snapshot = TenantContext.get();
            if (snapshot != null) {
                operatorInfo = snapshot.roles() != null ? snapshot.roles() : "";
            }
        } catch (Exception e) {
            log.warn("无法获取操作人信息", e);
        }

        BillingRuleSwitchLog logEntry = new BillingRuleSwitchLog();
        logEntry.setParkingLotId(parkingLotId);
        logEntry.setTenantId(tenantId);
        logEntry.setOperatorId(userId);
        logEntry.setOperatorName(operatorInfo);
        logEntry.setBeforeRuleId(beforeRuleId);
        logEntry.setAfterRuleId(afterRuleId);
        logEntry.setApplyToExisting(Boolean.TRUE.equals(applyToExisting) ? 1 : 0);
        logEntry.setReason(reason != null ? reason : "");
        logEntry.setCreatedAt(LocalDateTime.now());
        switchLogMapper.insert(logEntry);
    }

    /**
     * 对当前在场车辆按新规则版本重新计算费用并记录审计日志。
     */
    private void recalcActiveRecords(Long parkingLotId, Long tenantId,
                                     BillingRuleVersion newVersion, Long operatorId) {
        List<ParkingRecord> activeRecords = parkingRecordMapper.selectList(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStatus, "PARKING"));
        if (activeRecords.isEmpty()) {
            log.info("规则切换无在场车辆需要重新计算: parkingLotId={}", parkingLotId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (ParkingRecord record : activeRecords) {
            int feeCents;
            try {
                feeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), now);
            } catch (BusinessException e) {
                log.warn("规则切换重新计算费用失败，跳过该记录: recordId={}, reason={}",
                        record.getId(), e.getMessage());
                continue;
            }

            BillingRuleRecalcLog recalcLog = new BillingRuleRecalcLog();
            recalcLog.setTenantId(tenantId);
            recalcLog.setParkingLotId(parkingLotId);
            recalcLog.setParkingRecordId(record.getId());
            recalcLog.setPlateNumber(record.getStandardizedPlate());
            recalcLog.setRuleVersionId(newVersion.getId());
            recalcLog.setFeeCents(feeCents);
            recalcLog.setRecalcTime(now);
            recalcLog.setOperatorId(operatorId);
            recalcLog.setCreatedAt(LocalDateTime.now());
            recalcLogMapper.insert(recalcLog);
        }

        log.info("规则切换重新计算完成: parkingLotId={}, versionId={}, records={}",
                parkingLotId, newVersion.getId(), activeRecords.size());
    }

    /**
     * 校验规则类型。
     */
    private void validateRuleType(String ruleType) {
        if (!RULE_TYPE_HOURLY.equals(ruleType) && !RULE_TYPE_FIXED.equals(ruleType) && !RULE_TYPE_NO_FEE.equals(ruleType)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的规则类型: " + ruleType + "，仅支持 HOURLY / FIXED / NO_FEE");
        }
    }

    /**
     * 校验状态。
     */
    private void validateStatus(String status) {
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的状态: " + status + "，仅支持 ENABLED / DISABLED");
        }
    }

    /**
     * 校验生效方式。
     */
    private void validateEffectType(String effectType) {
        if (effectType == null || effectType.isBlank()) {
            return;
        }
        String trimmed = effectType.trim();
        if (!BillingRule.EFFECT_IMMEDIATE.equals(trimmed)
                && !BillingRule.EFFECT_NEW_ENTRY_ONLY.equals(trimmed)
                && !BillingRule.EFFECT_SCHEDULED.equals(trimmed)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "无效的生效方式: " + trimmed + "，仅支持 IMMEDIATE / NEW_ENTRY_ONLY / SCHEDULED");
        }
    }

    /**
     * 解析生效方式，默认 IMMEDIATE。
     */
    private String resolveEffectType(String effectType) {
        if (effectType == null || effectType.isBlank()) {
            return BillingRule.EFFECT_IMMEDIATE;
        }
        String trimmed = effectType.trim();
        validateEffectType(trimmed);
        return trimmed;
    }

    /**
     * 解析定时生效时间。
     */
    private LocalDateTime parseEffectTime(String effectType, String effectTime) {
        if (effectTime == null || effectTime.isBlank()) {
            if (BillingRule.EFFECT_SCHEDULED.equals(effectType)) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "定时生效方式必须设置生效时间");
            }
            return null;
        }
        try {
            return LocalDateTime.parse(effectTime.trim());
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "生效时间格式错误，请使用 yyyy-MM-ddTHH:mm:ss 格式");
        }
    }

    /**
     * 获取停车场当前生效的规则 ID。
     */
    private Long getCurrentActiveRuleId(Long parkingLotId) {
        LambdaQueryWrapper<BillingRuleVersion> wrapper = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getParkingLotId, parkingLotId)
                .eq(BillingRuleVersion::getIsActive, 1);
        BillingRuleVersion active = versionMapper.selectOne(wrapper);
        if (active != null) {
            return active.getRuleId();
        }
        return null;
    }

    /**
     * BillingRule → BillingRuleVO 转换。
     */
    private BillingRuleVO toVO(BillingRule rule) {
        if (rule == null) return null;

        BillingRuleVO vo = new BillingRuleVO();
        BeanUtil.copyProperties(rule, vo);

        // 获取当前生效版本信息
        LambdaQueryWrapper<BillingRuleVersion> activeWrapper = new LambdaQueryWrapper<BillingRuleVersion>()
                .eq(BillingRuleVersion::getRuleId, rule.getId())
                .eq(BillingRuleVersion::getIsActive, 1);
        BillingRuleVersion activeVersion = versionMapper.selectOne(activeWrapper);
        if (activeVersion != null) {
            vo.setActiveVersionId(activeVersion.getId());
            vo.setActiveVersion(activeVersion.getVersion());
            vo.setConfigSummary(activeVersion.getConfigSummary());
            vo.setFreeMinutes(activeVersion.getFreeMinutes());
            vo.setFirstPeriod(activeVersion.getFirstPeriod());
            vo.setFirstAmount(activeVersion.getFirstAmount());
            vo.setUnitPeriod(activeVersion.getUnitPeriod());
            vo.setUnitAmount(activeVersion.getUnitAmount());
            vo.setDailyCap(activeVersion.getDailyCap());
            vo.setMaxAmount(activeVersion.getMaxAmount());
        }

        // 规则类型描述
        vo.setRuleTypeDesc(getRuleTypeDesc(rule.getRuleType()));
        // 状态描述
        vo.setStatusDesc(getStatusDesc(rule.getStatus()));
        // 生效方式
        vo.setEffectType(rule.getEffectType());
        vo.setEffectTypeDesc(getEffectTypeDesc(rule.getEffectType()));
        if (rule.getEffectTime() != null) {
            vo.setEffectTime(rule.getEffectTime().toString());
        }

        return vo;
    }

    /**
     * BillingRuleVersion → BillingRuleVersionVO 转换。
     */
    private BillingRuleVersionVO toVersionVO(BillingRuleVersion version) {
        if (version == null) return null;

        BillingRuleVersionVO vo = new BillingRuleVersionVO();
        BeanUtil.copyProperties(version, vo);
        return vo;
    }

    /**
     * 获取规则类型描述。
     */
    private String getRuleTypeDesc(String ruleType) {
        return switch (ruleType) {
            case RULE_TYPE_HOURLY -> "按时长收费";
            case RULE_TYPE_FIXED -> "固定金额";
            case RULE_TYPE_NO_FEE -> "免费";
            default -> ruleType;
        };
    }

    /**
     * 获取状态描述。
     */
    private String getStatusDesc(String status) {
        return STATUS_ENABLED.equals(status) ? "启用" : "禁用";
    }

    /**
     * 获取生效方式描述。
     */
    private String getEffectTypeDesc(String effectType) {
        if (effectType == null) return "立即生效";
        return switch (effectType) {
            case BillingRule.EFFECT_IMMEDIATE -> "立即生效";
            case BillingRule.EFFECT_NEW_ENTRY_ONLY -> "仅新入场生效";
            case BillingRule.EFFECT_SCHEDULED -> "定时生效";
            default -> effectType;
        };
    }

    private static String defaultString(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }
}