/**
 * 我的 — 个人中心
 * T10 占位页面
 */
Page({
  data: {
    isLoggedIn: false,
    displayName: '',
    phone: '',
    phoneBound: false,
  },

  onLoad() { this.refreshState() },
  onShow() { this.refreshState() },

  refreshState() {
    var app = getApp()
    var token = app.globalData.token
    var ownerInfo = app.globalData.ownerInfo
    if (token && ownerInfo) {
      this.setData({
        isLoggedIn: true,
        displayName: ownerInfo.displayName || ownerInfo.nickname || '车主',
        phone: ownerInfo.phone || ownerInfo.maskedPhone || '',
        phoneBound: app.globalData.phoneBound || false,
      })
    } else {
      this.setData({
        isLoggedIn: false, displayName: '', phone: '', phoneBound: false,
      })
    }
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: function(res) {
        if (res.confirm) {
          var app = getApp()
          app.logout()
          this.setData({ isLoggedIn: false, displayName: '', phone: '', phoneBound: false })
          wx.showToast({ title: '已退出', icon: 'success' })
        }
      }.bind(this),
    })
  },

  goToMessages() { wx.navigateTo({ url: '/pages/messages/messages' }) },
  goToVehicles() { wx.showToast({ title: '我的车辆 — 后续版本开放', icon: 'none' }) },
  goToMonthCards() { wx.showToast({ title: '我的月卡 — 后续版本开放', icon: 'none' }) },
  goToCoupons() { wx.showToast({ title: '优惠券 — 后续版本开放', icon: 'none' }) },
})
