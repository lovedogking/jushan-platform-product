package com.jushan.platform.modules.vehicle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.vehicle.dto.VehicleCreateCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleMultiPlateBindCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleUpdateCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.vo.VehicleVO;

import java.util.List;

/**
 * 车辆主表服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysVehicleService extends IService<SysVehicle> {

    /**
     * 新增车辆。
     *
     * @param cmd 创建命令
     * @return 车辆视图对象
     */
    VehicleVO create(VehicleCreateCmd cmd);

    /**
     * 编辑车辆。
     *
     * @param id  车辆ID
     * @param cmd 更新命令
     * @return 车辆视图对象
     */
    VehicleVO updateVehicle(Long id, VehicleUpdateCmd cmd);

    /**
     * 软删除车辆。
     *
     * @param id 车辆ID
     */
    void deleteVehicle(Long id);

    /**
     * 批量删除车辆。
     *
     * @param ids 车辆ID列表
     */
    void batchDelete(List<Long> ids);

    /**
     * 查询车辆详情。
     *
     * @param id 车辆ID
     * @return 车辆视图对象
     */
    VehicleVO detail(Long id);

    /**
     * 分页查询车辆列表。
     *
     * @param page         分页参数
     * @param plateNumber  车牌号（可选）
     * @param vehicleType  车辆类型（可选）
     * @param departmentId 部门ID（可选）
     * @param parkingLotId 停车场ID（可选）
     * @param status       状态（可选）
     * @return 分页结果
     */
    IPage<VehicleVO> pageList(IPage<SysVehicle> page, String plateNumber, String vehicleType,
                               Long departmentId, Long parkingLotId, String status);

    /**
     * 添加一位多车绑定。
     *
     * @param vehicleId 主车辆ID
     * @param cmd       绑定命令
     */
    void addMultiPlate(Long vehicleId, VehicleMultiPlateBindCmd cmd);

    /**
     * 解除一位多车绑定。
     *
     * @param vehicleId 主车辆ID
     * @param bindId    绑定ID
     */
    void removeMultiPlate(Long vehicleId, Long bindId);

    /**
     * 根据车牌号查询车辆。
     *
     * @param plateNumber 车牌号
     * @return 车辆视图对象
     */
    VehicleVO findByPlateNumber(String plateNumber);
}
