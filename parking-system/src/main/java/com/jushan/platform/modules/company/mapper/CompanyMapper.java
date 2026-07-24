package com.jushan.platform.modules.company.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.Company;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 公司/集团档案 Mapper。
 *
 * <p><b>已废弃（@Deprecated）：</b>本 Mapper 为旧风格实现，对应表 {@code company}，
 * 已迁移至新风格 {@code com.jushan.platform.modules.company.mapper.SysCompanyMapper}
 * （对应表 {@code sys_company}）。新增代码请勿再依赖本接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 * @deprecated 自 1.0.0 起废弃，迁移目标见类注释。
 */
@Deprecated
@Mapper
public interface CompanyMapper extends BaseMapper<Company> {

    /**
     * 根据路径前缀查询所有后代节点（包含自身）。
     * <p>
     * 调用方需自行拼接租户条件或确保在 Service 层做租户隔离校验。
     *
     * @param pathPrefix 路径前缀，如 /1/12/
     * @return 后代公司列表
     */
    @Select("SELECT * FROM company WHERE deleted_at IS NULL AND path LIKE CONCAT(#{pathPrefix}, '%') ORDER BY path, sort_order")
    List<Company> selectDescendantsByPath(@Param("pathPrefix") String pathPrefix);

    /**
     * 按租户 ID 查询默认集团（level=1），忽略租户拦截器。
     * <p>
     * 仅用于测试数据准备等无会话上下文场景。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM company WHERE tenant_id = #{tenantId} AND level = 1 AND deleted_at IS NULL LIMIT 1")
    Company selectDefaultByTenantIdIgnoreTenant(@Param("tenantId") Long tenantId);

    /**
     * 更新公司 path，忽略租户拦截器。
     * <p>
     * 仅用于注册阶段创建默认公司后回填自增 ID 生成的 path（此时上下文为空）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE company SET path = #{path}, updated_at = NOW() WHERE id = #{id}")
    int updatePathIgnoreTenant(@Param("id") Long id, @Param("path") String path);
}
