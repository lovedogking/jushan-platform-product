package com.jushan.platform.modules.account.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.account.dto.CustomRoleCreateCmd;
import com.jushan.platform.modules.account.dto.CustomRoleUpdateCmd;
import com.jushan.platform.modules.account.dto.RolePermissionSaveCmd;
import com.jushan.platform.modules.account.vo.CustomRoleVO;
import com.jushan.platform.modules.account.vo.RolePermissionVO;

import java.util.List;

/**
 * 自定义角色服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysCustomRoleService {

    /**
     * 创建自定义角色。
     *
     * @param cmd 创建参数
     * @return 角色视图对象
     */
    CustomRoleVO create(CustomRoleCreateCmd cmd);

    /**
     * 编辑自定义角色。
     *
     * @param id  角色 ID
     * @param cmd 编辑参数
     * @return 角色视图对象
     */
    CustomRoleVO update(Long id, CustomRoleUpdateCmd cmd);

    /**
     * 查询自定义角色详情。
     *
     * @param id 角色 ID
     * @return 角色视图对象
     */
    CustomRoleVO getById(Long id);

    /**
     * 分页查询自定义角色列表。
     *
     * @param page    页码
     * @param size    每页大小
     * @param keyword 关键字（角色名称/编码）
     * @return 分页结果
     */
    IPage<CustomRoleVO> list(int page, int size, String keyword);

    /**
     * 保存角色权限矩阵。
     *
     * @param roleId 角色 ID
     * @param cmd    权限矩阵参数
     */
    void savePermissions(Long roleId, RolePermissionSaveCmd cmd);

    /**
     * 查询角色权限矩阵。
     *
     * @param roleId 角色 ID
     * @return 权限矩阵列表
     */
    List<RolePermissionVO> getPermissions(Long roleId);
}
