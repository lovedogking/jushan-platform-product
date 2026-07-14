package com.jushan.platform.modules.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.department.entity.SysDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门/组织架构 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {

    /**
     * 查询指定部门下的子部门数量。
     *
     * @param parentId 上级部门ID
     * @return 子部门数量
     */
    @Select("SELECT COUNT(*) FROM sys_department WHERE parent_id = #{parentId} AND deleted_at IS NULL")
    long countChildren(@Param("parentId") Long parentId);

    /**
     * 查询指定停车场下的所有部门。
     *
     * @param parkingLotId 停车场ID
     * @return 部门列表
     */
    @Select("SELECT * FROM sys_department WHERE parking_lot_id = #{parkingLotId} AND deleted_at IS NULL ORDER BY sort_order, id")
    List<SysDepartment> selectByParkingLotId(@Param("parkingLotId") Long parkingLotId);
}
