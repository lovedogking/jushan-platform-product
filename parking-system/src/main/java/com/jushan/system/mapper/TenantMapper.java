package com.jushan.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /**
     * 按联系人手机号查询租户，忽略租户拦截器。
     * <p>
     * 仅用于测试数据准备等无会话上下文场景。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM tenant WHERE contact_phone = #{contactPhone} LIMIT 1")
    Tenant selectByContactPhoneIgnoreTenant(@Param("contactPhone") String contactPhone);
}
