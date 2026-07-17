/**
 * 智慧停车 - 车主端微信小程序
 * 全局入口
 * R01 冻结契约：jushan_access_token、code===200、message 字段、displayName
 */
const TOKEN_KEY = 'jushan_access_token'
const OWNER_INFO_KEY = 'ownerInfo'

App({
  globalData: {
    // 后端 API 基础地址（开发环境）
    apiBaseUrl: 'http://192.168.20.106:8080',
    // 用户登录凭证
    token: null,
    ownerInfo: null,
    // 设备信息
    statusBarHeight: 0,
    navBarHeight: 44,
  },

  onLaunch() {
    // 获取系统信息
    const sysInfo = wx.getSystemInfoSync()
    this.globalData.statusBarHeight = sysInfo.statusBarHeight
    const menuButton = wx.getMenuButtonBoundingClientRect()
    this.globalData.navBarHeight = (menuButton.top - sysInfo.statusBarHeight) * 2 + menuButton.height

    // 仅从本地存储恢复登录态，不主动发起登录请求
    const token = wx.getStorageSync(TOKEN_KEY)
    if (token && typeof token === 'string' && token.trim().length > 0) {
      this.globalData.token = token
      // 迁移旧字段：读取 ownerInfo 并统一为 displayName
      const ownerInfo = wx.getStorageSync(OWNER_INFO_KEY)
      if (ownerInfo) {
        // 兼容旧字段 nickname → displayName
        if (!ownerInfo.displayName && ownerInfo.nickname) {
          ownerInfo.displayName = ownerInfo.nickname
        }
        this.globalData.ownerInfo = ownerInfo
      }
    }
    // 没有 Token 时保持未登录状态，不主动发起登录请求
  },

  /**
   * 清除登录态
   */
  logout() {
    this.globalData.token = null
    this.globalData.ownerInfo = null
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(OWNER_INFO_KEY)
  },
})
