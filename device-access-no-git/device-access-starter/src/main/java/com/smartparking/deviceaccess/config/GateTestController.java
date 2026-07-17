package com.smartparking.deviceaccess.config;

import com.smartparking.deviceaccess.adapter.zhenshi.ZhenshiMessageHandler;
import com.smartparking.deviceaccess.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 临时测试端点：批量测试 gpio_out 道闸控制。
 * 测试完成后删除此文件。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/_test/gate")
@RequiredArgsConstructor
public class GateTestController {

    private final ZhenshiMessageHandler handler;

    @PostMapping("/{deviceId}/gpio-sweep")
    public Result<List<Map<String, Object>>> sweepGpio(@PathVariable String deviceId) {
        String sn = deviceId; // 臻识 deviceId == sn
        long timeout = 8;
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());

        // 测试所有 io(0,1,2) × value(0,1) × delay(0, 500, 1000) 组合
        int[] ios = {0, 1, 2};
        int[] values = {0, 1};
        int[] delays = {0, 500, 1000};

        log.info("[GateSweep] Starting GPIO sweep for device {}", sn);

        for (int io : ios) {
            for (int value : values) {
                for (int delay : delays) {
                    try {
                        long start = System.currentTimeMillis();
                        handler.sendGpioTest(sn, io, value, delay, timeout)
                                .orTimeout(timeout + 2, TimeUnit.SECONDS)
                                .thenApply(reply -> {
                                    long elapsed = System.currentTimeMillis() - start;
                                    Map<String, Object> r = new LinkedHashMap<>();
                                    r.put("io", io);
                                    r.put("value", value);
                                    r.put("delay", delay);
                                    r.put("success", true);
                                    r.put("code", reply.getCode());
                                    r.put("elapsedMs", elapsed);
                                    results.add(r);
                                    log.info("[GateSweep] OK   io={} value={} delay={}  code={}  elapsed={}ms",
                                            io, value, delay, reply.getCode(), elapsed);
                                    return r;
                                })
                                .exceptionally(e -> {
                                    long elapsed = System.currentTimeMillis() - start;
                                    Map<String, Object> r = new LinkedHashMap<>();
                                    r.put("io", io);
                                    r.put("value", value);
                                    r.put("delay", delay);
                                    r.put("success", false);
                                    r.put("error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                                    r.put("elapsedMs", elapsed);
                                    results.add(r);
                                    log.info("[GateSweep] FAIL io={} value={} delay={}  err={}  elapsed={}ms",
                                            io, value, delay,
                                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
                                            elapsed);
                                    return r;
                                })
                                .get(timeout + 5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("io", io);
                        r.put("value", value);
                        r.put("delay", delay);
                        r.put("success", false);
                        r.put("error", e.getMessage());
                        results.add(r);
                    }

                    // 每次测试间隔 1s，避免设备被打死
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }

        log.info("[GateSweep] Complete. {} results", results.size());
        return Result.ok(results);
    }

    @PostMapping("/{deviceId}/gpio")
    public Result<Map<String, Object>> singleGpio(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "0") int io,
            @RequestParam(defaultValue = "1") int value,
            @RequestParam(defaultValue = "500") int delay) {
        try {
            handler.sendGpioTest(deviceId, io, value, delay, 8)
                    .orTimeout(10, TimeUnit.SECONDS)
                    .thenApply(reply -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("io", io);
                        r.put("value", value);
                        r.put("delay", delay);
                        r.put("code", reply.getCode());
                        r.put("success", true);
                        return r;
                    })
                    .exceptionally(e -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("io", io);
                        r.put("value", value);
                        r.put("delay", delay);
                        r.put("error", e.getMessage());
                        r.put("success", false);
                        return r;
                    })
                    .get(12, TimeUnit.SECONDS);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("io", io);
            result.put("value", value);
            result.put("delay", delay);
            return Result.ok(result);
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }
}
