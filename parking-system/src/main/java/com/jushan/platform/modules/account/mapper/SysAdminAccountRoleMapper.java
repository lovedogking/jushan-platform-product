package com.jushan.platform.modules.account.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.account.entity.SysAdminAccountRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理员账号角色关联 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysAdminAccountRoleMapper extends BaseMapper<SysAdminAccountRole> {

    /**
     * 根据管理员账号 ID 查询角色 ID 列表（忽略租户拦截器）。
     *
     * @param adminAccountId 管理员账号 ID
     * @return 角色 ID 列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<Long> selectRoleIdsByAdminAccountId(@Param("adminAccountId") Long adminAccountId);

    /**
     * 根据管理员账号 ID 删除角色关联（忽略租户拦截器）。
     *
     * @param adminAccountId 管理员账号 ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    int deleteByAdminAccountId(@Param("adminAccountId") Long adminAccountId);

    /**
     * 根据角色 ID 查询账号角色关联（忽略租户拦截器）。
     *
     * @param roleId 角色 ID
     * @return 关联列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<SysAdminAccountRole> selectByRoleIdIgnoreTenant(@Param("roleId") Long roleId);

    /**
     * 插入账号角色关联（忽略租户拦截器）。
     *
     * @param entity 关联实体
     * @return 插入行数
     */
    @InterceptorIgnore(tenantLine = "true")
    int insertIgnoreTenant(SysAdminAccountRole entity);
}
