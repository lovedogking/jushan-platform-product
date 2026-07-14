package com.jushan.boot.config;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描配置（解决同名 Mapper Bean 冲突）。
 * <p>
 * {@code com.jushan.system.mapper} 与 {@code com.jushan.platform.modules.**.mapper}
 * 中存在同名接口（如 ParkingLaneMapper、ParkingLotMapper），使用 Java Config
 * 显式配置两个 {@link MapperScannerConfigurer}，并为 modules 包下的 Mapper
 * 添加前缀，避免 Spring Bean 冲突。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
public class MyBatisConfig {

    /**
     * 扫描 system.mapper 包（无前缀）。
     */
    @Bean
    public MapperScannerConfigurer systemMapperScanner() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.jushan.system.mapper");
        return configurer;
    }

    /**
     * 扫描 modules 下的 mapper 包（添加 "modules" 前缀）。
     */
    @Bean
    public MapperScannerConfigurer modulesMapperScanner() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.jushan.platform.modules.**.mapper");
        configurer.setNameGenerator(new AnnotationBeanNameGenerator() {
            @Override
            protected String buildDefaultBeanName(BeanDefinition definition) {
                return "modules" + super.buildDefaultBeanName(definition);
            }
        });
        return configurer;
    }
}
