/**
 * 智慧停车 - 车主端微信小程序
 * 全局入口
 */
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
    const token = wx.getStorageSync('token')
    const ownerInfo = wx.getStorageSync('ownerInfo')
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
              if (res.data && res.data.code === 200) {
                const { token, ownerId, ownerName, nickname, avatar, phone } = res.data.data
                this.globalData.token = token
                this.globalData.ownerInfo = { ownerId, ownerName, nickname, avatar, phone }
                wx.setStorageSync('token', token)
                wx.setStorageSync('ownerInfo', this.globalData.ownerInfo)
                resolve(token)
              } else {
                reject(new Error(res.data?.msg || '登录失败'))
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
    wx.removeStorageSync('token')
    wx.removeStorageSync('ownerInfo')
  },
})
