package com.jushan.boot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 演示用 DTO — 验证参数校验是否正确触发。
 */
public class DemoRequest {

    @NotBlank(message = "名称不能为空")
    @Size(min = 2, max = 20, message = "名称长度需在 2-20 之间")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
