package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 在场车辆记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingSessionMapper extends BaseMapper<ParkingSession> {

    /**
     * 查询指定停车场的在场车辆列表。
     *
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 在场车辆列表
     */
    @Select("SELECT * FROM parking_session WHERE parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND status = 'IN' AND deleted_at IS NULL ORDER BY entry_time DESC")
    List<ParkingSession> selectInByParkingLotId(@Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);

    /**
     * 根据车牌号查询在场记录。
     *
     * @param plateNumber 车牌号
     * @param tenantId    租户ID
     * @return 在场记录
     */
    @Select("SELECT * FROM parking_session WHERE plate_number = UPPER(#{plateNumber}) AND tenant_id = #{tenantId} AND status = 'IN' AND deleted_at IS NULL ORDER BY entry_time DESC LIMIT 1")
    ParkingSession selectInByPlateNumber(@Param("plateNumber") String plateNumber, @Param("tenantId") Long tenantId);

    /**
     * 统计指定停车场的在场车辆数量。
     *
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 在场车辆数量
     */
    @Select("SELECT COUNT(*) FROM parking_session WHERE parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND status = 'IN' AND deleted_at IS NULL")
    long countInByParkingLotId(@Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);

    /**
     * 根据车牌号和停车场ID查询在场记录。
     *
     * @param plateNumber  车牌号
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 在场记录
     */
    @Select("SELECT * FROM parking_session WHERE plate_number = UPPER(#{plateNumber}) AND parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND status = 'IN' AND deleted_at IS NULL ORDER BY entry_time DESC LIMIT 1")
    ParkingSession selectInByPlateAndLot(@Param("plateNumber") String plateNumber, @Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);

    /**
     * 查询所有在场（IN）车辆记录，忽略租户拦截器。
     * <p>
     * 供超时停放自动拉黑定时任务使用：该任务在系统上下文（无租户）下运行，
     * 需要跨租户扫描全部在场车辆，再由调用方按各自租户写入黑名单。
     *
     * @return 全部在场车辆列表（按入场时间升序）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_session WHERE status = 'IN' AND deleted_at IS NULL ORDER BY entry_time ASC")
    List<ParkingSession> selectAllInSessions();

    /**
     * 更新出场信息（含 ParkingRecordId 关联）。
     *
     * @param id              记录ID
     * @param exitTime        出场时间
     * @param exitLaneId      出场通道ID
     * @param exitImage       出场图片
     * @param exitOperator    出场操作人
     * @param feeAmount       应收费用
     * @param parkingRecordId 关联停车记录ID
     * @return 影响行数
     */
    @Update("UPDATE parking_session SET status = 'OUT', exit_time = #{exitTime}, exit_lane_id = #{exitLaneId}, exit_image = #{exitImage}, exit_operator = #{exitOperator}, fee_amount = #{feeAmount}, parking_record_id = #{parkingRecordId}, updated_at = NOW() WHERE id = #{id}")
    int updateExitWithRecord(@Param("id") Long id, @Param("exitTime") java.time.LocalDateTime exitTime,
                             @Param("exitLaneId") Long exitLaneId, @Param("exitImage") String exitImage,
                             @Param("exitOperator") Long exitOperator, @Param("feeAmount") java.math.BigDecimal feeAmount,
                             @Param("parkingRecordId") Long parkingRecordId);
}
