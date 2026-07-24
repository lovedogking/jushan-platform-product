package com.jushan.platform.modules.device.client;

import com.jushan.platform.modules.device.client.dto.CaptureResultDTO;
import com.jushan.platform.modules.device.client.dto.CommandResultDTO;
import com.jushan.platform.modules.device.client.dto.DeviceAccessResponse;
import com.jushan.platform.modules.device.client.dto.DeviceStatusDTO;
import com.jushan.platform.modules.device.client.dto.DisplayConfigRequest;
import com.jushan.platform.modules.device.client.dto.DisplayResultDTO;
import com.jushan.platform.modules.device.client.dto.DisplaySaveRequest;
import com.jushan.platform.modules.device.client.dto.DisplayTextRequest;
import com.jushan.platform.modules.device.client.dto.TimeSyncResultDTO;
import com.jushan.platform.modules.device.client.dto.VoiceControlRequest;
import com.jushan.platform.modules.device.client.dto.VoiceResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Device Access Mock 客户端实现（任务包 7-1）。
 * <p>
 * 当 {@code jushan.device-access.mock.enabled=true} 时替代 {@link DeviceAccessClientImpl}。
 * 所有方法返回默认成功响应，不执行任何网络调用。
 * <p>
 * <strong>安全约束</strong>：生产环境必须设置 {@code mock.enabled=false}，
 * 本类通过 {@code @ConditionalOnProperty} 保证不会意外激活。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
@ConditionalOnProperty(name = "jushan.device-access.mock.enabled", havingValue = "true")
public class MockDeviceAccessClient implements DeviceAccessClient {

    private static final Logger log = LoggerFactory.getLogger(MockDeviceAccessClient.class);

    private static final String MOCK_PREFIX = "[MOCK] ";

    @Override
    public DeviceStatusDTO getStatus(String deviceSn) {
        log.info("[MOCK] 查询设备状态: deviceSn={}", deviceSn);
        DeviceStatusDTO dto = new DeviceStatusDTO();
        dto.setDeviceSn(deviceSn);
        dto.setOnline(true);
        dto.setLastOnlineTime(java.time.LocalDateTime.now().toString());
        dto.setStatus("connected");
        return dto;
    }

    @Override
    public TimeSyncResultDTO syncTime(String deviceSn) {
        log.info("[MOCK] 校时: deviceSn={}", deviceSn);
        TimeSyncResultDTO dto = new TimeSyncResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "校时成功");
        return dto;
    }

    @Override
    public CommandResultDTO openGate(String deviceSn) {
        log.info("[MOCK] 开闸: deviceSn={}", deviceSn);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "开闸成功");
        return dto;
    }

    @Override
    public CommandResultDTO closeGate(String deviceSn) {
        log.info("[MOCK] 关闸: deviceSn={}", deviceSn);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "关闸成功");
        return dto;
    }

    @Override
    public CommandResultDTO openGate(String deviceSn, String commandId) {
        log.info("[MOCK] 开闸（幂等）: deviceSn={}, commandId={}", deviceSn, commandId);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "开闸成功 (commandId=" + commandId + ")");
        return dto;
    }

    @Override
    public CommandResultDTO closeGate(String deviceSn, String commandId) {
        log.info("[MOCK] 关闸（幂等）: deviceSn={}, commandId={}", deviceSn, commandId);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "关闸成功 (commandId=" + commandId + ")");
        return dto;
    }

    @Override
    public CommandResultDTO lockGate(String deviceSn) {
        log.info("[MOCK] 常开（锁定道闸）: deviceSn={}", deviceSn);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "常开成功（道闸已锁定）");
        return dto;
    }

    @Override
    public CommandResultDTO unlockGate(String deviceSn) {
        log.info("[MOCK] 取消常开（解除道闸锁定）: deviceSn={}", deviceSn);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "取消常开成功（道闸已解锁并关闸）");
        return dto;
    }

    @Override
    public CommandResultDTO lockGate(String deviceSn, String commandId) {
        log.info("[MOCK] 常开（锁定道闸，幂等）: deviceSn={}, commandId={}", deviceSn, commandId);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "常开成功 (commandId=" + commandId + ")");
        return dto;
    }

    @Override
    public CommandResultDTO unlockGate(String deviceSn, String commandId) {
        log.info("[MOCK] 取消常开（解除道闸锁定，幂等）: deviceSn={}, commandId={}", deviceSn, commandId);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "取消常开成功 (commandId=" + commandId + ")");
        return dto;
    }

    @Override
    public CommandResultDTO lockCloseGate(String deviceSn) {
        log.info("[MOCK] 常关（锁定道闸关闭）: deviceSn={}", deviceSn);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "常关成功（道闸已锁定关闭）");
        return dto;
    }

    @Override
    public CommandResultDTO lockCloseGate(String deviceSn, String commandId) {
        log.info("[MOCK] 常关（锁定道闸关闭，幂等）: deviceSn={}, commandId={}", deviceSn, commandId);
        CommandResultDTO dto = new CommandResultDTO();
        dto.setSuccess(true);
        dto.setDeviceCode(200);
        dto.setMessage(MOCK_PREFIX + "常关成功 (commandId=" + commandId + ")");
        return dto;
    }

    @Override
    public DisplayResultDTO displayText(String deviceSn, DisplayTextRequest request) {
        log.info("[MOCK] 显示屏文字: deviceSn={}", deviceSn);
        DisplayResultDTO dto = new DisplayResultDTO();
        dto.setSuccess(true);
        dto.setMessage(MOCK_PREFIX + "显示屏文字设置成功");
        return dto;
    }

    @Override
    public DisplayResultDTO saveDisplay(String deviceSn, DisplaySaveRequest request) {
        log.info("[MOCK] 保存显示: deviceSn={}", deviceSn);
        DisplayResultDTO dto = new DisplayResultDTO();
        dto.setSuccess(true);
        dto.setMessage(MOCK_PREFIX + "显示内容保存成功");
        return dto;
    }

    @Override
    public DisplayResultDTO displayConfig(String deviceSn, DisplayConfigRequest request) {
        log.info("[MOCK] 显示屏配置: deviceSn={}", deviceSn);
        DisplayResultDTO dto = new DisplayResultDTO();
        dto.setSuccess(true);
        dto.setMessage(MOCK_PREFIX + "显示屏配置成功");
        return dto;
    }

    @Override
    public VoiceResultDTO voiceControl(String deviceSn, VoiceControlRequest request) {
        log.info("[MOCK] 语音播报: deviceSn={}", deviceSn);
        VoiceResultDTO dto = new VoiceResultDTO();
        dto.setSuccess(true);
        dto.setMessage(MOCK_PREFIX + "语音播报成功");
        return dto;
    }

    @Override
    public CaptureResultDTO captureImage(String deviceSn) {
        log.info("[MOCK] 主动抓拍: deviceSn={}", deviceSn);
        CaptureResultDTO dto = new CaptureResultDTO();
        dto.setSuccess(true);
        dto.setMessage(MOCK_PREFIX + "抓拍成功");
        return dto;
    }
}
