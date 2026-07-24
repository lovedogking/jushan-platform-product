// @Deprecated — 迁移至 com.jushan.platform.modules.common.controller.FileUploadController
package com.jushan.platform.modules.common.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传 Controller（包 6-1：车场图片上传）。
 * <p>
 * 允许上传图片文件（jpg/png/gif/webp），存储到本地 uploads 目录，
 * 返回可访问的 URL 路径。静态资源映射由 Spring Boot 或 Nginx 提供。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            log.error("无法创建上传目录: {}", uploadPath, e);
        }
    }

    /**
     * 上传图片文件。
     *
     * @param file 图片文件（≤5MB）
     * @return { url: "/uploads/20260718/xxx.jpg" }
     */
    @PostMapping("/upload")
    @RequirePermission("parking:write")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail(400, "文件为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return R.fail(400, "仅支持图片文件");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return R.fail(400, "文件大小不能超过 5MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
            if (!ext.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                return R.fail(400, "不支持的图片格式: " + ext);
            }
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            Path datePath = uploadPath.resolve(dateDir);
            Files.createDirectories(datePath);
            Path targetPath = datePath.resolve(fileName);
            file.transferTo(targetPath);

            String url = "/uploads/" + dateDir + "/" + fileName;
            log.info("文件上传成功: {}", url);
            return R.ok(Map.of("url", url));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return R.fail(500, "文件上传失败");
        }
    }
}
