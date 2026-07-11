/**
 * 我的 — 个人中心
 * T10 占位页面
 */
Page({
  data: {
    isLoggedIn: false,
    displayName: '',
    phone: '',
  },

  onLoad() {
    const app = getApp()
    if (app.globalData.token && app.globalData.ownerInfo) {
      this.setData({
        isLoggedIn: true,
        displayName: app.globalData.ownerInfo.displayName || '车主',
        phone: app.globalData.ownerInfo.phone || '',
      })
    }
  },

  onShow() {
    this.onLoad()
  },

  /** 退出登录 */
  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          const app = getApp()
          app.logout()
          this.setData({ isLoggedIn: false })
          wx.showToast({ title: '已退出', icon: 'success' })
        }
      },
    })
  },

  /** 跳转我的车辆 */
  goToVehicles() {
    wx.showToast({ title: '我的车辆 — 后续版本开放', icon: 'none' })
  },

  /** 跳转我的月卡 */
  goToMonthCards() {
    wx.showToast({ title: '我的月卡 — 后续版本开放', icon: 'none' })
  },

  /** 跳转优惠券 */
  goToCoupons() {
    wx.showToast({ title: '优惠券 — 后续版本开放', icon: 'none' })
  },
})
