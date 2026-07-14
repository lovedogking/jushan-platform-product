package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.ParkingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 停车记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingRecordMapper extends BaseMapper<ParkingRecord> {

    /**
     * 查询指定车牌在指定停车场的活跃（PARKING）记录。
     *
     * @param parkingLotId      停车场 ID
     * @param standardizedPlate 标准化车牌号
     * @return 存在的 PARKING 记录列表
     */
    @Select("SELECT * FROM parking_record WHERE parking_lot_id = #{parkingLotId} AND standardized_plate = #{standardizedPlate} AND status = 'PARKING' AND deleted_at IS NULL ORDER BY entry_time DESC")
    List<ParkingRecord> selectActiveByPlate(@Param("parkingLotId") Long parkingLotId, @Param("standardizedPlate") String standardizedPlate);
}
