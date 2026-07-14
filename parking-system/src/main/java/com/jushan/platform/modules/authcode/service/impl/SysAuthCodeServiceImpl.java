package com.jushan.platform.modules.authcode.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.authcode.dto.BatchGenerateAuthCodeRequest;
import com.jushan.platform.modules.authcode.entity.SysAuthCode;
import com.jushan.platform.modules.authcode.mapper.SysAuthCodeMapper;
import com.jushan.platform.modules.authcode.service.SysAuthCodeService;
import com.jushan.platform.modules.authcode.vo.SysAuthCodeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权码服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class SysAuthCodeServiceImpl implements SysAuthCodeService {

    private static final Logger log = LoggerFactory.getLogger(SysAuthCodeServiceImpl.class);

    /** 授权码字符集（排除易混淆字符 0, O, I, L） */
    private static final String CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** 授权码总长度（不含分隔符） */
    private static final int CODE_LENGTH = 16;

    /** 每组长度 */
    private static final int GROUP_LENGTH = 4;

    /** 单组最多生成数量 */
    private static final int MAX_BATCH_COUNT = 100;

    private final SysAuthCodeMapper authCodeMapper;

    public SysAuthCodeServiceImpl(SysAuthCodeMapper authCodeMapper) {
        this.authCodeMapper = authCodeMapper;
    }

    // ==================== 批量生成 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<SysAuthCodeVO> batchGenerate(BatchGenerateAuthCodeRequest request) {
        // 仅平台管理员可生成授权码
        if (!TenantContext.isPlatformUser()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "仅平台管理员可生成授权码");
        }

        // 校验参数
        validateGenerateRequest(request);

        int count = request.getCount();
        LocalDateTime now = LocalDateTime.now();

        // 生成不重复的授权码
        Set<String> codeSet = generateUniqueCodes(count);

        // 构建实体列表
        List<SysAuthCode> entities = new ArrayList<>(codeSet.size());
        for (String code : codeSet) {
            SysAuthCode entity = new SysAuthCode();
            entity.setCode(code);
            entity.setTenantId(null);
            entity.setMaxParkingCount(request.getMaxParkingCount());
            entity.setValidStart(request.getValidStart());
            entity.setValidEnd(request.getValidEnd());
            entity.setUsedCount(0);
            entity.setMaxUseCount(request.getMaxUseCount());
            entity.setVersionType(request.getVersionType().trim().toUpperCase());
            entity.setStatus(SysAuthCode.STATUS_UNUSED);
            entity.setActivatedBy(null);
            entity.setActivatedAt(null);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }

        // 批量插入
        authCodeMapper.insertBatch(entities);

        log.info("批量生成授权码成功: count={}, operatorId={}", entities.size(), TenantContext.requireUserId());

        // 转换为 VO
        return entities.stream().map(this::toVO).toList();
    }

    // ==================== 查询列表 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public IPage<SysAuthCodeVO> list(int page, int size, Integer status, String code) {
        // 根据用户类型决定数据范围
        Long tenantId;
        Integer effectiveStatus = status;
        if (TenantContext.isPlatformUser()) {
            // 平台管理员查看全部
            tenantId = null;
        } else {
            // 租户管理员仅查看本租户已激活的码
            tenantId = TenantContext.requireTenantId();
            if (effectiveStatus != null && effectiveStatus != SysAuthCode.STATUS_ACTIVATED) {
                // 租户用户只能看到已激活的码，其他状态强制为空
                IPage<SysAuthCodeVO> emptyPage = new Page<>(page, size);
                emptyPage.setTotal(0);
                return emptyPage;
            }
            effectiveStatus = SysAuthCode.STATUS_ACTIVATED;
        }

        IPage<SysAuthCode> entityPage = authCodeMapper.selectPageIgnoreTenant(
                new Page<>(page, size), tenantId, effectiveStatus, normalizeLike(code));
        return entityPage.convert(this::toVO);
    }

    // ==================== 激活 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SysAuthCodeVO activate(String code) {
        // 仅租户管理员可激活
        Long tenantId = TenantContext.requireTenantId();
        Long userId = TenantContext.requireUserId();

        if (code == null || code.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "授权码不能为空");
        }
        String normalizedCode = code.trim().toUpperCase();

        // 查询授权码
        SysAuthCode authCode = authCodeMapper.selectByCodeIgnoreTenant(normalizedCode);
        if (authCode == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "授权码不存在");
        }

        // 校验激活条件
        validateActivateConditions(authCode);

        // 执行条件更新（并发安全）
        int affected = authCodeMapper.activateIgnoreTenant(
                normalizedCode, tenantId, userId, LocalDateTime.now());
        if (affected == 0) {
            // 并发或状态已变更，重新查询后给出明确提示
            SysAuthCode refreshed = authCodeMapper.selectByCodeIgnoreTenant(normalizedCode);
            if (refreshed != null) {
                validateActivateConditions(refreshed);
            }
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码激活失败，请稍后重试");
        }

        log.info("激活授权码成功: code={}, tenantId={}, operatorId={}", normalizedCode, tenantId, userId);

        // 查询最新记录返回
        SysAuthCode activated = authCodeMapper.selectByCodeIgnoreTenant(normalizedCode);
        return toVO(activated);
    }

    // ==================== 禁用 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void disable(Long id) {
        // 仅平台管理员可禁用
        if (!TenantContext.isPlatformUser()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "仅平台管理员可禁用授权码");
        }

        if (id == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "授权码 ID 不能为空");
        }

        SysAuthCode authCode = authCodeMapper.selectByIdIgnoreTenant(id);
        if (authCode == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "授权码不存在");
        }
        if (authCode.getStatus() == SysAuthCode.STATUS_DISABLED) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码已处于禁用状态");
        }

        authCodeMapper.disableIgnoreTenant(id);
        log.info("禁用授权码成功: id={}, operatorId={}", id, TenantContext.requireUserId());
    }

    // ==================== 导出 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SysAuthCodeVO> export(Integer status, String code) {
        // 复用列表查询逻辑，但导出全部（不分页）
        Long tenantId;
        Integer effectiveStatus = status;
        if (TenantContext.isPlatformUser()) {
            tenantId = null;
        } else {
            tenantId = TenantContext.requireTenantId();
            if (effectiveStatus != null && effectiveStatus != SysAuthCode.STATUS_ACTIVATED) {
                return List.of();
            }
            effectiveStatus = SysAuthCode.STATUS_ACTIVATED;
        }

        List<SysAuthCode> records = authCodeMapper.selectListIgnoreTenant(
                tenantId, effectiveStatus, normalizeLike(code));
        return records.stream().map(this::toVO).toList();
    }

    // ==================== 私有方法 ====================

    /**
     * 校验批量生成请求参数。
     */
    private void validateGenerateRequest(BatchGenerateAuthCodeRequest request) {
        if (request.getCount() == null || request.getCount() < 1 || request.getCount() > MAX_BATCH_COUNT) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "生成数量必须在 1-" + MAX_BATCH_COUNT + " 之间");
        }
        if (request.getMaxParkingCount() == null || request.getMaxParkingCount() < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "可开通车场数量至少为 1");
        }
        if (request.getMaxUseCount() == null || request.getMaxUseCount() < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "最大使用次数至少为 1");
        }
        if (request.getValidStart() == null || request.getValidEnd() == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "有效期开始和结束不能为空");
        }
        if (request.getValidEnd().isBefore(request.getValidStart())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "有效期结束不能早于开始");
        }
        String versionType = request.getVersionType();
        if (versionType == null || versionType.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "版本类型不能为空");
        }
        String normalizedVersion = versionType.trim().toUpperCase();
        if (!isValidVersionType(normalizedVersion)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "版本类型仅支持 BASIC / STANDARD / PREMIUM");
        }
    }

    /**
     * 校验激活条件。
     */
    private void validateActivateConditions(SysAuthCode authCode) {
        if (authCode.getStatus() == SysAuthCode.STATUS_DISABLED) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码已被禁用");
        }
        if (authCode.getStatus() == SysAuthCode.STATUS_ACTIVATED) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码已被激活");
        }
        if (authCode.getStatus() == SysAuthCode.STATUS_EXPIRED) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码已过期");
        }
        if (authCode.getStatus() != SysAuthCode.STATUS_UNUSED) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码状态异常，无法激活");
        }

        LocalDate today = LocalDate.now();
        if (authCode.getValidStart() == null || authCode.getValidEnd() == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码有效期信息不完整");
        }
        if (today.isBefore(authCode.getValidStart()) || today.isAfter(authCode.getValidEnd())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码不在有效期内");
        }
        if (authCode.getUsedCount() >= authCode.getMaxUseCount()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "授权码已达到最大使用次数");
        }
    }

    /**
     * 生成指定数量的不重复授权码。
     */
    private Set<String> generateUniqueCodes(int count) {
        Set<String> codes = new HashSet<>(count);
        SecureRandom random = new SecureRandom();
        int maxAttempts = count * 100;
        int attempts = 0;

        while (codes.size() < count && attempts < maxAttempts) {
            attempts++;
            String candidate = generateSingleCode(random);
            if (codes.contains(candidate)) {
                continue;
            }
            // 校验数据库中是否已存在
            if (authCodeMapper.selectByCodeIgnoreTenant(candidate) != null) {
                log.warn("生成授权码与库中已存在冲突: {}", candidate);
                continue;
            }
            codes.add(candidate);
        }

        if (codes.size() < count) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "无法生成足够数量的唯一授权码，请减少生成数量后重试");
        }

        return codes;
    }

    /**
     * 生成单个授权码。
     *
     * <p>格式：XXXX-XXXX-XXXX-XXXX（16 位字母数字，大写，4 位一组）</p>
     *
     * @param random 安全随机数生成器
     * @return 授权码
     */
    private String generateSingleCode(SecureRandom random) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
        }

        String raw = sb.toString();
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && i % GROUP_LENGTH == 0) {
                formatted.append("-");
            }
            formatted.append(raw.charAt(i));
        }
        return formatted.toString();
    }

    /**
     * 校验版本类型是否有效。
     */
    private boolean isValidVersionType(String versionType) {
        return SysAuthCode.VERSION_TYPE_BASIC.equals(versionType)
                || SysAuthCode.VERSION_TYPE_STANDARD.equals(versionType)
                || SysAuthCode.VERSION_TYPE_PREMIUM.equals(versionType);
    }

    /**
     * 规范化模糊查询字符串。
     */
    private String normalizeLike(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase().replace("-", "");
    }

    /**
     * 实体转视图对象。
     */
    private SysAuthCodeVO toVO(SysAuthCode entity) {
        if (entity == null) {
            return null;
        }
        SysAuthCodeVO vo = new SysAuthCodeVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setTenantId(entity.getTenantId());
        vo.setMaxParkingCount(entity.getMaxParkingCount());
        vo.setValidStart(entity.getValidStart());
        vo.setValidEnd(entity.getValidEnd());
        vo.setUsedCount(entity.getUsedCount());
        vo.setMaxUseCount(entity.getMaxUseCount());
        vo.setVersionType(entity.getVersionType());
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(getStatusDesc(entity.getStatus()));
        vo.setVersionTypeDesc(getVersionTypeDesc(entity.getVersionType()));
        vo.setActivatedBy(entity.getActivatedBy());
        vo.setActivatedAt(entity.getActivatedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 获取状态描述。
     */
    private String getStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case SysAuthCode.STATUS_UNUSED -> "未使用";
            case SysAuthCode.STATUS_ACTIVATED -> "已激活";
            case SysAuthCode.STATUS_EXPIRED -> "已过期";
            case SysAuthCode.STATUS_DISABLED -> "已禁用";
            default -> "未知";
        };
    }

    /**
     * 获取版本类型描述。
     */
    private String getVersionTypeDesc(String versionType) {
        if (versionType == null) {
            return "未知";
        }
        return switch (versionType) {
            case SysAuthCode.VERSION_TYPE_BASIC -> "基础版";
            case SysAuthCode.VERSION_TYPE_STANDARD -> "标准版";
            case SysAuthCode.VERSION_TYPE_PREMIUM -> "高级版";
            default -> versionType;
        };
    }
}
