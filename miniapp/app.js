/**
 * 智慧停车 - 车主端微信小程序
 * 全局入口
 * R01 冻结契约：jushan_access_token、code===200、message 字段、displayName
 */
const TOKEN_KEY = 'jushan_access_token'
const OWNER_INFO_KEY = 'ownerInfo'

function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({ success: resolve, fail: reject })
  })
}

App({
  globalData: {
    apiBaseUrl: 'http://192.168.20.106:8080',
    token: null,
    ownerInfo: null,
    phoneBound: false,
    _refreshPromise: null,
    statusBarHeight: 0,
    navBarHeight: 44,
  },

  onLaunch() {
    const sysInfo = wx.getSystemInfoSync()
    this.globalData.statusBarHeight = sysInfo.statusBarHeight
    const menuButton = wx.getMenuButtonBoundingClientRect()
    this.globalData.navBarHeight = (menuButton.top - sysInfo.statusBarHeight) * 2 + menuButton.height
    this.restoreSession()
  },

  async restoreSession() {
    const token = wx.getStorageSync(TOKEN_KEY)
    if (!token) { await this.doLogin(); return }
    this.globalData.token = token
    try {
      const userInfo = await this.request({ url: '/wx/user', method: 'GET' })
      this.globalData.ownerInfo = userInfo
      this.globalData.phoneBound = userInfo && userInfo.phoneVerified === true
    } catch (err) {
      if (err && err.status === 401) { this.logout(); await this.doLogin() }
    }
  },

  async doLogin() {
    try {
      const { code } = await wxLogin()
      const result = await this.request({ url: '/api/v1/mini/login', method: 'POST', data: { code }, skipAuth: true })
      this.globalData.token = result.token
      this.globalData.ownerInfo = result
      this.globalData.phoneBound = result.phoneBound === true
      wx.setStorageSync(TOKEN_KEY, result.token)
      if (!result.phoneBound) {
        const pages = getCurrentPages()
        const currentPage = pages[pages.length - 1]
        if (currentPage && currentPage.route !== 'pages/bind-phone/bind-phone') {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      }
    } catch (err) {
      wx.showModal({
        title: '登录失败',
        content: (err && err.message) || '请检查网络连接后重试',
        showCancel: false, confirmText: '重试',
        success: () => { this.doLogin() }
      })
    }
  },

  request(options) {
    return new Promise((resolve, reject) => {
      const header = { 'Content-Type': 'application/json' }
      if (!options.skipAuth && this.globalData.token) header['Authorization'] = 'Bearer ' + this.globalData.token
      wx.request({
        url: this.globalData.apiBaseUrl + options.url,
        method: options.method || 'GET',
        data: options.data || {},
        header,
        success(res) {
          const body = typeof res.data === 'object' ? res.data : null
          if (res.statusCode >= 200 && res.statusCode < 300) {
            if (body && (body.code === 0 || body.code === 200)) resolve(body.data)
            else if (body && body.code !== undefined) reject({ message: body.message, code: body.code, status: res.statusCode })
            else resolve(null)
          } else if (res.statusCode === 401) reject({ message: (body && body.message) || '未登录', status: 401 })
          else reject({ message: (body && body.message) || '请求失败', status: res.statusCode })
        },
        fail(err) { reject({ message: err.errMsg || '网络连接失败', status: 0 }) }
      })
    })
  },

  logout() {
    this.globalData.token = null
    this.globalData.ownerInfo = null
    this.globalData.phoneBound = false
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(OWNER_INFO_KEY)
  },
})
