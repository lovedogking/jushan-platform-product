package com.jushan.platform.modules.authcode.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.authcode.entity.SysAuthCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权码 Mapper。
 *
 * <p>
 * 授权码表 {@code sys_auth_code} 为系统级表：激活前 {@code tenant_id} 为 null，
 * 激活后绑定租户。因此本 Mapper 所有方法均忽略 MyBatis-Plus 多租户拦截器，
 * 由 Service 层根据当前登录用户类型自行控制数据范围。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysAuthCodeMapper extends BaseMapper<SysAuthCode> {

    /**
     * 批量插入授权码（忽略租户拦截器）。
     *
     * @param list 授权码列表
     * @return 插入条数
     */
    @InterceptorIgnore(tenantLine = "true")
    int insertBatch(@Param("list") List<SysAuthCode> list);

    /**
     * 按授权码查询（忽略租户拦截器）。
     *
     * @param code 授权码
     * @return 授权码记录
     */
    @InterceptorIgnore(tenantLine = "true")
    SysAuthCode selectByCodeIgnoreTenant(@Param("code") String code);

    /**
     * 按 ID 查询（忽略租户拦截器）。
     *
     * @param id 授权码 ID
     * @return 授权码记录
     */
    @InterceptorIgnore(tenantLine = "true")
    SysAuthCode selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 分页查询授权码（忽略租户拦截器）。
     *
     * <p>
     * 查询条件由 Service 层根据当前用户类型决定：
     * <ul>
     *   <li>平台用户：查看全部</li>
     *   <li>租户用户：仅查看本租户已激活的码</li>
     * </ul>
     *
     * @param page      分页对象
     * @param tenantId  租户 ID（平台用户传 null）
     * @param status    状态筛选（可选）
     * @param code      授权码模糊查询（可选）
     * @return 分页结果
     */
    @InterceptorIgnore(tenantLine = "true")
    IPage<SysAuthCode> selectPageIgnoreTenant(IPage<SysAuthCode> page,
                                              @Param("tenantId") Long tenantId,
                                              @Param("status") Integer status,
                                              @Param("code") String code);

    /**
     * 激活授权码（忽略租户拦截器）。
     *
     * <p>
     * 使用条件更新保证并发安全：仅当原状态为未使用、且使用次数未超限时才更新成功。
     *
     * @param code       授权码
     * @param tenantId   当前租户 ID
     * @param activatedBy 激活人 ID
     * @param activatedAt 激活时间
     * @return 影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    int activateIgnoreTenant(@Param("code") String code,
                             @Param("tenantId") Long tenantId,
                             @Param("activatedBy") Long activatedBy,
                             @Param("activatedAt") LocalDateTime activatedAt);

    /**
     * 禁用授权码（忽略租户拦截器）。
     *
     * @param id 授权码 ID
     * @return 影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    int disableIgnoreTenant(@Param("id") Long id);

    /**
     * 查询全部授权码（忽略租户拦截器，用于导出）。
     *
     * @param tenantId 租户 ID（平台用户传 null）
     * @param status   状态筛选（可选）
     * @param code     授权码模糊查询（可选）
     * @return 授权码列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<SysAuthCode> selectListIgnoreTenant(@Param("tenantId") Long tenantId,
                                             @Param("status") Integer status,
                                             @Param("code") String code);
}
