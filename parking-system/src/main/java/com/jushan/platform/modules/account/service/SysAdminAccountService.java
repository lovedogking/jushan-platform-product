package com.jushan.platform.modules.account.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.account.dto.AdminAccountCreateCmd;
import com.jushan.platform.modules.account.dto.AdminAccountUpdateCmd;
import com.jushan.platform.modules.account.vo.AdminAccountVO;
import com.jushan.platform.modules.account.vo.ResetPasswordVO;

/**
 * 管理员账号服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysAdminAccountService {

    /**
     * 创建管理员账号。
     *
     * @param cmd 创建参数
     * @return 账号视图对象
     */
    AdminAccountVO create(AdminAccountCreateCmd cmd);

    /**
     * 编辑管理员账号。
     *
     * @param id  账号 ID
     * @param cmd 编辑参数
     * @return 账号视图对象
     */
    AdminAccountVO update(Long id, AdminAccountUpdateCmd cmd);

    /**
     * 软删除管理员账号。
     *
     * @param id 账号 ID
     */
    void delete(Long id);

    /**
     * 查询管理员账号详情。
     *
     * @param id 账号 ID
     * @return 账号视图对象
     */
    AdminAccountVO getById(Long id);

    /**
     * 分页查询管理员账号列表。
     *
     * @param page   页码
     * @param size   每页大小
     * @param keyword 关键字（用户名/真实姓名/手机号）
     * @param status 状态筛选
     * @return 分页结果
     */
    IPage<AdminAccountVO> list(int page, int size, String keyword, Integer status);

    /**
     * 重置管理员密码。
     *
     * @param id 账号 ID
     * @return 明文密码
     */
    ResetPasswordVO resetPassword(Long id);

    /**
     * 记录登录成功。
     *
     * @param username 登录账号
     */
    void onLoginSuccess(String username);

    /**
     * 记录登录失败。
     *
     * @param username 登录账号
     */
    void onLoginFail(String username);
}
