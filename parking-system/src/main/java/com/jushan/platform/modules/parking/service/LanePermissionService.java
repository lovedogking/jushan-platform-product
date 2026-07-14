package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.parking.dto.LanePermissionCreateCmd;
import com.jushan.platform.modules.parking.dto.LanePermissionUpdateCmd;
import com.jushan.platform.modules.parking.entity.LanePermission;
import com.jushan.platform.modules.parking.vo.LanePermissionVO;

import java.util.List;

/**
 * 通道权限配置服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface LanePermissionService extends IService<LanePermission> {

    /**
     * 创建通道权限。
     *
     * @param cmd 创建命令
     * @return 权限视图对象
     */
    LanePermissionVO create(LanePermissionCreateCmd cmd);

    /**
     * 更新通道权限。
     *
     * @param id  权限ID
     * @param cmd 更新命令
     * @return 权限视图对象
     */
    LanePermissionVO update(Long id, LanePermissionUpdateCmd cmd);

    /**
     * 删除通道权限（软删除）。
     *
     * @param id 权限ID
     */
    void delete(Long id);

    /**
     * 查询权限详情。
     *
     * @param id 权限ID
     * @return 权限视图对象
     */
    LanePermissionVO detail(Long id);

    /**
     * 分页查询通道权限列表。
     *
     * @param page       分页参数
     * @param laneId     通道ID（可选）
     * @param targetType 目标类型（可选）
     * @param targetId   目标ID（可选）
     * @param status     状态（可选）
     * @return 分页结果
     */
    IPage<LanePermissionVO> pageList(IPage<LanePermission> page, Long laneId, String targetType, Long targetId, String status);

    /**
     * 查询指定通道的所有权限。
     *
     * @param laneId 通道ID
     * @return 权限列表
     */
    List<LanePermissionVO> listByLaneId(Long laneId);

    /**
     * 查询指定车辆的权限。
     *
     * @param vehicleId 车辆ID
     * @return 权限列表
     */
    List<LanePermissionVO> listByVehicleId(Long vehicleId);

    /**
     * 查询指定部门的权限。
     *
     * @param departmentId 部门ID
     * @return 权限列表
     */
    List<LanePermissionVO> listByDepartmentId(Long departmentId);
}
