package com.smartparking.deviceaccess.api.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 抓拍图片存储服务。
 * <p>
 * 负责芊熠相机「独立上传图片」（协议 §7.1.2）的落盘与对外 URL 构造：
 * <ul>
 *   <li>存储结构：{@code {storageDir}/{yyyyMMdd}/{sn}/{plateSignTime}.jpg}（全景图）、
 *       {@code {plateSignTime}_plate.jpg}（车牌特写图）</li>
 *   <li>日期目录按相机识别时间（plateSignTime/utc_ts）换算，与
 *       {@link #buildFullImageUrl}/{@link #buildPlateImageUrl} 构造规则保持一致，
 *       不依赖服务器时钟</li>
 *   <li>对外 URL：{@code {publicBaseUrl}/{yyyyMMdd}/{sn}/{plateSignTime}.jpg}，
 *       生产由 nginx alias 直接读磁盘（方案 B），不经过本服务</li>
 * </ul>
 * <p>
 * 后续接 OSS 时仅需替换本类的存储与 URL 构造实现（保留本地模式作降级）。
 *
 * @since v0.6
 */
@Slf4j
@Service
public class ImageStorageService {

    /** 全景图文件名模板 */
    private static final String FULL_IMAGE_NAME = "%d.jpg";

    /** 车牌特写图文件名模板 */
    private static final String PLATE_IMAGE_NAME = "%d_plate.jpg";

    /** 日期目录格式（按识别时间） */
    private static final DateTimeFormatter DATE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 设备 SN 安全字符白名单（防目录穿越） */
    private static final Pattern SAFE_SN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ImageProperties properties;

    public ImageStorageService(ImageProperties properties) {
        this.properties = properties;
    }

    /**
     * 保存相机上传的抓拍图片。
     *
     * @param sn            相机序列号
     * @param plateSignTime 识别时间（UTC 秒，与结果上报 utc_ts 一致）
     * @param bigFile       全景图（可为 null）
     * @param smallFile     车牌特写图（可为 null）
     * @throws IOException 落盘失败
     * @throws IllegalArgumentException 参数非法（sn 含非法字符等）
     */
    public void save(String sn, long plateSignTime, MultipartFile bigFile, MultipartFile smallFile)
            throws IOException {
        validateSn(sn);
        if (plateSignTime <= 0) {
            throw new IllegalArgumentException("plateSignTime must be positive");
        }
        Path dir = resolveDir(sn, plateSignTime);
        Files.createDirectories(dir);

        if (bigFile != null && !bigFile.isEmpty()) {
            writeFile(dir.resolve(String.format(FULL_IMAGE_NAME, plateSignTime)), bigFile);
        }
        if (smallFile != null && !smallFile.isEmpty()) {
            writeFile(dir.resolve(String.format(PLATE_IMAGE_NAME, plateSignTime)), smallFile);
        }
        log.info("抓拍图片已保存: sn={}, plateSignTime={}, hasFull={}, hasPlate={}",
                sn, plateSignTime, bigFile != null && !bigFile.isEmpty(),
                smallFile != null && !smallFile.isEmpty());
    }

    /**
     * 保存字节形式图片（用于远程图片本地化转存：臻识等品牌事件携带的
     * OSS 签名 URL 为限时地址，由平台下载后落盘为本地永久可访问图片）。
     *
     * @param sn           设备序列号
     * @param epochSeconds 识别时间（UTC 秒）
     * @param fullImage    全景图字节（可为 null）
     * @param plateImage   车牌特写图字节（可为 null）
     * @throws IOException 落盘失败
     */
    public void saveBytes(String sn, long epochSeconds, byte[] fullImage, byte[] plateImage)
            throws IOException {
        validateSn(sn);
        if (epochSeconds <= 0) {
            throw new IllegalArgumentException("epochSeconds must be positive");
        }
        Path dir = resolveDir(sn, epochSeconds);
        Files.createDirectories(dir);
        if (fullImage != null && fullImage.length > 0) {
            Files.write(dir.resolve(String.format(FULL_IMAGE_NAME, epochSeconds)), fullImage);
        }
        if (plateImage != null && plateImage.length > 0) {
            Files.write(dir.resolve(String.format(PLATE_IMAGE_NAME, epochSeconds)), plateImage);
        }
    }

    /**
     * 构造全景图对外访问 URL（按识别时间推导，无需等图片落盘）。
     *
     * @param sn           相机序列号
     * @param epochSeconds 识别时间（UTC 秒，result 上报的 utc_ts）
     * @return 可访问 URL；sn 非法时返回 null
     */
    public String buildFullImageUrl(String sn, long epochSeconds) {
        return buildUrl(sn, epochSeconds, FULL_IMAGE_NAME);
    }

    /**
     * 构造车牌特写图对外访问 URL。
     *
     * @param sn           相机序列号
     * @param epochSeconds 识别时间（UTC 秒）
     * @return 可访问 URL；sn 非法时返回 null
     */
    public String buildPlateImageUrl(String sn, long epochSeconds) {
        return buildUrl(sn, epochSeconds, PLATE_IMAGE_NAME);
    }

    /**
     * 定时清理过期图片（每日凌晨 03:20，删除 retentionDays 之前的日期目录）。
     */
    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanExpiredImages() {
        LocalDate cutoff = LocalDate.now(ZONE).minusDays(properties.getRetentionDays());
        Path root = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> dateDirs = Files.list(root)) {
            dateDirs.filter(Files::isDirectory)
                    .filter(p -> isExpiredDateDir(p.getFileName().toString(), cutoff))
                    .forEach(this::deleteRecursively);
        } catch (IOException e) {
            log.warn("过期图片清理失败: {}", e.getMessage());
        }
    }

    // ──────────────────── 内部方法 ────────────────────

    private String buildUrl(String sn, long epochSeconds, String nameTemplate) {
        if (!StringUtils.hasText(sn) || !SAFE_SN.matcher(sn).matches() || epochSeconds <= 0) {
            return null;
        }
        String base = properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + dateDirOf(epochSeconds) + "/" + sn + "/"
                + String.format(nameTemplate, epochSeconds);
    }

    public Path resolveFilePath(String sn, long epochSeconds, boolean plateOnly) {
        String filename = plateOnly
                ? String.format(PLATE_IMAGE_NAME, epochSeconds)
                : String.format(FULL_IMAGE_NAME, epochSeconds);
        return resolveDir(sn, epochSeconds).resolve(filename);
    }

    private Path resolveDir(String sn, long epochSeconds) {
        return Paths.get(properties.getStorageDir(), dateDirOf(epochSeconds), sn)
                .toAbsolutePath().normalize();
    }

    private String dateDirOf(long epochSeconds) {
        return DATE_DIR_FORMAT.format(Instant.ofEpochSecond(epochSeconds).atZone(ZONE));
    }

    private void writeFile(Path target, MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validateSn(String sn) {
        if (!StringUtils.hasText(sn) || !SAFE_SN.matcher(sn).matches()) {
            throw new IllegalArgumentException("invalid sn: " + sn);
        }
    }

    /** 目录名为 yyyyMMdd 且早于截止日期则视为过期 */
    private boolean isExpiredDateDir(String dirName, LocalDate cutoff) {
        if (!dirName.matches("^\\d{8}$")) {
            return false;
        }
        LocalDate dirDate = LocalDate.parse(dirName, DateTimeFormatter.ofPattern("yyyyMMdd"));
        return dirDate.isBefore(cutoff);
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("删除过期图片失败: path={}, error={}", p, e.getMessage());
                        }
                    });
            log.info("过期图片目录已清理: {}", dir);
        } catch (IOException e) {
            log.warn("遍历过期图片目录失败: path={}, error={}", dir, e.getMessage());
        }
    }
}
