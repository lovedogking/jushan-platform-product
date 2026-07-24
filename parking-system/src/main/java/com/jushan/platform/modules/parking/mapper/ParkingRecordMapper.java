package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
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

    /**
     * 按标准化车牌列表查询停车记录（绕过租户行级过滤器）。
     * <p>
     * 供小程序车主端使用：wx 用户无 tenantId，通过车牌归属验证保证安全。
     * 调用方必须校验请求车牌属于当前用户。
     *
     * @param plates 标准化车牌号列表
     * @return 匹配的停车记录列表（按入场时间降序）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>" +
            "SELECT * FROM parking_record WHERE standardized_plate IN " +
            "<foreach collection='plates' item='plate' open='(' separator=',' close=')'>" +
            "#{plate}" +
            "</foreach>" +
            " AND deleted_at IS NULL ORDER BY entry_time DESC" +
            "</script>")
    List<ParkingRecord> selectByPlates(@Param("plates") List<String> plates);

    /**
     * 按标准化车牌列表分页查询停车记录（绕过租户行级过滤器）。
     *
     * @param page  分页参数
     * @param plates 标准化车牌号列表
     * @return 分页结果
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>" +
            "SELECT * FROM parking_record WHERE standardized_plate IN " +
            "<foreach collection='plates' item='plate' open='(' separator=',' close=')'>" +
            "#{plate}" +
            "</foreach>" +
            " AND deleted_at IS NULL ORDER BY entry_time DESC" +
            "</script>")
    IPage<ParkingRecord> selectPageByPlates(IPage<ParkingRecord> page, @Param("plates") List<String> plates);

    /**
     * 按标准化车牌列表查询在场记录（绕过租户行级过滤器）。
     *
     * @param plates 标准化车牌号列表
     * @return 匹配的 PARKING 状态记录
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>" +
            "SELECT * FROM parking_record WHERE standardized_plate IN " +
            "<foreach collection='plates' item='plate' open='(' separator=',' close=')'>" +
            "#{plate}" +
            "</foreach>" +
            " AND status = 'PARKING' AND deleted_at IS NULL ORDER BY entry_time DESC" +
            "</script>")
    List<ParkingRecord> selectActiveByPlates(@Param("plates") List<String> plates);
}
