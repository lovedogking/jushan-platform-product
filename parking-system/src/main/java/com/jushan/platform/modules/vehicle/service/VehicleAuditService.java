package com.jushan.platform.modules.vehicle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.vehicle.dto.VehicleAuditProcessCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleAuditSubmitCmd;
import com.jushan.platform.modules.vehicle.entity.VehicleAudit;
import com.jushan.platform.modules.vehicle.vo.VehicleAuditVO;

import java.util.List;

/**
 * 车辆审核服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface VehicleAuditService extends IService<VehicleAudit> {

    /**
     * 提交审核申请。
     *
     * @param cmd 提交命令
     * @return 审核记录视图对象
     */
    VehicleAuditVO submit(VehicleAuditSubmitCmd cmd);

    /**
     * 处理审核（通过/驳回/待补充）。
     *
     * @param id  审核ID
     * @param cmd 处理命令
     * @return 审核记录视图对象
     */
    VehicleAuditVO process(Long id, VehicleAuditProcessCmd cmd);

    /**
     * 查询审核详情。
     *
     * @param id 审核ID
     * @return 审核记录视图对象
     */
    VehicleAuditVO detail(Long id);

    /**
     * 分页查询审核列表。
     *
     * @param page        分页参数
     * @param auditStatus 审核状态（可选）
     * @param applyType   申请类型（可选）
     * @param plateNumber 车牌号（可选）
     * @return 分页结果
     */
    IPage<VehicleAuditVO> pageList(IPage<VehicleAudit> page, String auditStatus, String applyType, String plateNumber);

    /**
     * 查询指定车辆的审核记录。
     *
     * @param vehicleId 车辆ID
     * @return 审核记录列表
     */
    List<VehicleAuditVO> listByVehicleId(Long vehicleId);
}
