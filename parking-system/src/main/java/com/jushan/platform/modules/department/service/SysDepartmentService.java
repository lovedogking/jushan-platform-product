package com.jushan.platform.modules.department.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.department.dto.DepartmentCreateCmd;
import com.jushan.platform.modules.department.dto.DepartmentUpdateCmd;
import com.jushan.platform.modules.department.entity.SysDepartment;
import com.jushan.platform.modules.department.vo.DepartmentTreeVO;
import com.jushan.platform.modules.department.vo.DepartmentVO;

import java.util.List;

/**
 * 部门/组织架构服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysDepartmentService extends IService<SysDepartment> {

    /**
     * 新增部门。
     *
     * @param cmd 创建命令
     * @return 部门视图对象
     */
    DepartmentVO create(DepartmentCreateCmd cmd);

    /**
     * 编辑部门。
     *
     * @param id  部门ID
     * @param cmd 更新命令
     * @return 部门视图对象
     */
    DepartmentVO updateDepartment(Long id, DepartmentUpdateCmd cmd);

    /**
     * 软删除部门。
     *
     * @param id 部门ID
     */
    void deleteDepartment(Long id);

    /**
     * 查询部门详情。
     *
     * @param id 部门ID
     * @return 部门视图对象
     */
    DepartmentVO detail(Long id);

    /**
     * 查询当前租户的部门树形结构。
     *
     * @return 部门树列表
     */
    List<DepartmentTreeVO> tree();

    /**
     * 分页查询部门列表。
     *
     * @param page         分页参数
     * @param name         部门名称关键字（可选）
     * @param parkingLotId 停车场ID（可选）
     * @param parentId     上级部门ID（可选）
     * @return 分页结果
     */
    IPage<DepartmentVO> pageList(IPage<SysDepartment> page, String name, Long parkingLotId, Long parentId);
}
