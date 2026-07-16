package com.jushan.system.dto;

/**
 * 系统参数更新请求（A3）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class SystemParamUpdateRequest {

    /** 新的参数值 */
    private String value;

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
