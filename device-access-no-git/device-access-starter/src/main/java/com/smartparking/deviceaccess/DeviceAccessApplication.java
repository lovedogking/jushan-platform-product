package com.smartparking.deviceaccess;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Device Access 应用入口。
 * <p>
 * 自动扫描 com.smartparking.deviceaccess 下所有模块的 Spring Bean 和 MyBatis Mapper。
 */
@SpringBootApplication(scanBasePackages = "com.smartparking.deviceaccess")
@MapperScan({"com.smartparking.deviceaccess.common.mapper", "com.smartparking.deviceaccess.common.entity"})
@EnableScheduling
public class DeviceAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceAccessApplication.class, args);
    }
}
