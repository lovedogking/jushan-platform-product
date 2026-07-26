package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.booth.dto.SpaceAdjustCmd;
import com.jushan.platform.modules.booth.service.BoothSpaceService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗亭余位调整控制器。
 * <p>
 * 提供岗亭端手动修正剩余车位数的能力，支持直接设定和加减调整两种模式。
 *
 * @author Jushan Platform
 * @since 1.5.3
 */
@Slf4j
@RestController
@RequestMapping("/api/booth/spaces")
public class BoothSpaceController {

    private final BoothSpaceService boothSpaceService;

    public BoothSpaceController(BoothSpaceService boothSpaceService) {
        this.boothSpaceService = boothSpaceService;
    }

    /**
     * 调整剩余车位数。
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li>SET — 直接设定目标值（如改为 50 表示剩余 50 个车位）</li>
     *   <li>ADJUST — 加减调整（+5 表示增加 5 个，-3 表示减少 3 个）</li>
     * </ul>
     * 操作后自动推送到所有在线的岗亭客户端。
     *
     * @param cmd 调整命令
     * @return 操作结果
     */
    @PostMapping("/adjust")
    @RequirePermission("booth:operate")
    public R<Void> adjust(@Valid @RequestBody SpaceAdjustCmd cmd) {
        boothSpaceService.adjust(cmd);
        log.info("岗亭余位调整成功: parkingLotId={}, mode={}, value={}",
                cmd.getParkingLotId(), cmd.getMode(), cmd.getValue());
        return R.ok();
    }
}
