package com.jushan.platform.modules.company.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.company.dto.CompanyCreateCmd;
import com.jushan.platform.modules.company.dto.CompanyUpdateCmd;
import com.jushan.platform.modules.company.entity.SysCompany;
import com.jushan.platform.modules.company.service.SysCompanyService;
import com.jushan.platform.modules.company.vo.CompanyTreeVO;
import com.jushan.platform.modules.company.vo.CompanyVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公司/集团档案管理控制器。
 * <p>
 * 提供公司/集团的新增、编辑、软删除、详情、树形查询及分页列表接口。
 * 所有操作通过 {@link com.jushan.platform.infra.auth.TenantContext} 推导租户范围，
 * 不信任前端传入的租户ID。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/companies")
public class SysCompanyController {

    private final SysCompanyService sysCompanyService;

    public SysCompanyController(SysCompanyService sysCompanyService) {
        this.sysCompanyService = sysCompanyService;
    }

    /**
     * 新增公司/集团。
     *
     * @param cmd 创建命令
     * @return 创建后的公司信息
     */
    @PostMapping
    @RequirePermission("company:create")
    public R<CompanyVO> create(@Valid @RequestBody CompanyCreateCmd cmd) {
        CompanyVO vo = sysCompanyService.create(cmd);
        log.info("新增公司成功: companyId={}, name={}", vo.getId(), vo.getName());
        return R.ok(vo);
    }

    /**
     * 编辑公司/集团。
     *
     * @param id  公司ID
     * @param cmd 更新命令
     * @return 更新后的公司信息
     */
    @PutMapping("/{id}")
    @RequirePermission("company:update")
    public R<CompanyVO> update(@PathVariable Long id, @Valid @RequestBody CompanyUpdateCmd cmd) {
        CompanyVO vo = sysCompanyService.updateCompany(id, cmd);
        log.info("编辑公司成功: companyId={}", id);
        return R.ok(vo);
    }

    /**
     * 软删除公司/集团。
     *
     * @param id 公司ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("company:delete")
    public R<Void> delete(@PathVariable Long id) {
        sysCompanyService.deleteCompany(id);
        log.info("删除公司成功: companyId={}", id);
        return R.ok();
    }

    /**
     * 查询公司详情。
     *
     * @param id 公司ID
     * @return 公司详情
     */
    @GetMapping("/{id}")
    @RequirePermission("company:view")
    public R<CompanyVO> detail(@PathVariable Long id) {
        return R.ok(sysCompanyService.detail(id));
    }

    /**
     * 查询公司树形结构。
     *
     * @return 公司树列表
     */
    @GetMapping("/tree")
    @RequirePermission("company:view")
    public R<List<CompanyTreeVO>> tree() {
        return R.ok(sysCompanyService.tree());
    }

    /**
     * 分页查询公司列表。
     *
     * @param current  当前页码
     * @param size     每页大小
     * @param name     公司名称关键字
     * @param level    公司级别
     * @param parentId 上级公司ID
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission("company:view")
    public R<IPage<CompanyVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Long parentId) {
        IPage<CompanyVO> result = sysCompanyService.pageList(
                new Page<SysCompany>(current, size), name, level, parentId);
        return R.ok(result);
    }
}
