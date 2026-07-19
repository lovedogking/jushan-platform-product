package com.jushan.platform.modules.parking.service;

import com.jushan.platform.modules.parking.dto.AnalyticsQueryCmd;
import com.jushan.platform.modules.parking.vo.AnalyticsOverviewVO;

public interface AnalyticsService {
    AnalyticsOverviewVO getOverview(AnalyticsQueryCmd cmd);
}
