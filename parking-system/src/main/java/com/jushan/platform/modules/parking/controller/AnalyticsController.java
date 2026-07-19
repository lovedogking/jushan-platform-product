package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.AnalyticsQueryCmd;
import com.jushan.platform.modules.parking.service.AnalyticsService;
import com.jushan.platform.modules.parking.vo.AnalyticsOverviewVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @RequirePermission("parking:read")
    public R<AnalyticsOverviewVO> getOverview(
            @RequestParam(required = false) Long lotId,
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        AnalyticsQueryCmd cmd = new AnalyticsQueryCmd();
        cmd.setLotId(lotId);
        cmd.setPeriod(period);
        if (startDate != null) cmd.setStartDate(java.time.LocalDate.parse(startDate));
        if (endDate != null) cmd.setEndDate(java.time.LocalDate.parse(endDate));
        return R.ok(analyticsService.getOverview(cmd));
    }
}
