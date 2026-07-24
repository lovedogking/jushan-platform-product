package com.jushan.platform.modules.company.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.modules.company.dto.CreateCompanyRequest;
import com.jushan.platform.modules.company.dto.UpdateCompanyRequest;
import com.jushan.platform.modules.company.service.CompanyService;
import com.jushan.platform.modules.company.vo.CompanyTreeVO;
import com.jushan.platform.modules.company.vo.CompanyVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公司/集团档案管理控制器（运营端）。
 * <p>
 * 提供公司 CRUD、分页查询与树形查询接口。所有操作从当前登录会话推导租户范围，
 * 不信任前端传入的 tenantId。
 *
 * <p><b>已废弃（@Deprecated）：</b>本控制器为旧风格实现，底层表为 {@code company}，
 * 已迁移至新风格 {@code com.jushan.platform.modules.company.controller.SysCompanyController}
 * （底层表 {@code sys_company}，路径 {@code /api/v1/companies}）。
 * 当前保留仅为兼容仍调用 {@code /api/admin/companies} 的前端，待前端切换后清理。
 * 新增代码请勿再依赖本类。
 *
 * @author Jushan Platform
 * @since 1.0.0
 * @deprecated 自 1.0.0 起废弃，迁移目标见类注释。
 */
@Deprecated
@RestController
@RequestMapping("/api/admin/companies")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * 分页查询公司列表。
     * <p>
     * 权限：company:read
     *
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @param name     名称模糊筛选（可选）
     * @param level    级别筛选（可选）
     * @param parentId 上级公司 ID 筛选（可选）
     */
    @GetMapping
    @RequirePermission("company:read")
    public R<IPage<CompanyVO>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) Integer level,
                                     @RequestParam(required = false) Long parentId) {
        IPage<CompanyVO> result = companyService.page(page, size, name, level, parentId);
        return R.ok(result);
    }

    /**
     * 创建公司/集团。
     * <p>
     * 权限：company:write
     */
    @PostMapping
    @RequirePermission("company:write")
    public R<CompanyVO> create(@Valid @RequestBody CreateCompanyRequest request) {
        CompanyVO vo = companyService.create(request);
        log.info("创建公司成功: companyId={}, name={}", vo.getId(), vo.getName());
        return R.ok(vo);
    }

    /**
     * 更新公司/集团。
     * <p>
     * 权限：company:write
     */
    @PutMapping("/{id}")
    @RequirePermission("company:write")
    public R<CompanyVO> update(@PathVariable Long id, @Valid @RequestBody UpdateCompanyRequest request) {
        CompanyVO vo = companyService.update(id, request);
        log.info("更新公司成功: companyId={}", id);
        return R.ok(vo);
    }

    /**
     * 软删除公司/集团。
     * <p>
     * 存在子节点或关联停车场时删除失败。
     * 权限：company:delete
     */
    @DeleteMapping("/{id}")
    @RequirePermission("company:delete")
    public R<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        log.info("删除公司成功: companyId={}", id);
        return R.ok();
    }

    /**
     * 查询公司详情。
     * <p>
     * 权限：company:read
     */
    @GetMapping("/{id}")
    @RequirePermission("company:read")
    public R<CompanyVO> detail(@PathVariable Long id) {
        CompanyVO vo = companyService.get(id);
        return R.ok(vo);
    }

    /**
     * 查询公司树（当前租户）。
     * <p>
     * 权限：company:read
     */
    @GetMapping("/tree")
    @RequirePermission("company:read")
    public R<List<CompanyTreeVO>> tree() {
        List<CompanyTreeVO> tree = companyService.tree();
        return R.ok(tree);
    }
}
