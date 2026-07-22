package com.smartparking.deviceaccess.api.image;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 抓拍图片模块配置。
 * <p>
 * 注册 {@code GET /images/**} 静态资源映射，供本地开发直连 DA 调试图片访问；
 * 生产环境由 nginx alias 直接读磁盘（方案 B），不经过本映射。
 *
 * @since v0.6
 */
@Configuration
@EnableConfigurationProperties(ImageProperties.class)
public class ImageWebConfig implements WebMvcConfigurer {

    private final ImageProperties properties;

    public ImageWebConfig(ImageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize()
                .toUri().toString();
        registry.addResourceHandler("/images/**").addResourceLocations(location);
    }
}
