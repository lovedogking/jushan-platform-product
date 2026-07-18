package com.jushan.platform.infra.security;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 首次登录强制改密拦截器。
 * <p>
 * 当账号标记 must_change_password=1 时，仅允许访问改密接口和退出接口，
 * 其他所有 API 请求均被拦截。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MustChangePasswordInterceptor.class);

    /** 允许在强制改密期间访问的路径前缀 */
    private static final String[] ALLOWED_PREFIXES = {
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/userinfo",
            "/api/v1/auth/session",
    };

    private final SysAdminAccountMapper adminAccountMapper;

    public MustChangePasswordInterceptor(SysAdminAccountMapper adminAccountMapper) {
        this.adminAccountMapper = adminAccountMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return true; // let authentication interceptor handle
        }

        String path = request.getRequestURI();
        for (String allowed : ALLOWED_PREFIXES) {
            if (path.startsWith(allowed)) {
                return true;
            }
        }

        // Use selectByIdIgnoreTenant to bypass tenant filtering in interceptor context
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account != null && account.getMustChangePassword() != null
                && account.getMustChangePassword() == 1) {
            log.warn("拦截强制改密用户的请求: userId={}, path={}", userId, path);
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "首次登录须修改密码，请前往修改密码页面");
        }

        return true;
    }
}
