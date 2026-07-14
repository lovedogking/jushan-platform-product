package com.jushan.system.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.common.R;
import com.jushan.system.dto.CreateCompanyRequest;
import com.jushan.system.dto.UpdateCompanyRequest;
import com.jushan.system.service.CompanyService;
import com.jushan.system.vo.CompanyTreeVO;
import com.jushan.system.vo.CompanyVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公司/集团档案管理控制器（运营端）。
 * <p>
 * 提供公司 CRUD 与树形查询接口。所有操作从当前登录会话推导租户范围，
 * 不信任前端传入的 tenantId。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/companies")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
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
