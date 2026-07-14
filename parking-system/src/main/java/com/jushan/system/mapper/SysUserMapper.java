package com.jushan.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     * <p>
     * 用于需要先查询实体再做租户归属校验的场景，避免跨租户访问被拦截器直接过滤为 NOT_FOUND。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 按用户名查询，忽略租户拦截器。
     * <p>
     * 仅用于登录阶段（此时租户上下文尚未建立）。其他场景应使用 BaseMapper 默认方法，
     * 以享受自动租户过滤。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsernameIgnoreTenant(@Param("username") String username);

    /**
     * 按用户名统计，忽略租户拦截器。
     * <p>
     * 仅用于注册阶段（此时租户上下文尚未建立）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    Long countByUsernameIgnoreTenant(@Param("username") String username);

    /**
     * 更新用户租户 ID，忽略租户拦截器。
     * <p>
     * 仅用于注册阶段回填 tenant_id（此时租户上下文尚未建立）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET tenant_id = #{tenantId}, updated_at = NOW() WHERE id = #{userId}")
    int updateTenantIdIgnoreTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * 更新用户状态，忽略租户拦截器。
     * <p>
     * 仅用于平台管理员审核租户时同步更新管理员账号状态（此时上下文为平台用户）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET status = #{status}, updated_at = NOW() WHERE id = #{userId} AND status = #{expectedStatus}")
    int updateStatusIgnoreTenant(@Param("userId") Long userId,
                                 @Param("status") String status,
                                 @Param("expectedStatus") String expectedStatus);
}
