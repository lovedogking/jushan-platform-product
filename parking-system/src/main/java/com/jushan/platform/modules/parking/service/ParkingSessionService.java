package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.vo.ParkingSessionVO;

import java.util.List;

/**
 * 在场车辆管理服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface ParkingSessionService extends IService<ParkingSession> {

    /**
     * 车辆入场记录。
     *
     * @param cmd 入场命令
     * @return 在场记录视图对象
     */
    ParkingSessionVO entry(ParkingSessionEntryCmd cmd);

    /**
     * 车辆出场记录。
     *
     * @param cmd 出场命令
     * @return 在场记录视图对象
     */
    ParkingSessionVO exit(ParkingSessionExitCmd cmd);

    /**
     * 查询在场记录详情。
     *
     * @param id 记录ID
     * @return 在场记录视图对象
     */
    ParkingSessionVO detail(Long id);

    /**
     * 分页查询在场记录列表。
     *
     * @param page         分页参数
     * @param parkingLotId 停车场ID（可选）
     * @param plateNumber  车牌号（可选）
     * @param status       状态（可选）
     * @return 分页结果
     */
    IPage<ParkingSessionVO> pageList(IPage<ParkingSession> page, Long parkingLotId, String plateNumber, String status);

    /**
     * 查询指定停车场的在场车辆列表。
     *
     * @param parkingLotId 停车场ID
     * @return 在场车辆列表
     */
    List<ParkingSessionVO> listInByParkingLotId(Long parkingLotId);

    /**
     * 根据车牌号查询在场记录。
     *
     * @param plateNumber 车牌号
     * @return 在场记录
     */
    ParkingSessionVO getInByPlateNumber(String plateNumber);

    /**
     * 根据车牌号和停车场ID查询在场记录。
     *
     * @param plateNumber  车牌号
     * @param parkingLotId 停车场ID
     * @return 在场记录
     */
    ParkingSessionVO getInByPlateAndLot(String plateNumber, Long parkingLotId);

    /**
     * 统计指定停车场的在场车辆数量。
     *
     * @param parkingLotId 停车场ID
     * @return 在场车辆数量
     */
    long countInByParkingLotId(Long parkingLotId);

    /**
     * 统计指定停车场的在场车辆数量（忽略租户上下文）。
     * <p>
     * 用于 Webhook 入场/出场链路等无租户上下文场景下的车位数计算。
     *
     * @param parkingLotId 停车场ID
     * @return 在场车辆数量
     */
    long countInByParkingLotIdIgnoreTenant(Long parkingLotId);

    /**
     * 查询指定车牌在指定停车场最近一条已出场记录（限定时间窗口内）。
     * <p>
     * 用于出场识别幂等：开闸放行后相机持续上报同一车辆，
     * 若最近已成功出场则判定为重复识别。
     *
     * @param plateNumber    车牌号
     * @param parkingLotId   停车场ID
     * @param withinSeconds  时间窗口（秒），exitTime 在此窗口内才算重复
     * @return 最近的已出场记录，无则返回 null
     */
    ParkingSessionVO getRecentOutByPlateAndLot(String plateNumber, Long parkingLotId, int withinSeconds);
}
