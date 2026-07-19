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

    /**
     * 按账号查询分配的停车场 ID 列表。
     * 注：岗亭管理员 tenant_id 为 NULL，故只按 admin_account_id 查询。
     */
    @Select("SELECT parking_lot_id FROM sys_admin_account_parking_lot WHERE admin_account_id = #{adminAccountId}")
    List<Long> selectParkingLotIdsByAccountId(@Param("adminAccountId") Long adminAccountId);

    @Delete("DELETE FROM sys_admin_account_parking_lot WHERE admin_account_id = #{adminAccountId}")
    int deleteByAdminAccountId(@Param("adminAccountId") Long adminAccountId);
}
