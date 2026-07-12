package com.jushan.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.system.dto.CreateEmployeeRequest;
import com.jushan.system.dto.ResetPasswordRequest;
import com.jushan.system.dto.UpdateEmployeeRequest;
import com.jushan.system.service.EmployeeService;
import com.jushan.system.vo.EmployeeVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 客户员工管理控制器（运营端）。
 * <p>
 * 客户管理员通过此接口管理本租户内的员工。
 * 所有操作从当前登录会话推导租户范围，不信任前端传入的 tenantId。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/employees")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * 分页查询本租户员工列表。
     * <p>
     * 权限：user:read
     *
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @param status 状态筛选（可选：ENABLED / DISABLED）
     */
    @GetMapping
    @SaCheckPermission("user:read")
    public R<IPage<EmployeeVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String status) {
        IPage<EmployeeVO> result = employeeService.listEmployees(page, size, status);
        return R.ok(result);
    }

    /**
     * 查询单个员工详情。
     * <p>
     * 权限：user:read
     */
    @GetMapping("/{id}")
    @SaCheckPermission("user:read")
    public R<EmployeeVO> detail(@PathVariable Long id) {
        EmployeeVO vo = employeeService.getEmployee(id);
        return R.ok(vo);
    }

    /**
     * 创建员工。
     * <p>
     * 权限：user:write
     */
    @PostMapping
    @SaCheckPermission("user:write")
    public R<EmployeeVO> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeVO vo = employeeService.createEmployee(request);
        log.info("创建员工成功: employeeId={}, role={}", vo.getId(), vo.getRoleCode());
        return R.ok(vo);
    }

    /**
     * 更新员工信息。
     * <p>
     * 权限：user:write
     */
    @PutMapping("/{id}")
    @SaCheckPermission("user:write")
    public R<EmployeeVO> update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        EmployeeVO vo = employeeService.updateEmployee(id, request);
        log.info("更新员工成功: employeeId={}", id);
        return R.ok(vo);
    }

    /**
     * 重置员工密码。
     * <p>
     * 权限：user:write
     */
    @PostMapping("/{id}/reset-password")
    @SaCheckPermission("user:write")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        employeeService.resetPassword(id, request);
        log.info("重置员工密码成功: employeeId={}", id);
        return R.ok();
    }

    /**
     * 更新员工状态（启用/禁用）。
     * <p>
     * 权限：user:write
     *
     * @param id     员工 ID
     * @param action 操作类型：ENABLED / DISABLED
     */
    @PostMapping("/{id}/status")
    @SaCheckPermission("user:write")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String action) {
        employeeService.updateStatus(id, action);
        log.info("更新员工状态成功: employeeId={}, action={}", id, action);
        return R.ok();
    }
}
