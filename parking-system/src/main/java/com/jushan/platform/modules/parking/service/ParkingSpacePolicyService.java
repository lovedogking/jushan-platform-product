package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.parking.dto.ParkingSpacePolicyCreateCmd;
import com.jushan.platform.modules.parking.entity.ParkingSpacePolicy;
import com.jushan.platform.modules.parking.vo.ParkingSpacePolicyVO;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;

import java.util.List;

/**
 * 车位管控策略服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface ParkingSpacePolicyService extends IService<ParkingSpacePolicy> {

    /**
     * 创建车位管控策略。
     *
     * @param cmd 创建命令
     * @return 策略视图对象
     */
    ParkingSpacePolicyVO create(ParkingSpacePolicyCreateCmd cmd);

    /**
     * 更新车位管控策略。
     *
     * @param id  策略ID
     * @param cmd 更新命令
     * @return 策略视图对象
     */
    ParkingSpacePolicyVO update(Long id, ParkingSpacePolicyCreateCmd cmd);

    /**
     * 删除车位管控策略。
     *
     * @param id 策略ID
     */
    void delete(Long id);

    /**
     * 查询策略详情。
     *
     * @param id 策略ID
     * @return 策略视图对象
     */
    ParkingSpacePolicyVO detail(Long id);

    /**
     * 分页查询策略列表。
     *
     * @param page         分页参数
     * @param parkingLotId 停车场ID（可选）
     * @return 分页结果
     */
    IPage<ParkingSpacePolicyVO> pageList(IPage<ParkingSpacePolicy> page, Long parkingLotId);

    /**
     * 查询指定停车场的车位管控策略。
     *
     * @param parkingLotId 停车场ID
     * @return 策略列表
     */
    List<ParkingSpacePolicyVO> listByParkingLotId(Long parkingLotId);

    /**
     * 查询指定区域的车位管控策略。
     *
     * @param zoneId 区域ID
     * @return 策略视图对象
     */
    ParkingSpacePolicyVO getByZoneId(Long zoneId);

    /**
     * 计算并返回余位信息。
     *
     * @param parkingLotId 停车场ID
     * @return 余位信息（总车位、已用、剩余）
     */
    ParkingSpaceRemainVO calculateRemain(Long parkingLotId);
}
