package com.smartparking.deviceaccess.api.image;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 抓拍图片存储配置。
 * <p>
 * 对应 application.yml 的 {@code device-access.image.*}：
 * <ul>
 *   <li>{@code storage-dir}：图片本地存储根目录（生产与 nginx 共享同一宿主目录）</li>
 *   <li>{@code public-base-url}：图片对外访问基础 URL（nginx 反代入口，如 {@code https://域名/images}）；
 *       本地开发可直连 DA，如 {@code http://localhost:8082/images}</li>
 *   <li>{@code retention-days}：图片保留天数，到期由 {@link ImageStorageService#cleanExpiredImages()} 清理</li>
 * </ul>
 *
 * @since v0.6
 */
@Data
@ConfigurationProperties(prefix = "device-access.image")
public class ImageProperties {

    /** 图片本地存储根目录 */
    private String storageDir = "./data/images";

    /** 图片对外访问基础 URL（不含尾部路径） */
    private String publicBaseUrl = "http://localhost:8082/images";

    /** 图片保留天数（默认 30 天） */
    private int retentionDays = 30;
}
