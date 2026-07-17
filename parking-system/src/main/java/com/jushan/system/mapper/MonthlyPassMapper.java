package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.MonthlyPass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 月卡 Mapper（任务包 3-1）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Mapper
public interface MonthlyPassMapper extends BaseMapper<MonthlyPass> {

    /**
     * 查询生效中的月卡（用于车辆类型判定）。
     *
     * @param tenantId    租户ID
     * @param plateNumber 车牌号（标准化大写）
     * @param today       当前日期
     * @return 生效中的月卡，无则 null
     */
    @Select("SELECT * FROM monthly_pass WHERE tenant_id = #{tenantId} AND plate_number = #{plateNumber} AND pass_status = 'ACTIVE' AND valid_start_date <= #{today} AND valid_end_date >= #{today} AND deleted_at IS NULL LIMIT 1")
    MonthlyPass selectActiveByPlate(@Param("tenantId") Long tenantId,
                                     @Param("plateNumber") String plateNumber,
                                     @Param("today") LocalDate today);

    /**
     * 过期扫描：将到期月卡批量置为 EXPIRED。
     *
     * @param today 当前日期
     * @return 更新行数
     */
    @Update("UPDATE monthly_pass SET pass_status = 'EXPIRED', updated_at = NOW() WHERE pass_status = 'ACTIVE' AND valid_end_date < #{today}")
    int expireActivePasses(@Param("today") LocalDate today);
}
