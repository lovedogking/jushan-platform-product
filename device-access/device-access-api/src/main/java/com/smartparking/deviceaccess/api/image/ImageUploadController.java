package com.smartparking.deviceaccess.api.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 芊熠相机抓拍图片独立上传接口（协议 §7.1.2）。
 * <p>
 * 相机端网络配置 {@code alonepush=1} 且勾选「发送图片/发送小图片」后，
 * 抓拍时通过 HTTP multipart POST 主动上传图片到本接口；
 * 原 MQTT result 消息中不再携带 base64 图片数据。
 * <p>
 * 表单字段（协议规定）：
 * <ul>
 *   <li>{@code sn}：相机序列号（必填）</li>
 *   <li>{@code plateSignTime}：车辆识别时间（UTC 秒，与 result 上报的 utc_ts 关联）</li>
 *   <li>{@code bigFile}：全景图片数据（可选，未勾选「发送图片」时不传）</li>
 *   <li>{@code smallFile}：车牌图片数据（可选，未勾选「发送小图片」时不传）</li>
 * </ul>
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
     *
     * @param sn            相机序列号
     * @param plateSignTime 识别时间（UTC 秒）
     * @param bigFile       全景图（可选）
     * @param smallFile     车牌特写图（可选）
     * @return 协议应答；失败时 data=false
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam String sn,
                                      @RequestParam long plateSignTime,
                                      @RequestParam(required = false) MultipartFile bigFile,
                                      @RequestParam(required = false) MultipartFile smallFile) {
        try {
            imageStorageService.save(sn, plateSignTime, bigFile, smallFile);
            return reply(200, "ok", true);
        } catch (IllegalArgumentException e) {
            log.warn("图片上传参数非法: sn={}, plateSignTime={}, error={}", sn, plateSignTime, e.getMessage());
            return reply(400, e.getMessage(), false);
        } catch (IOException e) {
            log.error("图片上传落盘失败: sn={}, plateSignTime={}, error={}", sn, plateSignTime, e.getMessage(), e);
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
