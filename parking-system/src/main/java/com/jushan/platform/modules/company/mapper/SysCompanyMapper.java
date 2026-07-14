package com.jushan.platform.modules.company.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.company.entity.SysCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公司档案 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysCompanyMapper extends BaseMapper<SysCompany> {

    /**
     * 查询租户下的公司树形结构（使用递归 CTE）。
     *
     * @param tenantId 租户ID
     * @return 公司列表（平铺，需在 Service 层组装为树）
     */
    List<SysCompany> selectTreeByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 统计指定公司下未删除的管理员账号数量。
     *
     * @param companyId 公司ID
     * @return 关联账号数量
     */
    long countAdminAccountByCompanyId(@Param("companyId") Long companyId);
}
