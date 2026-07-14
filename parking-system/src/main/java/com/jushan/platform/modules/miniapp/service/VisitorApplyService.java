package com.jushan.platform.modules.miniapp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.miniapp.dto.VisitorApplyAuditCmd;
import com.jushan.platform.modules.miniapp.dto.VisitorApplyCreateCmd;
import com.jushan.platform.modules.miniapp.entity.VisitorApply;
import com.jushan.platform.modules.miniapp.vo.VisitorApplyVO;

import java.util.List;

/**
 * 访客预约申请服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface VisitorApplyService extends IService<VisitorApply> {

    /**
     * 提交访客预约申请。
     *
     * @param cmd 创建命令
     * @return 预约视图对象
     */
    VisitorApplyVO submit(VisitorApplyCreateCmd cmd);

    /**
     * 审核访客预约申请。
     *
     * @param id  预约ID
     * @param cmd 审核命令
     * @return 预约视图对象
     */
    VisitorApplyVO audit(Long id, VisitorApplyAuditCmd cmd);

    /**
     * 取消访客预约申请。
     *
     * @param id 预约ID
     * @return 预约视图对象
     */
    VisitorApplyVO cancel(Long id);

    /**
     * 查询预约详情。
     *
     * @param id 预约ID
     * @return 预约视图对象
     */
    VisitorApplyVO detail(Long id);

    /**
     * 分页查询预约列表。
     *
     * @param page         分页参数
     * @param parkingLotId 停车场ID（可选）
     * @param applyStatus  申请状态（可选）
     * @param plateNumber  车牌号（可选）
     * @return 分页结果
     */
    IPage<VisitorApplyVO> pageList(IPage<VisitorApply> page, Long parkingLotId, String applyStatus, String plateNumber);

    /**
     * 查询指定停车场的预约列表。
     *
     * @param parkingLotId 停车场ID
     * @return 预约列表
     */
    List<VisitorApplyVO> listByParkingLotId(Long parkingLotId);

    /**
     * 查询指定申请人的预约列表。
     *
     * @param applicantId 申请人ID
     * @return 预约列表
     */
    List<VisitorApplyVO> listByApplicantId(Long applicantId);
}
