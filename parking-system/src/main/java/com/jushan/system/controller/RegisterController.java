package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.system.dto.RegisterRequest;
import com.jushan.system.service.TenantService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户注册控制器。
 * <p>
 * 提供公开的客户注册接口（无需登录），客户在运营端注册页面提交申请。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/register")
public class RegisterController {

    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    private final TenantService tenantService;

    public RegisterController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * 客户自助注册。
     * <p>
     * 公开接口，无需登录。客户提交企业信息和管理员账号后，
     * 系统创建待审核租户和待审核管理员账号。
     *
     * @param request 注册请求（企业名称 + 联系人 + 手机号 + 密码）
     * @return 成功响应
     */
    @PostMapping
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        tenantService.register(request);
        log.info("客户注册提交成功: company={}, phone={}", request.getCompanyName(), request.getContactPhone());
        return R.ok();
    }
}
