package com.jushan.platform.modules.account.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.account.dto.CustomRoleCreateCmd;
import com.jushan.platform.modules.account.dto.CustomRoleUpdateCmd;
import com.jushan.platform.modules.account.dto.RolePermissionSaveCmd;
import com.jushan.platform.modules.account.service.SysCustomRoleService;
import com.jushan.platform.modules.account.vo.CustomRoleVO;
import com.jushan.platform.modules.account.vo.RolePermissionVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义角色管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/custom-roles")
public class SysCustomRoleController {

    private static final Logger log = LoggerFactory.getLogger(SysCustomRoleController.class);

    private final SysCustomRoleService customRoleService;

    public SysCustomRoleController(SysCustomRoleService customRoleService) {
        this.customRoleService = customRoleService;
    }

    /**
     * 创建自定义角色。
     */
    @PostMapping
    @RequirePermission("role:create")
    public R<CustomRoleVO> create(@Valid @RequestBody CustomRoleCreateCmd cmd) {
        CustomRoleVO vo = customRoleService.create(cmd);
        log.info("创建自定义角色成功: id={}", vo.getId());
        return R.ok(vo);
    }

    /**
     * 编辑自定义角色。
     */
    @PutMapping("/{id}")
    @RequirePermission("role:update")
    public R<CustomRoleVO> update(@PathVariable Long id,
                                   @Valid @RequestBody CustomRoleUpdateCmd cmd) {
        CustomRoleVO vo = customRoleService.update(id, cmd);
        log.info("编辑自定义角色成功: id={}", id);
        return R.ok(vo);
    }

    /**
     * 查询自定义角色详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("role:view")
    public R<CustomRoleVO> detail(@PathVariable Long id) {
        CustomRoleVO vo = customRoleService.getById(id);
        return R.ok(vo);
    }

    /**
     * 分页查询自定义角色列表。
     */
    @GetMapping
    @RequirePermission("role:view")
    public R<IPage<CustomRoleVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        IPage<CustomRoleVO> result = customRoleService.list(page, size, keyword);
        return R.ok(result);
    }

    /**
     * 查询角色权限矩阵。
     */
    @GetMapping("/{id}/permissions")
    @RequirePermission("role:view")
    public R<List<RolePermissionVO>> getPermissions(@PathVariable Long id) {
        List<RolePermissionVO> list = customRoleService.getPermissions(id);
        return R.ok(list);
    }

    /**
     * 保存角色权限矩阵。
     */
    @PutMapping("/{id}/permissions")
    @RequirePermission("role:update")
    public R<Void> savePermissions(@PathVariable Long id,
                                    @Valid @RequestBody RolePermissionSaveCmd cmd) {
        customRoleService.savePermissions(id, cmd);
        log.info("保存角色权限矩阵成功: roleId={}", id);
        return R.ok();
    }
}
