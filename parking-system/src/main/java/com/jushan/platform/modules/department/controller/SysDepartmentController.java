package com.jushan.platform.modules.department.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.department.dto.DepartmentCreateCmd;
import com.jushan.platform.modules.department.dto.DepartmentUpdateCmd;
import com.jushan.platform.modules.department.entity.SysDepartment;
import com.jushan.platform.modules.department.service.SysDepartmentService;
import com.jushan.platform.modules.department.vo.DepartmentTreeVO;
import com.jushan.platform.modules.department.vo.DepartmentVO;
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
 * 部门/组织架构管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/departments")
public class SysDepartmentController {

    private final SysDepartmentService sysDepartmentService;

    public SysDepartmentController(SysDepartmentService sysDepartmentService) {
        this.sysDepartmentService = sysDepartmentService;
    }

    @PostMapping
    @RequirePermission("department:create")
    public R<DepartmentVO> create(@Valid @RequestBody DepartmentCreateCmd cmd) {
        DepartmentVO vo = sysDepartmentService.create(cmd);
        log.info("新增部门成功: departmentId={}, name={}", vo.getId(), vo.getName());
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("department:update")
    public R<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateCmd cmd) {
        DepartmentVO vo = sysDepartmentService.updateDepartment(id, cmd);
        log.info("编辑部门成功: departmentId={}", id);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("department:delete")
    public R<Void> delete(@PathVariable Long id) {
        sysDepartmentService.deleteDepartment(id);
        log.info("删除部门成功: departmentId={}", id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequirePermission("department:view")
    public R<DepartmentVO> detail(@PathVariable Long id) {
        return R.ok(sysDepartmentService.detail(id));
    }

    @GetMapping("/tree")
    @RequirePermission("department:view")
    public R<List<DepartmentTreeVO>> tree() {
        return R.ok(sysDepartmentService.tree());
    }

    @GetMapping
    @RequirePermission("department:view")
    public R<IPage<DepartmentVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) Long parentId) {
        IPage<DepartmentVO> result = sysDepartmentService.pageList(
                new Page<SysDepartment>(current, size), name, parkingLotId, parentId);
        return R.ok(result);
    }
}
