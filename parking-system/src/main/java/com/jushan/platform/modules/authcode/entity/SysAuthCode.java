package com.jushan.platform.modules.authcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 授权码实体。
 * <p>
 * 对应表 {@code sys_auth_code}，用于车场开通授权码的生成、激活与管理。
 * 激活前 {@code tenant_id} 为 null，由平台管理员生成；激活后绑定当前租户。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_code")
public class SysAuthCode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 授权码，格式 XXXX-XXXX-XXXX-XXXX */
    private String code;

    /** 可开通车场数量 */
    private Integer maxParkingCount;

    /** 有效期开始 */
    private LocalDate validStart;

    /** 有效期结束 */
    private LocalDate validEnd;

    /** 已使用次数 */
    private Integer usedCount;

    /** 最大使用次数 */
    private Integer maxUseCount;

    /** 功能版本类型：BASIC-基础版, STANDARD-标准版, PREMIUM-高级版 */
    private String versionType;

    /** 状态：0未使用, 1已激活, 2已过期, 3已禁用 */
    private Integer status;

    /** 激活人 ID */
    private Long activatedBy;

    /** 激活时间 */
    private LocalDateTime activatedAt;

    // ==================== 状态常量 ====================

    /** 状态：未使用 */
    public static final int STATUS_UNUSED = 0;
    /** 状态：已激活 */
    public static final int STATUS_ACTIVATED = 1;
    /** 状态：已过期 */
    public static final int STATUS_EXPIRED = 2;
    /** 状态：已禁用 */
    public static final int STATUS_DISABLED = 3;

    // ==================== 版本类型常量 ====================

    /** 版本类型：基础版 */
    public static final String VERSION_TYPE_BASIC = "BASIC";
    /** 版本类型：标准版 */
    public static final String VERSION_TYPE_STANDARD = "STANDARD";
    /** 版本类型：高级版 */
    public static final String VERSION_TYPE_PREMIUM = "PREMIUM";
}
