package com.jushan.platform.modules.booth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.booth.entity.ShiftRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 交接班记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ShiftRecordMapper extends BaseMapper<ShiftRecord> {

    /**
     * 查询指定操作员的当前开班记录。
     *
     * @param operatorId 操作员ID
     * @param tenantId   租户ID
     * @return 开班记录
     */
    @Select("SELECT * FROM shift_record WHERE operator_id = #{operatorId} AND tenant_id = #{tenantId} AND handover_status = 'OPEN' AND deleted_at IS NULL ORDER BY start_time DESC LIMIT 1")
    ShiftRecord selectOpenByOperator(@Param("operatorId") Long operatorId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定停车场的交接班记录。
     *
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 记录列表
     */
    @Select("SELECT * FROM shift_record WHERE parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND deleted_at IS NULL ORDER BY start_time DESC")
    List<ShiftRecord> selectByParkingLotId(@Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);
}
