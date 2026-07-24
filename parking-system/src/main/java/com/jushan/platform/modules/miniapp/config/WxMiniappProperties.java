package com.jushan.platform.modules.miniapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置属性。
 * <p>
 * appid/secret 通过环境变量注入，不入库、不入仓、不入版本管理。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMiniappProperties {

    /** 小程序 AppId */
    private String appid;

    /** 小程序 Secret（必须通过环境变量注入） */
    private String secret;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时（毫秒） */
    private int readTimeout = 10000;

    /** 订阅消息模板 ID */
    private String subscribeTemplateId;

    // ==================== getter / setter ====================

    public String getAppid() { return appid; }
    public void setAppid(String appid) { this.appid = appid; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }

    public String getSubscribeTemplateId() { return subscribeTemplateId; }
    public void setSubscribeTemplateId(String subscribeTemplateId) { this.subscribeTemplateId = subscribeTemplateId; }
}
