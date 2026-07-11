/**
 * 智慧停车 - 车主端微信小程序
 * 全局入口
 * R01 冻结契约：jushan_access_token、code===0、message 字段
 */

const TOKEN_KEY = 'jushan_access_token'
const OWNER_INFO_KEY = 'ownerInfo'

App({
  globalData: {
    // 后端 API 基础地址（开发环境）
    apiBaseUrl: 'http://localhost:8080',
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

    // 尝试从本地存储恢复登录态
    const token = wx.getStorageSync(TOKEN_KEY)
    const ownerInfo = wx.getStorageSync(OWNER_INFO_KEY)
    if (token) {
      this.globalData.token = token
      this.globalData.ownerInfo = ownerInfo
    }
  },

  /**
   * 检查登录态，未登录则自动登录
   */
  checkLogin() {
    return new Promise((resolve, reject) => {
      if (this.globalData.token) {
        resolve(this.globalData.token)
        return
      }
      wx.login({
        success: (loginRes) => {
          if (!loginRes.code) {
            reject(new Error('wx.login 失败'))
            return
          }
          wx.request({
            url: `${this.globalData.apiBaseUrl}/api/auth/wechat-login`,
            method: 'POST',
            data: { code: loginRes.code },
            success: (res) => {
              // 业务成功码为 0
              if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 0) {
                const { accessToken, user } = res.data.data || {}
                this.globalData.token = accessToken
                this.globalData.ownerInfo = user
                wx.setStorageSync(TOKEN_KEY, accessToken)
                wx.setStorageSync(OWNER_INFO_KEY, user)
                resolve(accessToken)
              } else {
                reject(new Error(res.data?.message || '登录失败'))
              }
            },
            fail: reject,
          })
        },
        fail: reject,
      })
    })
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
