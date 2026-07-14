package com.jushan.platform.modules.company.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.company.dto.CompanyCreateCmd;
import com.jushan.platform.modules.company.dto.CompanyUpdateCmd;
import com.jushan.platform.modules.company.entity.SysCompany;
import com.jushan.platform.modules.company.vo.CompanyTreeVO;
import com.jushan.platform.modules.company.vo.CompanyVO;

import java.util.List;

/**
 * 公司/集团档案服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysCompanyService extends IService<SysCompany> {

    /**
     * 新增公司/集团。
     *
     * @param cmd 创建命令
     * @return 公司视图对象
     */
    CompanyVO create(CompanyCreateCmd cmd);

    /**
     * 编辑公司/集团。
     *
     * @param id  公司ID
     * @param cmd 更新命令
     * @return 公司视图对象
     */
    CompanyVO updateCompany(Long id, CompanyUpdateCmd cmd);

    /**
     * 软删除公司/集团。
     *
     * @param id 公司ID
     */
    void deleteCompany(Long id);

    /**
     * 查询公司详情。
     *
     * @param id 公司ID
     * @return 公司视图对象
     */
    CompanyVO detail(Long id);

    /**
     * 查询当前租户的公司树形结构。
     *
     * @return 公司树列表
     */
    List<CompanyTreeVO> tree();

    /**
     * 分页查询公司列表。
     *
     * @param page     分页参数
     * @param name     公司名称关键字（可选）
     * @param level    公司级别（可选）
     * @param parentId 上级公司ID（可选）
     * @return 分页结果
     */
    IPage<CompanyVO> pageList(IPage<SysCompany> page, String name, Integer level, Long parentId);
}
