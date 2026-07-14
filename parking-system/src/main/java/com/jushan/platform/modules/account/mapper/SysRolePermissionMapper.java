package com.jushan.platform.modules.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.account.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色权限矩阵 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 根据角色 ID 查询权限矩阵。
     *
     * @param roleId 角色 ID
     * @return 权限矩阵列表
     */
    List<SysRolePermission> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据角色 ID 列表查询权限编码（去重）。
     *
     * @param roleIds 角色 ID 列表
     * @return 权限编码列表
     */
    List<String> selectPermissionCodesByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据角色 ID 物理删除权限矩阵。
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    int deleteByRoleId(@Param("roleId") Long roleId);
}
