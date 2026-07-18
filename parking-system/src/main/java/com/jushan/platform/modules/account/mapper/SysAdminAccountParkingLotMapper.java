package com.jushan.platform.modules.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.account.entity.SysAdminAccountParkingLot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysAdminAccountParkingLotMapper extends BaseMapper<SysAdminAccountParkingLot> {

    @Select("SELECT parking_lot_id FROM sys_admin_account_parking_lot WHERE admin_account_id = #{adminAccountId} AND tenant_id = #{tenantId}")
    List<Long> selectParkingLotIdsByAccountId(@Param("adminAccountId") Long adminAccountId,
                                               @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM sys_admin_account_parking_lot WHERE admin_account_id = #{adminAccountId}")
    int deleteByAdminAccountId(@Param("adminAccountId") Long adminAccountId);
}
