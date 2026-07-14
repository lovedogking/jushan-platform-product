package com.jushan.platform.modules.authcode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.authcode.dto.BatchGenerateAuthCodeRequest;
import com.jushan.platform.modules.authcode.vo.SysAuthCodeVO;

import java.util.List;

/**
 * 授权码服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysAuthCodeService {

    /**
     * 批量生成授权码。
     *
     * <p>仅平台管理员（一级）可调用。</p>
     *
     * @param request 批量生成请求
     * @return 生成的授权码视图列表
     */
    List<SysAuthCodeVO> batchGenerate(BatchGenerateAuthCodeRequest request);

    /**
     * 分页查询授权码列表。
     *
     * <p>
     * 平台管理员查看全部，租户管理员仅查看本租户已激活的码。
     *
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @param status 状态筛选（可选）
     * @param code   授权码模糊查询（可选）
     * @return 分页结果
     */
    IPage<SysAuthCodeVO> list(int page, int size, Integer status, String code);

    /**
     * 激活授权码。
     *
     * <p>仅租户管理员（二级）可调用。</p>
     *
     * @param code 授权码
     * @return 激活后的授权码视图
     */
    SysAuthCodeVO activate(String code);

    /**
     * 禁用授权码。
     *
     * <p>仅平台管理员可调用。</p>
     *
     * @param id 授权码 ID
     */
    void disable(Long id);

    /**
     * 导出授权码列表（简易实现，返回全部符合条件的数据）。
     *
     * <p>
     * 平台管理员导出全部，租户管理员仅导出本租户已激活的码。
     *
     * @param status 状态筛选（可选）
     * @param code   授权码模糊查询（可选）
     * @return 授权码视图列表
     */
    List<SysAuthCodeVO> export(Integer status, String code);
}
