package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 查询指定角色编码列表对应的所有权限编码。
     *
     * @param roleCodes 角色编码列表
     * @return 权限编码列表（去重）
     */
    @Select("<script>" +
            "SELECT DISTINCT rp.permission_code FROM sys_role_permission rp " +
            "WHERE rp.role_code IN " +
            "<foreach collection='roleCodes' item='code' open='(' separator=',' close=')'>" +
            "#{code}" +
            "</foreach>" +
            "</script>")
    List<String> selectPermissionCodesByRoleCodes(@Param("roleCodes") List<String> roleCodes);
}
