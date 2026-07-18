package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;

public class MiniPhoneRequest {
    @NotBlank(message = "手机号凭证不能为空")
    private String code;
    private String iv;
    private String encryptedData;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
}
