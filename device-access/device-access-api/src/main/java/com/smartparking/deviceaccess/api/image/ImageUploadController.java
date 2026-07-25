package com.smartparking.deviceaccess.api.image;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 芊熠相机抓拍图片独立上传接口（协议 §7.1.2）。
 * <p>
 * 相机端网络配置 {@code alonepush=1} 且勾选「发送图片/发送小图片」后，
 * 抓拍时通过 HTTP multipart POST 主动上传图片到本接口；
 * 原 MQTT result 消息中不再携带 base64 图片数据。
 * <p>
 * 表单字段（兼容不同固件版本的参数名）：
 * <ul>
 *   <li>{@code sn} 或 {@code deviceId}：相机序列号（必填）</li>
 *   <li>{@code plateSignTime} 或 {@code timestamp} 或 {@code utc_ts}：识别时间（UTC 秒）</li>
 *   <li>{@code bigFile} 或 {@code image}：全景图片数据（可选）</li>
 *   <li>{@code smallFile} 或 {@code plateImage}：车牌特写图数据（可选）</li>
 * </ul>
 * <p>
 * 所有参数缺失时使用当前时间戳作为 fallback。
 * <p>
 * 应答格式按协议要求：{@code {"code":200,"msg":"ok","success":true,"data":true}}。
 * 相机无 ApiKey，本端点在 {@code ApiKeyAuthFilter} 中豁免认证；
 * 生产环境经 nginx 反代 {@code POST /images/upload → 本接口}。
 *
 * @since v0.6
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageStorageService imageStorageService;

    /**
     * 接收相机上传的抓拍图片并落盘。
     * <p>
     * 兼容不同固件版本的参数名：{@code sn/deviceId}、
     * {@code plateSignTime/timestamp/utc_ts}、{@code bigFile/image}、
     * {@code smallFile/plateImage}。
     * 参数缺失时记录所有接收到的参数名帮助排查。
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(HttpServletRequest request,
                                      @RequestParam(required = false) String sn,
                                      @RequestParam(required = false) String deviceId,
                                      @RequestParam(required = false) Long plateSignTime,
                                      @RequestParam(required = false) Long timestamp,
                                      @RequestParam(required = false) Long utc_ts,
                                      @RequestParam(required = false) MultipartFile bigFile,
                                      @RequestParam(required = false) MultipartFile image,
                                      @RequestParam(required = false) MultipartFile smallFile,
                                      @RequestParam(required = false) MultipartFile plateImage) {
        // 解析设备 SN（sn 优先，deviceId 兜底）
        String resolvedSn = sn != null ? sn : deviceId;
        if (resolvedSn == null || resolvedSn.isBlank()) {
            // 记录所有参数名帮助排查
            log.warn("图片上传缺少设备SN，收到参数: {}",
                    Collections.list(request.getParameterNames()));
            return reply(400, "missing sn/deviceId", false);
        }

        // 解析时间戳（plateSignTime → timestamp → utc_ts → 当前时间兜底）
        Long resolvedTs = plateSignTime != null ? plateSignTime
                : (timestamp != null ? timestamp : utc_ts);
        if (resolvedTs == null) {
            log.info("图片上传缺少时间戳，使用当前时间兜底: sn={}, 收到参数: {}",
                    resolvedSn, Collections.list(request.getParameterNames()));
            resolvedTs = Instant.now().getEpochSecond();
        }

        // 解析图片文件（优先标准名，兜底别名）
        MultipartFile resolvedBig = bigFile != null ? bigFile : image;
        MultipartFile resolvedSmall = smallFile != null ? smallFile : plateImage;

        try {
            imageStorageService.save(resolvedSn, resolvedTs, resolvedBig, resolvedSmall);
            return reply(200, "ok", true);
        } catch (IllegalArgumentException e) {
            log.warn("图片上传参数非法: sn={}, plateSignTime={}, error={}", resolvedSn, resolvedTs, e.getMessage());
            return reply(400, e.getMessage(), false);
        } catch (IOException e) {
            log.error("图片上传落盘失败: sn={}, plateSignTime={}, error={}", resolvedSn, resolvedTs, e.getMessage(), e);
            return reply(500, "save failed: " + e.getMessage(), false);
        }
    }

    /** 协议应答格式：code / msg / success / data */
    private Map<String, Object> reply(int code, String msg, boolean data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("success", code == 200);
        body.put("data", data);
        return body;
    }
}
