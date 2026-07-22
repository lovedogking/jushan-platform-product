package com.smartparking.deviceaccess.api.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 远程抓拍图片下载转存器。
 * <p>
 * 臻识等品牌相机的识别事件仅携带远程图片地址（如阿里云 OSS 签名 URL，
 * 约 1 小时后过期），平台无法长期展示。本组件在事件到达时立即下载图片
 * 并转存到本地存储（与芊熠独立上传同一目录结构），替换为本地可访问 URL。
 * <p>
 * 下载或落盘失败时返回 null，调用方应保留原始远程地址（限时内仍可访问）。
 *
 * @since v0.7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteImageDownloader {

    private final ImageStorageService imageStorageService;

    /** 最大尝试次数：首次失败后按 1s/2s 退避重试（应对相机 OSS 上传与事件到达的竞态 404） */
    private static final int MAX_ATTEMPTS = 3;

    private static final long[] RETRY_DELAYS_MS = {0, 1000, 2000};

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 下载远程图片并转存本地。
     *
     * @param sn           设备序列号
     * @param epochSeconds 识别时间（UTC 秒，决定落盘目录与文件名）
     * @param remoteUrl    远程图片地址（http/https）
     * @param plateImage   true=车牌特写图，false=全景图
     * @return 本地可访问 URL；下载或落盘失败返回 null
     */
    public String downloadAndStore(String sn, long epochSeconds, String remoteUrl, boolean plateImage) {
        if (remoteUrl == null || !remoteUrl.startsWith("http")) {
            return null;
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(RETRY_DELAYS_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            String localUrl = doDownload(sn, epochSeconds, remoteUrl, plateImage, attempt + 1);
            if (localUrl != null) {
                return localUrl;
            }
        }
        return null;
    }

    /** 单次下载尝试，成功返回本地 URL，失败返回 null */
    private String doDownload(String sn, long epochSeconds, String remoteUrl, boolean plateImage, int attempt) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(remoteUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() != 200 || body == null || body.length == 0) {
                log.warn("远程图片下载失败(第{}次): sn={}, status={}, url={}", attempt, sn, response.statusCode(), abbrev(remoteUrl));
                return null;
            }
            if (plateImage) {
                imageStorageService.saveBytes(sn, epochSeconds, null, body);
            } else {
                imageStorageService.saveBytes(sn, epochSeconds, body, null);
            }
            String localUrl = plateImage
                    ? imageStorageService.buildPlateImageUrl(sn, epochSeconds)
                    : imageStorageService.buildFullImageUrl(sn, epochSeconds);
            log.info("远程图片已转存本地: sn={}, size={}B, plateImage={}, localUrl={}",
                    sn, body.length, plateImage, localUrl);
            return localUrl;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("远程图片转存被中断: sn={}, url={}", sn, abbrev(remoteUrl));
            return null;
        } catch (Exception e) {
            log.warn("远程图片转存失败: sn={}, url={}, error={}", sn, abbrev(remoteUrl), e.getMessage());
            return null;
        }
    }

    /** 日志中截断超长 URL（OSS 签名地址较长） */
    private String abbrev(String url) {
        return url != null && url.length() > 120 ? url.substring(0, 120) + "..." : url;
    }
}
