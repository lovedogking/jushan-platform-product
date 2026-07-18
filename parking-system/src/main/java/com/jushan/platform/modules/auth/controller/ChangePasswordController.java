package com.jushan.platform.modules.auth.controller;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 修改密码控制器。
 * <p>
 * 提供首次登录强制改密和常规改密功能。
 * 修改密码成功后清除 must_change_password 标记。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/auth")
public class ChangePasswordController {

    private final SysAdminAccountMapper adminAccountMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public ChangePasswordController(SysAdminAccountMapper adminAccountMapper,
                                     BCryptPasswordEncoder passwordEncoder) {
        this.adminAccountMapper = adminAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 修改密码。
     * <p>
     * 验证旧密码正确性，新密码不能与原密码相同。
     * 修改成功后自动清除 must_change_password 标记。
     */
    @PostMapping("/change-password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = TenantContext.requireUserId();
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "账号不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), account.getPassword())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "原密码不正确");
        }

        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "新密码不能与原密码相同");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        account.setMustChangePassword(0);
        account.setUpdatedAt(LocalDateTime.now());
        adminAccountMapper.updateById(account);

        return R.ok();
    }

    /**
     * 修改密码请求体。
     */
    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度须在6-64个字符之间")
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
