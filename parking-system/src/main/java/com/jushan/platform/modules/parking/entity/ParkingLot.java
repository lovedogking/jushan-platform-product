package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 停车场实体（T18 完整字段）。
 * <p>
 * 对齐 DB parking_lot 表所有字段，继承 {@link BaseEntity} 统一规范。
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

    /** DB 使用 AUTO_INCREMENT，覆盖 BaseEntity 的 ASSIGN_ID */
    @TableId(type = IdType.AUTO)
    private Long id;

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

    /** 状态：ENABLED-启用, DISABLED-禁用 */
    private String status;

    /** 总车位数（自动汇总区域，不可手动编辑） */
    private Integer totalSpaces;

    /** 当前在场车辆数（只读，由停车记录计算） */
    private Integer currentVehicles;

    /** 剩余车位数（默认 = totalSpaces - currentVehicles，允许人工修正） */
    private Integer remainingSpaces;

    /** 支付模式：PLATFORM-平台统一商户, CUSTOMER-客户独立商户 */
    private String paymentMode;

    /** 抓拍图片保存天数 */
    private Integer imageRetentionDays;

    /** 业务数据保存天数 */
    private Integer dataRetentionDays;

    /** 绑定的计费规则 ID（逻辑外键：fee_rule.id） */
    private Long feeRuleId;

    /** 缴费后免费离场时间（分钟） */
    private Integer freeExitMinutes;

    /** 人工放行策略：ADMIN_ONLY-仅管理员, BOOTH_ALLOWED-岗亭可放行 */
    private String manualReleasePolicy;

    /** 离线运行策略：ALLOW_ENTRY_EXIT-允许出入, ALLOW_EXIT_ONLY-只出不进, STRICT-禁止通行 */
    private String offlinePolicy;

    /** 重复入场策略：REJECT-拒绝, UPDATE-更新原记录, EXCEPTION-创建异常记录 */
    private String duplicateEntryPolicy;

    /** 车场图片 URL 数组（JSON 格式，最多 5 张） */
    private String images;

    /** 营业时间，如 00:00-24:00 */
    private String businessHours;

    /** 乐观锁版本号 */
    private Integer version;

    /** 停用时是否允许新车入场：1-允许, 0-禁止（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableNewEntries;

    /** 停用时是否允许缴费：1-允许, 0-禁止（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disablePayment;

    /** 停用时是否允许出场：1-允许, 0-禁止（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableExit;

    /** 停用时是否允许自动开闸：1-保留, 0-关闭（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableAutoGate;

    /** 停用时是否仅限制后台配置：1-是, 0-否（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableOnlyConfig;
}
