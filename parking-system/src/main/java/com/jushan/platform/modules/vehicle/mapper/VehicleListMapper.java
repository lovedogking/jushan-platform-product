package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.VehicleList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VehicleListMapper extends BaseMapper<VehicleList> {

    @Select("SELECT * FROM vehicle_list WHERE parking_lot_id = #{parkingLotId} "
            + "AND plate_number = #{plateNumber} AND list_type = 'WHITE' "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    VehicleList selectActiveWhite(@Param("parkingLotId") Long parkingLotId,
                                   @Param("plateNumber") String plateNumber);

    @Select("SELECT * FROM vehicle_list WHERE parking_lot_id = #{parkingLotId} "
            + "AND plate_number = #{plateNumber} AND list_type = 'BLACK' "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    VehicleList selectActiveBlack(@Param("parkingLotId") Long parkingLotId,
                                   @Param("plateNumber") String plateNumber);

    @Select("SELECT * FROM vehicle_list WHERE parking_lot_id = #{parkingLotId} "
            + "AND plate_number = #{plateNumber} AND list_type = #{listType} "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    VehicleList selectByLotAndPlateAndType(@Param("parkingLotId") Long parkingLotId,
                                            @Param("plateNumber") String plateNumber,
                                            @Param("listType") String listType);

    @Select("SELECT * FROM vehicle_list WHERE end_date < CURRENT_DATE "
            + "AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT #{limit}")
    List<VehicleList> selectExpired(@Param("limit") int limit);
}
