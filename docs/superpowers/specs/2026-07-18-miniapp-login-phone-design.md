# 设计文档：小程序登录链路与手机号绑定（任务包 5-1）

> 需求依据：V1.1 3.3.1（MINI-001）、2.4.5、确认项 64
> 关联任务包：5-1（阶段 5 第一个包，后续 5-2/5-3 依赖本包）

---

## 1. 概述

### 1.1 背景

小程序端当前：
- `app.js onLaunch` 不调用 `wx.login`，无主动登录流程
- 后端 `/wx/login` 为纯 mock（`mock_openid_` + code 前缀），无真实 `jscode2session` 调用
- 手机号绑定仅有后端 `POST /wx/phone`（明文 phone+verifyCode），无 `getPhoneNumber` 流程
- 登录态实际缺失，所有页面靠 storage 残留 token 假装登录

### 1.2 目标

1. 实现真实微信登录链路：`wx.login` → `code2session` → 签发小程序专用 JWT
2. 实现微信手机号快速验证绑定：`getPhoneNumber` → 后端换手机号
3. 未绑定手机号拦截核心功能（缴费/月卡/车位/代缴），仅允许浏览首页与余位
4. 接口路径从 `/wx/*` 统一为 `/api/v1/mini/*`
5. 开发环境保留 mock 开关，生产强制真实

### 1.3 非目标

- 不涉及短信验证码绑定（本期不做短信推送）
- 不涉及微信 UnionID 跨应用打通（后续需求）
- 不涉及月卡/固定车位功能（任务包 5-2）
- 不涉及三标签记录/订阅消息/支付收口（任务包 5-3）

---

## 2. 架构设计

### 2.1 整体交互流程

```
┌─────────── 小程序前端 ────────────┐     ┌────────── 后端 ───────────┐     ┌──── 微信服务端 ────┐
│                                    │     │                           │     │                    │
│  app.js onLaunch                   │     │                           │     │                    │
│    │                               │     │                           │     │                    │
│    ├─ storage 有 token?            │     │                           │     │                    │
│    │  ├─ 有 → GET /mini/user       │────→│ JwtFilter 验证 token      │     │                    │
│    │  │   ├─ 200 → 进入首页        │     │   └─ 校验 aud=miniapp     │     │                    │
│    │  │   └─ 401 → 走登录          │     │                           │     │                    │
│    │  └─ 无 → 走登录               │     │                           │     │                    │
│    │                               │     │                           │     │                    │
│    ├─ wx.login() ──────────────────────────────────────────────────────→ jscode2session        │
│    │  └─ code                      │     │                           │     │  └─ openid         │
│    │                               │     │                           │     │     session_key    │
│    ├─ POST /mini/login {code} ────→│     │                           │     │                    │
│    │                               │     │ MiniAuthService.login()   │     │                    │
│    │                               │     │  ├─ resolveOpenid(code)   │     │                    │
│    │                               │     │  ├─ 查/建 wx_user         │     │                    │
│    │                               │     │  ├─ 签发 JWT(aud=miniapp) │     │                    │
│    │                               │     │  └─ 返回 loginResult      │     │                    │
│    │  ← {token, phoneBound, ...} ──│     │                           │     │                    │
│    │                               │     │                           │     │                    │
│    ├─ phoneBound?                  │     │                           │     │                    │
│    │  ├─ true → 首页               │     │                           │     │                    │
│    │  └─ false → 手机号绑定引导页   │     │                           │     │                    │
│    │                               │     │                           │     │                    │
│    └─ bind-phone page              │     │                           │     │                    │
│       ├─ <button getPhoneNumber>   │     │                           │     │                    │
│       │  └─ 回调 {code}            │     │                           │     │                    │
│       ├─ POST /mini/phone {code} ─→│     │                           │     │                    │
│       │                            │     │ bindPhoneByWechat(code)   │     │                    │
│       │                            │     │  ├─ getAccessToken() ──────────────────────────────→ │
│       │                            │     │  ├─ getPhoneNumber(code) ───────────────────────────→│
│       │                            │     │  │  ← purePhoneNumber      │     │                    │
│       │                            │     │  ├─ 校验手机号唯一性       │     │                    │
│       │                            │     │  ├─ 更新 wx_user.phone     │     │                    │
│       │                            │     │  └─ 返回成功               │     │                    │
│       │  ← {success: true} ────────│     │                           │     │                    │
│       └─ 跳转首页                  │     │                           │     │                    │
│                                    │     │                           │     │                    │
└────────────────────────────────────┘     └───────────────────────────┘     └────────────────────┘
```

### 2.2 组件关系

```
┌─────────────────────────────────────────────────────────────────┐
│ 小程序前端                                                        │
│  app.js (onLaunch 启动登录)                                       │
│  utils/request.js (Token 注入 + 401 自动刷新)                      │
│  pages/bind-phone/bind-phone (手机号绑定引导页)                     │
│  pages/index, pay, proxy-pay, plate... (绑定拦截检测)               │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTPS
┌─────────────────────────▼───────────────────────────────────────┐
│ 后端                                                             │
│                                                                  │
│  SecurityConfig                                                   │
│    ├─ 放开 /api/v1/mini/login, /api/v1/mini/phone (permitAll)    │
│    └─ 其他 /api/v1/mini/** 需 JWT(aud=miniapp)                   │
│                                                                  │
│  JwtAuthenticationFilter           JwtUtils                       │
│    └─ 解析 aud 字段校验             └─ generateToken 新增         │
│       aud=miniapp                      audience 参数              │
│                                                                  │
│  MiniAuthController (新建, /api/v1/mini)                         │
│    ├─ POST /login         → MiniAuthService.login()              │
│    └─ POST /phone         → MiniAuthService.bindPhone()          │
│                                                                  │
│  MiniAuthService (新建)                                          │
│    ├─ login(code, nickname, avatarUrl) → WxLoginResult           │
│    │   ├─ resolveOpenid(code) → openid                            │
│    │   ├─ wxUserMapper selectOne/create wx_user                   │
│    │   └─ JwtUtils.generateToken(..., aud=miniapp)               │
│    └─ bindPhone(code, iv?)                                        │
│        ├─ WeChatApiClient.getPhoneNumber(code)                   │
│        ├─ 手机号去重校验                                          │
│        └─ 更新 wx_user.phone, phone_verified                     │
│                                                                  │
│  WeChatApiClient (新建)                                          │
│    ├─ code2session(jsCode) → {openid, session_key, unionid}     │
│    ├─ getPhoneNumber(code) → {purePhoneNumber, countryCode}     │
│    └─ getAccessToken() → token (Redis 缓存, 7200s-300s刷新)     │
│                                                                  │
│  WxUserController (保留兼容)                                     │
│    ├─ /wx/login  → 标记 @Deprecated, 委托 MiniAuthService        │
│    └─ /wx/phone  → 标记 @Deprecated, 返回 410 Gone               │
│                                                                  │
│  WxLoginRequest (扩展)                                           │
│    └─ 新增 nickname, avatarUrl 可选字段                           │
│                                                                  │
│  wx_user 表 (不变，仅数据操作)                                    │
│    ├─ phone_verified 字段用于绑定状态判断                          │
│    └─ session_key 字段用于 getPhoneNumber 解密 (可选)              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 详细设计

### 3.1 微信 API 客户端（WeChatApiClient）

**包路径**: `com.jushan.system.client.WeChatApiClient`

**配置** (`application.yml`):
```yaml
wx:
  miniapp:
    appid: ${WX_MINIAPP_APPID:}
    secret: ${WX_MINIAPP_SECRET:}
  mock-login: false  # dev/test 可 override 为 true
```

**核心方法**:

| 方法 | 说明 |
|------|------|
| `code2session(String jsCode)` | 调 `https://api.weixin.qq.com/sns/jscode2session`，返回 openid/session_key/unionid |
| `getAccessToken()` | 调 `https://api.weixin.qq.com/cgi-bin/token`，Redis 缓存，TTL 6900s |
| `getPhoneNumber(String code)` | 调 `https://api.weixin.qq.com/wxa/business/getuserphonenumber` |

**错误码映射**:
| 微信码 | 业务含义 | 处理 |
|--------|---------|------|
| 0 | 成功 | - |
| 40029 | code 无效 | `BusinessException(INVALID_CODE, "登录凭证已失效，请重新授权")` |
| 45011 | 频率限制 | `BusinessException(RATE_LIMITED, "操作太频繁，请稍后再试")` |
| -1 | 系统繁忙 | `BusinessException(WECHAT_ERROR, "微信服务繁忙，请重试")` |
| 40163 | code 已被使用 | 同 40029 |
| 40013 | appid 无效 | `BusinessException(CONFIG_ERROR, "小程序 AppId 配置错误")` |

**access_token 缓存设计**:
```
Redis key: "wechat:access_token:{appid}"
TTL: 6900s (7200-300)
获取逻辑:
  1. 从 Redis 读取
  2. 命中 → 返回
  3. 未命中 → 调微信接口获取 → 存入 Redis → 返回
  4. 接口调用需加分布式锁（防止并发重复获取）
```

### 3.2 Mock 开关策略

```java
// WeChatApiClient 或 MiniAuthService 中
@Value("${wx.mock-login:false}")
private boolean mockLoginEnabled;

private String resolveOpenid(String code) {
    // dev/test profile 允许 mock，prod 强制真实
    if (mockLoginEnabled) {
        // 仅非 prod 环境可用
        log.warn("[Mock] 微信登录: code={}", code);
        return "mock_openid_" + code;
    }
    // 真实 code2session
    return weChatApiClient.code2session(code).getOpenid();
}
```

**prod 保护**:
```java
// 在 @Configuration @Profile("prod") 的配置类中
@PostConstruct
public void validate() {
    if (mockLoginEnabled) {
        throw new IllegalStateException(
            "wx.mock-login=true is not allowed in production profile");
    }
}
```

### 3.3 JWT audience 区分

**现有 JWT payload 结构**:
```json
{
  "sub": 1001,
  "tenantId": null,
  "loginType": "wx_user",
  "roles": ["wx_user"],
  "iat": 1752825600,
  "exp": 1752912000
}
```

**新增 aud 字段**:
```json
{
  "sub": 1001,
  "tenantId": null,
  "loginType": "wx_user",
  "aud": "miniapp",
  "roles": ["wx_user"],
  "iat": 1752825600,
  "exp": 1752912000
}
```

**JwtUtils.generateToken 改造**:
```java
// 新增 audience 参数
public static String generateToken(Long userId, Long tenantId,
    String loginType, String rolesJson,
    String audience, String secret, long expiration);
```

**JwtAuthenticationFilter 改造**:
```java
// 请求路径 /api/v1/mini/** → 校验 aud="miniapp"
// 请求路径 /api/v1/admin/** → 校验 aud 为空或 aud="web"
// 不匹配 → 401
```

**SecurityConfig 放开路径**:
```java
.requestMatchers("/api/v1/mini/login", "/api/v1/mini/phone").permitAll()
.requestMatchers("/wx/login", "/wx/phone").permitAll()  // 兼容期
```

### 3.4 登录 API

**POST /api/v1/mini/login**

请求:
```json
{
  "code": "081xxxxx",
  "nickname": "微信用户",
  "avatarUrl": "https://..."
}
```

响应:
```json
{
  "code": 0,
  "data": {
    "token": "eyJhbG...",
    "userId": 1001,
    "nickname": "微**户",
    "avatarUrl": "https://...",
    "isNewUser": false,
    "phoneBound": true,
    "plateCount": 2,
    "loginTime": "2026-07-18T10:00:00",
    "message": "欢迎回来"
  }
}
```

关键变更：`WxLoginResult` 新增 `phoneBound` 字段：
```java
// WxLoginResult.java 新增
private Boolean phoneBound;  // wx_user.phone_verified == true

// 赋值逻辑
result.setPhoneBound(Boolean.TRUE.equals(wxUser.getPhoneVerified()));
```

### 3.5 手机号绑定 API

**POST /api/v1/mini/phone**

请求:
```json
{
  "code": "xxxxx",
  "iv": "yyyyy",       // 可选，旧版基础库兼容
  "encryptedData": "zzzzz"  // 可选，旧版基础库兼容
}
```

响应:
```json
{
  "code": 0,
  "data": {
    "phone": "138****1234",
    "success": true
  }
}
```

**服务端逻辑**:
```java
@Transactional
public void bindPhoneByWechat(String code) {
    Long userId = getCurrentWxUserId();

    // 1. 调微信获取手机号
    WeChatPhoneInfo phoneInfo = weChatApiClient.getPhoneNumber(code);

    // 2. 校验手机号唯一性
    WxUser existing = wxUserMapper.selectOne(
        new LambdaQueryWrapper<WxUser>()
            .eq(WxUser::getPhone, phoneInfo.getPurePhoneNumber())
            .ne(WxUser::getId, userId));
    if (existing != null) {
        throw new BusinessException(CONFLICT, "该手机号已被其他账号绑定");
    }

    // 3. 更新
    WxUser wxUser = wxUserMapper.selectById(userId);
    wxUser.setPhone(phoneInfo.getPurePhoneNumber());
    wxUser.setPhoneVerified(true);
    wxUser.setUpdatedAt(LocalDateTime.now());
    wxUserMapper.updateById(wxUser);
}
```

### 3.6 接口路径兼容

| 旧路径 | 新路径 | 状态 |
|--------|--------|------|
| `POST /wx/login` | `POST /api/v1/mini/login` | 旧路径委托新服务，标记 `@Deprecated` |
| `POST /wx/phone` | `POST /api/v1/mini/phone` | 旧路径返回 `410 Gone`（手机号接口不兼容） |
| `POST /wx/plates` | 不变（5-3 统一迁移） | 暂时保持两套路径 |
| `GET /wx/user` | 不变 | 暂时保持 |

**URL 兼容处理**:
```java
@RestController
@RequestMapping("/wx")
@Deprecated
public class WxUserController {

    @PostMapping("/login")
    @Deprecated
    public R<WxLoginResult> login(@Valid @RequestBody WxLoginRequest request) {
        return R.ok(miniAuthService.login(request));
    }

    @PostMapping("/phone")
    @Deprecated
    public R<Void> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        // 旧接口明文手机号绑定已废弃，getPhoneNumber 流程请用 /api/v1/mini/phone
        return R.fail(ErrorCode.GONE.value(), "此接口已废弃，请在微信小程序中更新版本");
    }
}
```

---

## 4. 前端设计

### 4.1 app.js onLaunch 改造

```javascript
App({
  globalData: {
    apiBaseUrl: 'http://192.168.20.106:8080',
    token: null,
    ownerInfo: null,
    phoneBound: false,
    _refreshPromise: null,  // token 刷新锁
  },

  async onLaunch() {
    // 系统信息...
    const sysInfo = wx.getSystemInfoSync()
    // ...

    // 尝试恢复登录态
    await this.restoreSession()
  },

  async restoreSession() {
    const token = wx.getStorageSync(TOKEN_KEY)
    if (!token) {
      await this.doLogin()
      return
    }
    this.globalData.token = token

    // 验证 token 有效性
    try {
      const userInfo = await this.request({
        url: '/api/v1/mini/user',
        method: 'GET',
      })
      this.globalData.ownerInfo = userInfo
      this.globalData.phoneBound = userInfo.phoneVerified
    } catch (err) {
      if (err.status === 401) {
        this.logout()
        await this.doLogin()
      }
    }
  },

  async doLogin() {
    try {
      const { code } = await wxLogin()
      const result = await this.request({
        url: '/api/v1/mini/login',
        method: 'POST',
        data: { code },
      })
      this.globalData.token = result.token
      this.globalData.ownerInfo = result
      this.globalData.phoneBound = result.phoneBound
      wx.setStorageSync(TOKEN_KEY, result.token)

      // 未绑定手机号引导
      if (!result.phoneBound) {
        const pages = getCurrentPages()
        const currentPage = pages[pages.length - 1]
        if (currentPage && currentPage.route !== 'pages/bind-phone/bind-phone') {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      }
    } catch (err) {
      // 登录失败提示重试
      wx.showModal({
        title: '登录失败',
        content: err.message || '请检查网络连接后重试',
        showCancel: false,
        confirmText: '重试',
        success: () => this.doLogin(),
      })
    }
  },
})
```

### 4.2 request.js Token 无感刷新

```javascript
// utils/request.js 中
// 401 响应时自动刷新 token
async function refreshToken() {
  const app = getApp()
  // 防止并发刷新
  if (app.globalData._refreshPromise) {
    return app.globalData._refreshPromise
  }
  app.globalData._refreshPromise = (async () => {
    try {
      const { code } = await new Promise((resolve, reject) => {
        wx.login({ success: resolve, fail: reject })
      })
      const result = await rawRequest({
        url: '/api/v1/mini/login',
        method: 'POST',
        data: { code },
        skipAuth: true,
      })
      app.globalData.token = result.token
      app.globalData.phoneBound = result.phoneBound
      wx.setStorageSync(TOKEN_KEY, result.token)
      return result.token
    } finally {
      app.globalData._refreshPromise = null
    }
  })()
  return app.globalData._refreshPromise
}
```

### 4.3 手机号绑定引导页

**pages/bind-phone/bind-phone**

```
路由: /pages/bind-phone/bind-phone
需要 .json, .js, .wxml, .wxss 四个文件

页面状态:
- 初始: 展示绑定引导
- 加载中: 调用后端中
- 绑定成功: 自动跳转首页
- 绑定失败: 展示错误，允许重试
- 稍后绑定: 跳转首页，后续核心功能被拦截

.wxml 结构:
  <view class="bind-phone-page">
    <view class="bind-header">
      <text class="bind-title">为保障服务，请绑定手机号</text>
      <text class="bind-desc">绑定后可享受停车缴费、月卡申请等服务</text>
    </view>
    <button class="btn-get-phone" open-type="getPhoneNumber"
            bindgetphonenumber="onGetPhoneNumber">
      微信手机号一键绑定
    </button>
    <view class="skip-bind" bindtap="onSkip">
      稍后绑定
    </view>
  </view>
```

**核心逻辑**:
```javascript
Page({
  async onGetPhoneNumber(e) {
    if (!e.detail.code && !e.detail.errMsg) return

    // 用户拒绝授权
    if (e.detail.errMsg && e.detail.errMsg.includes('deny')) {
      wx.showToast({ title: '需要授权手机号才能继续', icon: 'none' })
      return
    }

    const { code, iv, encryptedData } = e.detail
    try {
      await post('/api/v1/mini/phone', { code, iv, encryptedData })
      getApp().globalData.phoneBound = true
      wx.showToast({ title: '绑定成功', icon: 'success' })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 1000)
    } catch (err) {
      wx.showModal({
        title: '绑定失败',
        content: err.message || '请重试',
        confirmText: '重试',
        cancelText: '稍后绑定',
        success: (res) => {
          if (!res.confirm) {
            wx.switchTab({ url: '/pages/index/index' })
          }
        },
      })
    }
  },

  onSkip() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
```

### 4.4 核心功能拦截

在需要绑定的页面添加拦截检查（缴费/月卡/车位/代缴/车辆管理）:

```javascript
// 通用拦截 mixin 或每个页面的 onShow 中
checkPhoneBinding() {
  const app = getApp()
  if (!app.globalData.token) {
    wx.showModal({
      title: '请先登录',
      content: '需要登录后才能使用此功能',
      confirmText: '去登录',
      success: (res) => {
        if (res.confirm) {
          // 触发登录流程
          app.doLogin()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      },
    })
    return false
  }
  if (!app.globalData.phoneBound) {
    wx.showModal({
      title: '请先绑定手机号',
      content: '绑定手机号后即可使用停车缴费、月卡申请等服务',
      confirmText: '去绑定',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      },
    })
    return false
  }
  return true
}
```

**拦截范围**:

| 页面 | 是否拦截 | 说明 |
|------|---------|------|
| 首页 index | 否 | 可浏览 |
| 余位查询 lot-space | 否 | 可浏览 |
| 支付 pay | 是 | 需绑定 |
| 代缴 proxy-pay | 是 | 需绑定 |
| 月卡 monthly-card | 是 | 需绑定（5-2） |
| 固定车位 fixed-space | 是 | 需绑定（5-2） |
| 车辆管理 plate | 是 | 需绑定 |
| 消息 messages | 是 | 需绑定 |
| 个人中心 profile | 否 | 可浏览但功能入口拦截 |

### 4.5 app.json 更新

```json
{
  "pages": [
    "pages/index/index",
    "pages/profile/profile",
    "pages/plate/plate",
    "pages/records/records",
    "pages/parking/parking",
    "pages/pay/pay",
    "pages/proxy-pay/proxy-pay",
    "pages/lot-space/lot-space",
    "pages/messages/messages",
    "pages/bind-phone/bind-phone"
  ],
  "permission": {
    "scope.userLocation": {
      "desc": "用于查询附近停车场余位信息"
    }
  }
  // ... 其他不变
}
```

### 4.6 补充缺失的 .json 配置文件

当前缺失的文件需补充（仅结构声明）:

**pages/lot-space/lot-space.json**:
```json
{ "usingComponents": {}, "navigationBarTitleText": "余位查询" }
```

**pages/messages/messages.json**:
```json
{ "usingComponents": {}, "navigationBarTitleText": "消息中心" }
```

**pages/proxy-pay/proxy-pay.json**:
```json
{ "usingComponents": {}, "navigationBarTitleText": "代缴停车费" }
```

---

## 5. 数据库变更

### 5.1 wx_user 表（无需 DDL 变更）

现有 `wx_user` 表结构已满足需求：
- `phone` — 存储手机号
- `phone_verified` — 绑定状态
- `session_key` — 如有需要可存储用于旧版 getPhoneNumber 解密

无需新增 Flyway 迁移。

### 5.2 新增 flyway 迁移（可选：session_key 字段）

如果 `wx_user` 表没有 `session_key` 字段：
```sql
ALTER TABLE wx_user
    ADD COLUMN session_key VARCHAR(100) COMMENT '微信会话密钥，用于手机号解密' AFTER phone;
```

---

## 6. 安全考虑

| 风险 | 缓解措施 |
|------|---------|
| `secret` 泄漏 | 仅通过环境变量注入，不入库、不入仓、不入配置版本管理 |
| `code` 重放攻击 | 微信 `code` 仅一次有效，5 分钟内有效，使用后即失效 |
| token 混用 | JWT `aud` 字段区分终端，安全过滤器校验 |
| 手机号撞库 | 绑定前校验唯一性，返回 409 Conflict |
| session_key 泄漏 | 不入日志，`toString()` 遮罩 |

---

## 7. 测试要点

| 测试场景 | 验证点 |
|---------|--------|
| Mock 登录 | `wx.mock-login=true` + `dev` profile → openid="mock_openid_xxx" |
| 真实登录 | `wx.mock-login=false` + 真实 appid/secret → code2session 成功 |
| Prod 保护 | `prod` profile + `wx.mock-login=true` → 启动失败 |
| 手机号绑定 | `getPhoneNumber` → 后端换手机号 → `phone_verified=true` |
| 手机号去重 | 两次绑定同一手机号 → 第二次返回 409 |
| 未绑定拦截 | 无手机号用户访问 `/pay` → 弹窗引导绑定 |
| Token 刷新 | 401 响应 → 自动 `wx.login` → 重试原请求 |
| JWT audience | 小程序 token 访问管理接口 `/api/v1/admin/` → 401 |
| 旧路径兼容 | `POST /wx/login` → 正常返回（委托新服务） |
| 旧 phone 废弃 | `POST /wx/phone` → 410 Gone |

---

## 8. 验收标准

1. **真机走通**：`wx.login` → `token` → 绑定手机号 → 进入首页
2. **未绑定拦截**：未绑定手机号时缴费被拦截并引导绑定
3. **Mock 开关**：关闭 mock 后真实 `code2session` 可用（测试号验证）
4. **404 消除**：`lot-space`/`messages`/`proxy-pay` 三页 .json 配置文件齐全
5. **接口路径**：新登录/手机号接口使用 `/api/v1/mini/*`，旧 `/wx/*` 保持兼容

---

## 9. 文件影响清单

### 后端改动

| 文件 | 操作 | 说明 |
|------|------|------|
| `parking-system/.../client/WeChatApiClient.java` | **新增** | 微信服务端 API 客户端 |
| `parking-system/.../config/WxMiniappProperties.java` | **新增** | 微信小程序配置映射 |
| `parking-system/.../controller/MiniAuthController.java` | **新增** | 小程序认证控制器 (/api/v1/mini) |
| `parking-system/.../service/MiniAuthService.java` | **新增** | 小程序认证服务 |
| `parking-system/.../dto/MiniLoginRequest.java` | **新增** | 小程序登录请求 DTO |
| `parking-system/.../dto/MiniPhoneRequest.java` | **新增** | 手机号绑定请求 DTO |
| `parking-system/.../vo/WxLoginResult.java` | **改造** | 新增 phoneBound 字段 |
| `parking-system/.../controller/WxUserController.java` | **改造** | 标记 Deprecated，/wx/login 委托新服务，/wx/phone 返回 410 |
| `parking-system/.../service/WxUserService.java` | **改造** | resolveOpenid 支持 mock/真实切换 |
| `parking-system/.../dto/WxLoginRequest.java` | **改造** | 新增可选 nickname/avatarUrl 字段 |
| `parking-infrastructure/.../security/JwtUtils.java` | **改造** | generateToken 新增 audience 参数 |
| `parking-infrastructure/.../security/JwtAuthenticationFilter.java` | **改造** | 新增 aud 校验逻辑 |
| `parking-infrastructure/.../security/SecurityConfig.java` | **改造** | 放开新路径，/api/v1/mini/** 白名单 |
| `parking-boot/.../application.yml` | **改造** | 新增 wx.miniapp 配置段 |

### 前端改动

| 文件 | 操作 | 说明 |
|------|------|------|
| `miniapp/app.js` | **改造** | onLaunch 增加 wx.login 链路 + 登录状态验证 + doLogin() |
| `miniapp/app.json` | **改造** | 新增 bind-phone 页面、permission 声明 |
| `miniapp/utils/request.js` | **改造** | 增加 401 自动刷新 token 逻辑 |
| `miniapp/pages/bind-phone/*` | **新增** | 手机号绑定引导页（4 文件） |
| `miniapp/pages/lot-space/lot-space.json` | **新增** | 补充页面配置 |
| `miniapp/pages/messages/messages.json` | **新增** | 补充页面配置 |
| `miniapp/pages/proxy-pay/proxy-pay.json` | **新增** | 补充页面配置 |
| `miniapp/pages/pay/pay.js` | **改造** | onLoad 增加 phoneBound 拦截 |
| `miniapp/pages/proxy-pay/proxy-pay.js` | **改造** | onLoad 增加 phoneBound 拦截 |
| `miniapp/pages/plate/plate.js` | **改造** | onLoad 增加 phoneBound 拦截 |
| `miniapp/pages/profile/profile.js` | **改造** | "我的月卡"入口保留但逻辑在 5-2 实现 |
