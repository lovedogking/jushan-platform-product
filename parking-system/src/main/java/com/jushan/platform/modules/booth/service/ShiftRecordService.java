package com.jushan.platform.modules.booth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.booth.dto.ShiftCloseCmd;
import com.jushan.platform.modules.booth.dto.ShiftStartCmd;
import com.jushan.platform.modules.booth.entity.ShiftRecord;
import com.jushan.platform.modules.booth.vo.ShiftRecordVO;

import java.util.List;

/**
 * 交接班管理服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface ShiftRecordService extends IService<ShiftRecord> {

    /**
     * 开班。
     *
     * @param cmd 开班命令
     * @return 交接班记录视图对象
     */
    ShiftRecordVO startShift(ShiftStartCmd cmd);

    /**
     * 交班。
     *
     * @param cmd 交班命令
     * @return 交接班记录视图对象
     */
    ShiftRecordVO closeShift(ShiftCloseCmd cmd);

    /**
     * 查询当前开班记录。
     *
     * @return 交接班记录视图对象
     */
    ShiftRecordVO getCurrentShift();

    /**
     * 查询交接班详情。
     *
     * @param id 记录ID
     * @return 交接班记录视图对象
     */
    ShiftRecordVO detail(Long id);

    /**
     * 分页查询交接班记录。
     *
     * @param page         分页参数
     * @param parkingLotId 停车场ID（可选）
     * @param status       交接状态（可选）
     * @return 分页结果
     */
    IPage<ShiftRecordVO> pageList(IPage<ShiftRecord> page, Long parkingLotId, String status);

    /**
     * 查询指定停车场的交接班记录。
     *
     * @param parkingLotId 停车场ID
     * @return 记录列表
     */
    List<ShiftRecordVO> listByParkingLotId(Long parkingLotId);
}
