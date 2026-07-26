package com.jushan.platform.modules.booth.service;

import com.jushan.platform.modules.booth.dto.SpaceAdjustCmd;

/**
 * 岗亭余位调整服务接口。
 *
 * @author Jushan Platform
 * @since 1.5.3
 */
public interface BoothSpaceService {

    /**
     * 调整剩余车位数。
     * <p>
     * 支持直接设定（SET）和加减调整（ADJUST）两种模式。
     * 调整后通过 WebSocket 实时推送到所有在线的岗亭客户端。
     *
     * @param cmd 调整命令（mode / value / parkingLotId / reason）
     */
    void adjust(SpaceAdjustCmd cmd);
}
