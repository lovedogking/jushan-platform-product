package com.jushan.platform.modules.miniapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.miniapp.entity.VisitorApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 访客预约申请 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface VisitorApplyMapper extends BaseMapper<VisitorApply> {

    /**
     * 查询指定停车场的访客预约列表。
     *
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 预约列表
     */
    @Select("SELECT * FROM visitor_apply WHERE parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND deleted_at IS NULL ORDER BY visit_date DESC, created_at DESC")
    List<VisitorApply> selectByParkingLotId(@Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定申请人的访客预约列表。
     *
     * @param applicantId 申请人ID
     * @param tenantId    租户ID
     * @return 预约列表
     */
    @Select("SELECT * FROM visitor_apply WHERE applicant_id = #{applicantId} AND tenant_id = #{tenantId} AND deleted_at IS NULL ORDER BY created_at DESC")
    List<VisitorApply> selectByApplicantId(@Param("applicantId") Long applicantId, @Param("tenantId") Long tenantId);
}
