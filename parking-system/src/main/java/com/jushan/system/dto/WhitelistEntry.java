package com.jushan.system.dto;

import java.time.LocalDate;

/**
 * 白名单同步单条条目（任务包 7-1）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class WhitelistEntry {

    /** 车牌号 */
    private String plateNumber;

    /** 类型：MONTHLY_PASS / FIXED_SPACE / WHITELIST */
    private String type;

    /** 车位号（仅 FIXED_SPACE 类型有值） */
    private String spotCode;

    /** 过期时间（MONTHLY_PASS / FIXED_SPACE 有值，WHITELIST 为 null） */
    private LocalDate expireAt;

    // ==================== getter / setter ====================

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSpotCode() { return spotCode; }
    public void setSpotCode(String spotCode) { this.spotCode = spotCode; }

    public LocalDate getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDate expireAt) { this.expireAt = expireAt; }
}
