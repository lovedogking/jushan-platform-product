package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 停车场档案实体。
 * <p>
 * 对齐 PRD V1.0 停车场档案定义，继承 {@link BaseEntity} 统一规范。
 * totalSpaces 由区域自动汇总，不可手动编辑。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_lot")
public class ParkingLot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属公司 ID（逻辑外键：sys_company.id） */
    private Long companyId;

    /** 所属集团 ID（冗余，用于快速按集团查询） */
    private Long groupId;

    /** 停车场名称 */
    private String name;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 区县 */
    private String district;

    /** 区域类型：1商场 2写字楼 3住宅小区 4医院 5景区 6交通枢纽 */
    private Integer regionType;

    /** 详细地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 状态：1营业中 2暂停营业 3装修升级 */
    private Integer status;

    /** 营业时间，如 00:00-24:00 */
    private String businessHours;

    /** 总车位数（自动汇总区域，不可手动编辑） */
    private Integer totalSpaces;

    /** 车场图片 URL 数组（JSON 格式，最多 5 张） */
    private String images;

    /** 乐观锁版本号 */
    private Integer version;
}
