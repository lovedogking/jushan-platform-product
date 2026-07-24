package com.jushan.platform.modules.miniapp.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 微信小程序配置校验。
 * <p>
 * production 环境禁止启用 mock-login。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Configuration
@Profile("prod")
public class WxMiniappConfig {

    private static final Logger log = LoggerFactory.getLogger(WxMiniappConfig.class);

    @Value("${wx.mock-login:false}")
    private boolean mockLoginEnabled;

    @PostConstruct
    public void validate() {
        if (mockLoginEnabled) {
            throw new IllegalStateException(
                    "wx.mock-login=true is not allowed in production profile. "
                            + "Set wx.mock-login=false in production environment.");
        }
        log.info("微信小程序 Prod 保护生效: mock-login={}", mockLoginEnabled);
    }
}
