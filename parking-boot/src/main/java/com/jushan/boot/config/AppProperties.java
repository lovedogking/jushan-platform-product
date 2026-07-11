package com.jushan.boot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 应用级配置属性（示例 + 后续扩展）。
 * <p>
 * 本类演示 {@code @ConfigurationProperties + @Validated} 的用法。
 * 当对应属性出现在配置中时，Spring Boot 自动校验。
 * 各业务模块后续可在自己的 {@code application-*.yml} 中追加业务级配置块。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 平台名称（展示用） */
    @NotBlank
    private String name = "智慧停车 SaaS 平台";

    /** 文件上传大小限制（MB） */
    @Min(1)
    @Max(100)
    private int uploadMaxSizeMb = 20;

    /** 超级管理员初始密码（仅首次部署时有效，之后由数据库管理） */
    private String initialAdminPassword;

    // ==================== getter / setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getUploadMaxSizeMb() { return uploadMaxSizeMb; }
    public void setUploadMaxSizeMb(int uploadMaxSizeMb) { this.uploadMaxSizeMb = uploadMaxSizeMb; }

    public String getInitialAdminPassword() { return initialAdminPassword; }
    public void setInitialAdminPassword(String initialAdminPassword) { this.initialAdminPassword = initialAdminPassword; }
}
