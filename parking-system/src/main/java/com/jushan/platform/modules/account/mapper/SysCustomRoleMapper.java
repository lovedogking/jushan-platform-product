package com.jushan.platform.modules.account.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.account.entity.SysCustomRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 自定义角色 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysCustomRoleMapper extends BaseMapper<SysCustomRole> {

    /**
     * 根据角色编码查询角色（忽略租户拦截器）。
     *
     * @param roleCode 角色编码
     * @return 自定义角色
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_custom_role WHERE role_code = #{roleCode} AND deleted_at IS NULL LIMIT 1")
    SysCustomRole selectByRoleCodeIgnoreTenant(@Param("roleCode") String roleCode);

    /**
     * 根据主键查询角色（忽略租户拦截器）。
     *
     * @param id 主键
     * @return 自定义角色
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_custom_role WHERE id = #{id} AND deleted_at IS NULL LIMIT 1")
    SysCustomRole selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 条件分页查询自定义角色（忽略租户拦截器，由业务层控制数据范围）。
     *
     * @param page     分页对象
     * @param tenantId 租户 ID（null 表示不限制）
     * @param keyword  关键字
     * @return 分页结果
     */
    @InterceptorIgnore(tenantLine = "true")
    IPage<SysCustomRole> selectPageList(@Param("page") IPage<SysCustomRole> page,
                                        @Param("tenantId") Long tenantId,
                                        @Param("keyword") String keyword);

    /**
     * 根据主键更新自定义角色（忽略租户拦截器）。
     *
     * @param entity 角色实体
     * @return 更新行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_custom_role " +
            "SET role_name = #{roleName}, role_code = #{roleCode}, description = #{description}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateByIdIgnoreTenant(SysCustomRole entity);

    /**
     * 根据主键软删除自定义角色（忽略租户拦截器）。
     *
     * @param id 角色 ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_custom_role SET deleted_at = NOW(), updated_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int deleteByIdIgnoreTenant(@Param("id") Long id);
}
