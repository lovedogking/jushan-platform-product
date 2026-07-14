package com.jushan.platform.modules.account.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 管理员账号 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysAdminAccountMapper extends BaseMapper<SysAdminAccount> {

    /**
     * 根据登录账号查询管理员（忽略租户拦截器）。
     *
     * @param username 登录账号
     * @return 管理员账号
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_admin_account WHERE username = #{username} AND deleted_at IS NULL LIMIT 1")
    SysAdminAccount selectByUsernameIgnoreTenant(@Param("username") String username);

    /**
     * 根据主键查询管理员（忽略租户拦截器）。
     *
     * @param id 主键
     * @return 管理员账号
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_admin_account WHERE id = #{id} AND deleted_at IS NULL LIMIT 1")
    SysAdminAccount selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 条件分页查询管理员账号（忽略租户拦截器，由业务层控制数据范围）。
     *
     * @param page      分页对象
     * @param tenantId  租户 ID（null 表示不限制）
     * @param companyId 公司 ID（null 表示不限制）
     * @param lotId     停车场 ID（null 表示不限制）
     * @param keyword   关键字
     * @param status    状态
     * @return 分页结果
     */
    @InterceptorIgnore(tenantLine = "true")
    IPage<SysAdminAccount> selectPageList(@Param("page") IPage<SysAdminAccount> page,
                                          @Param("tenantId") Long tenantId,
                                          @Param("companyId") Long companyId,
                                          @Param("lotId") Long lotId,
                                          @Param("keyword") String keyword,
                                          @Param("status") Integer status);

    /**
     * 根据主键更新管理员账号（忽略租户拦截器）。
     *
     * @param entity 账号实体
     * @return 更新行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_admin_account " +
            "SET company_id = #{companyId}, lot_id = #{lotId}, real_name = #{realName}, " +
            "phone = #{phone}, email = #{email}, level = #{level}, status = #{status}, " +
            "login_fail_count = #{loginFailCount}, lock_until = #{lockUntil}, " +
            "last_login_time = #{lastLoginTime}, password = #{password}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id} AND deleted_at IS NULL")
    int updateByIdIgnoreTenant(SysAdminAccount entity);

    /**
     * 根据主键软删除管理员账号（忽略租户拦截器）。
     *
     * @param id 账号 ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_admin_account SET deleted_at = NOW(), updated_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    int deleteByIdIgnoreTenant(@Param("id") Long id);
}
