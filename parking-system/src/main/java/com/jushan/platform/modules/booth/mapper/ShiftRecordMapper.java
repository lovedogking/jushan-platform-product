package com.jushan.platform.modules.booth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.booth.entity.ShiftRecord;
import com.jushan.platform.modules.booth.vo.ShiftRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
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

    /**
     * 统计本班期间订单金额汇总（分）。
     * parking_order 列名：amount_cents, pay_time
     */
    @Select("SELECT COALESCE(SUM(po.amount_cents), 0) FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status IN ('PAID', 'COMPLETED') "
            + "AND po.pay_time >= #{startTime} AND po.pay_time <= #{endTime}")
    Integer sumCashOrderFeeCents(@Param("parkingLotId") Long parkingLotId,
                                  @Param("tenantId") Long tenantId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /** 统计本班期间入场记录数 */
    @Select("SELECT COUNT(*) FROM parking_record pr "
            + "WHERE pr.parking_lot_id = #{parkingLotId} "
            + "AND pr.tenant_id = #{tenantId} "
            + "AND pr.entry_time >= #{startTime} AND pr.entry_time <= #{endTime} "
            + "AND pr.deleted_at IS NULL")
    int countEntries(@Param("parkingLotId") Long parkingLotId,
                     @Param("tenantId") Long tenantId,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    /** 统计本班期间出场记录数 */
    @Select("SELECT COUNT(*) FROM exit_record er "
            + "WHERE er.parking_lot_id = #{parkingLotId} "
            + "AND er.tenant_id = #{tenantId} "
            + "AND er.exit_time >= #{startTime} AND er.exit_time <= #{endTime}")
    int countExits(@Param("parkingLotId") Long parkingLotId,
                   @Param("tenantId") Long tenantId,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    /** 统计本班期间欠费订单数 */
    @Select("SELECT COUNT(*) FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status = 'ARREARS' "
            + "AND po.created_at >= #{startTime} AND po.created_at <= #{endTime}")
    int countArrearsOrders(@Param("parkingLotId") Long parkingLotId,
                            @Param("tenantId") Long tenantId,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /** 统计未支付/欠费订单数（交接给下一班） */
    @Select("SELECT COUNT(*) FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status IN ('PENDING_PAY', 'ARREARS')")
    int countHandoverOrders(@Param("parkingLotId") Long parkingLotId,
                             @Param("tenantId") Long tenantId);

    /** 查询本班欠费订单详情（用于交班预览） */
    @Select("SELECT po.id AS orderId, po.plate_number AS plateNumber, po.amount_cents AS feeCents, "
            + "po.created_at AS createdAt "
            + "FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status = 'ARREARS' "
            + "AND po.created_at >= #{startTime} AND po.created_at <= #{endTime} "
            + "ORDER BY po.created_at DESC")
    List<ShiftRecordVO.ArrearsOrderItem> listArrearsOrders(@Param("parkingLotId") Long parkingLotId,
                                                              @Param("tenantId") Long tenantId,
                                                              @Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);
}
