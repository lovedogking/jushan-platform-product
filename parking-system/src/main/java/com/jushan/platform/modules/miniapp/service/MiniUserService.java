package com.jushan.platform.modules.miniapp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.miniapp.vo.MiniParkingRecordVO;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;

import java.util.List;

/**
 * 小程序车主服务接口。
 * <p>
 * 提供停车记录查询、余位查询、车牌绑定等功能。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface MiniUserService {

    /**
     * 查询当前用户的停车记录。
     *
     * @param current 当前页
     * @param size    每页大小
     * @return 分页结果
     */
    IPage<MiniParkingRecordVO> listParkingRecords(long current, long size);

    /**
     * 查询指定车牌的停车记录。
     *
     * @param plateNumber 车牌号
     * @param current     当前页
     * @param size        每页大小
     * @return 分页结果
     */
    IPage<MiniParkingRecordVO> listParkingRecordsByPlate(String plateNumber, long current, long size);

    /**
     * 查询当前用户的在场记录。
     *
     * @return 在场记录列表
     */
    List<MiniParkingRecordVO> listCurrentSessions();

    /**
     * 查询停车场余位信息。
     *
     * @param parkingLotId 停车场ID
     * @return 余位信息
     */
    ParkingSpaceRemainVO getParkingLotRemain(Long parkingLotId);

    /**
     * 查询单条停车记录详情。
     *
     * @param id 停车记录 ID（ParkingRecord.id）
     * @return 记录详情
     */
    MiniParkingRecordVO getParkingRecordDetail(Long id);

    /**
     * 查询当前用户绑定的车牌列表。
     *
     * @return 车牌列表
     */
    List<String> listBoundPlates();
}
