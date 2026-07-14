package com.jushan.platform.modules.account.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.account.dto.AdminAccountCreateCmd;
import com.jushan.platform.modules.account.dto.AdminAccountUpdateCmd;
import com.jushan.platform.modules.account.service.SysAdminAccountService;
import com.jushan.platform.modules.account.vo.AdminAccountVO;
import com.jushan.platform.modules.account.vo.ResetPasswordVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员账号管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin-accounts")
public class SysAdminAccountController {

    private static final Logger log = LoggerFactory.getLogger(SysAdminAccountController.class);

    private final SysAdminAccountService adminAccountService;

    public SysAdminAccountController(SysAdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /**
     * 创建管理员账号。
     */
    @PostMapping
    @RequirePermission("account:create")
    public R<AdminAccountVO> create(@Valid @RequestBody AdminAccountCreateCmd cmd) {
        AdminAccountVO vo = adminAccountService.create(cmd);
        log.info("创建管理员账号成功: id={}", vo.getId());
        return R.ok(vo);
    }

    /**
     * 编辑管理员账号。
     */
    @PutMapping("/{id}")
    @RequirePermission("account:update")
    public R<AdminAccountVO> update(@PathVariable Long id,
                                      @Valid @RequestBody AdminAccountUpdateCmd cmd) {
        AdminAccountVO vo = adminAccountService.update(id, cmd);
        log.info("编辑管理员账号成功: id={}", id);
        return R.ok(vo);
    }

    /**
     * 软删除管理员账号。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("account:delete")
    public R<Void> delete(@PathVariable Long id) {
        adminAccountService.delete(id);
        log.info("删除管理员账号成功: id={}", id);
        return R.ok();
    }

    /**
     * 查询管理员账号详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("account:view")
    public R<AdminAccountVO> detail(@PathVariable Long id) {
        AdminAccountVO vo = adminAccountService.getById(id);
        return R.ok(vo);
    }

    /**
     * 分页查询管理员账号列表。
     */
    @GetMapping
    @RequirePermission("account:view")
    public R<IPage<AdminAccountVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        IPage<AdminAccountVO> result = adminAccountService.list(page, size, keyword, status);
        return R.ok(result);
    }

    /**
     * 重置管理员密码。
     */
    @PostMapping("/{id}/reset-password")
    @RequirePermission("account:update")
    public R<ResetPasswordVO> resetPassword(@PathVariable Long id) {
        ResetPasswordVO vo = adminAccountService.resetPassword(id);
        log.info("重置管理员密码成功: id={}", id);
        return R.ok(vo);
    }
}
