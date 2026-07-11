/**
 * 首页 — 当前停车状态
 * T10 占位页面，后续 T41（小程序当前停车、费用与记录页面）实现完整功能
 */
Page({
  data: {
    loading: true,
    hasActiveOrder: false,
    currentOrder: null,
  },

  onLoad() {
    this.checkLoginStatus()
  },

  onShow() {
    // 每次显示时刷新
  },

  checkLoginStatus() {
    const app = getApp()
    if (app.globalData.token) {
      this.setData({ loading: false })
    } else {
      app.checkLogin().then(() => {
        this.setData({ loading: false })
      }).catch(() => {
        this.setData({ loading: false })
      })
    }
  },

  /** 跳转车牌管理 */
  goToPlateManage() {
    wx.showToast({ title: '车牌管理 — 后续版本开放', icon: 'none' })
  },

  /** 跳转停车记录 */
  goToRecords() {
    wx.showToast({ title: '停车记录 — 后续版本开放', icon: 'none' })
  },
})
