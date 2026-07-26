package com.jushan.platform.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * H5 车主端 CORS 配置。
 * <p>
 * 允许 H5 前端（开发 localhost:3002，生产 h5.jushan-parking.com）跨域访问后端 API。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
public class H5CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:3002");
        config.addAllowedOrigin("https://h5.jushan-parking.com");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/h5/**", config);

        return new CorsFilter(source);
    }
}
