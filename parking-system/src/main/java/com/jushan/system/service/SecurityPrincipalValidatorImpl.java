package com.jushan.system.service;

import com.jushan.framework.auth.SecurityPrincipalValidator;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 安全主体校验实现（系统模块）。
 * <p>
 * 实现 {@link SecurityPrincipalValidator}，在校验时查询数据库确认用户和租户状态。
 * <p>
 * <strong>设计约束</strong>：本实现位于 parking-system 模块，由
 * {@code TenantContextInterceptor}（位于 parking-framework 模块）通过接口调用，
 * 遵循接口倒置原则避免循环依赖。
 * <p>
 * <strong>P0 安全修复（FIX-07）：</strong>
 * 当 MyBatis Mapper 不可用时（无可用的 DataSource）跳过本实现，
 * 此时 TenantContextInterceptor 以 principalValidator=null 安全降级。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
@ConditionalOnBean(SysUserMapper.class)
public class SecurityPrincipalValidatorImpl implements SecurityPrincipalValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityPrincipalValidatorImpl.class);

    private final SysUserMapper sysUserMapper;
    private final TenantMapper tenantMapper;

    public SecurityPrincipalValidatorImpl(SysUserMapper sysUserMapper,
                                          TenantMapper tenantMapper) {
        this.sysUserMapper = sysUserMapper;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public boolean isUserActive(Long userId) {
        try {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                log.warn("安全主体校验：用户不存在 userId={}", userId);
                return false;
            }
            boolean active = "ENABLED".equals(user.getStatus());
            if (!active) {
                log.warn("安全主体校验：用户状态非 ENABLED userId={} status={}",
                        userId, user.getStatus());
            }
            return active;
        } catch (Exception e) {
            log.error("安全主体校验：查询用户状态异常 userId={}", userId, e);
            return false;
        }
    }

    @Override
    public boolean isTenantActive(Long tenantId) {
        try {
            Tenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null) {
                log.warn("安全主体校验：租户不存在 tenantId={}", tenantId);
                return false;
            }
            boolean active = "ENABLED".equals(tenant.getStatus());
            if (!active) {
                log.warn("安全主体校验：租户状态非 ENABLED tenantId={} status={}",
                        tenantId, tenant.getStatus());
            }
            return active;
        } catch (Exception e) {
            log.error("安全主体校验：查询租户状态异常 tenantId={}", tenantId, e);
            return false;
        }
    }
}
