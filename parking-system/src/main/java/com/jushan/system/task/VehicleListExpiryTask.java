package com.jushan.system.task;

import com.jushan.system.cache.VehicleListCacheStore;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.mapper.VehicleListMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 名单到期定时任务。每天凌晨 2:00 扫描到期名单并置为 EXPIRED。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Slf4j
@Component
public class VehicleListExpiryTask {

    private static final int BATCH_SIZE = 500;

    private final VehicleListMapper vehicleListMapper;
    private final VehicleListCacheStore cacheStore;

    public VehicleListExpiryTask(VehicleListMapper vehicleListMapper, VehicleListCacheStore cacheStore) {
        this.vehicleListMapper = vehicleListMapper;
        this.cacheStore = cacheStore;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void expireVehicleList() {
        log.info("开始执行名单到期扫描");
        int totalExpired = 0;
        List<VehicleList> batch;
        do {
            batch = vehicleListMapper.selectExpired(BATCH_SIZE);
            for (VehicleList entity : batch) {
                entity.setStatus(VehicleList.STATUS_EXPIRED);
                entity.setUpdatedAt(LocalDateTime.now());
                vehicleListMapper.updateById(entity);
                cacheStore.evict(entity.getParkingLotId(), entity.getPlateNumber());
                totalExpired++;
            }
        } while (!batch.isEmpty());

        if (totalExpired > 0) {
            log.info("名单到期扫描完成: 到期 {} 条", totalExpired);
        }
    }
}
