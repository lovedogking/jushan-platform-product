package com.jushan.platform.modules.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 账号模块安全配置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
public class AccountSecurityConfig {

    /**
     * BCrypt 密码编码器。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
