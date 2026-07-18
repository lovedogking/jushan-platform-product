/**
 * 我的固定车位 — 列表页
 * 任务包 5-2 固定车位部分
 */
const { get } = require('../../utils/request')

Page({
  data: {
    activeList: [],
    pendingPayList: [],
    otherList: [],
    loading: false,
    empty: true,
  },

  onLoad() {
    this.loadData()
  },

  onShow() {
    this.loadData()
  },

  onPullDownRefresh() {
    this.loadData().finally(() => wx.stopPullDownRefresh())
  },

  async loadData() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const list = await get('/api/v1/mini/fixed-spaces')
      const arr = Array.isArray(list) ? list : []
      const activeList = []
      const pendingPayList = []
      const otherList = []
      arr.forEach(function (item) {
        if (item.status === 1) {
          activeList.push(item)
        } else if (item.reviewStatus === 'APPROVED' && item.status !== 1) {
          pendingPayList.push(item)
        } else {
          otherList.push(item)
        }
      })
      this.setData({
        activeList: activeList,
        pendingPayList: pendingPayList,
        otherList: otherList,
        empty: arr.length === 0,
      })
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goToApply() {
    var app = getApp()
    if (!app.globalData.phoneBound) {
      wx.showModal({
        title: '请先绑定手机号',
        content: '办理固定车位前需要先绑定手机号',
        confirmText: '去绑定',
        cancelText: '取消',
        success: function (res) {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
          }
        },
      })
      return
    }
    wx.navigateTo({ url: '/pages/fixed-space/apply' })
  },

  goToPay(e) {
    var id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认支付',
      content: '支付后将立即生效，确认支付？',
      success: function (res) {
        if (res.confirm) {
          wx.showLoading({ title: '支付中' })
          const { post } = require('../../utils/request')
          post('/api/v1/mini/fixed-spaces/' + id + '/pay').then(function () {
            wx.hideLoading()
            wx.showToast({ title: '支付成功', icon: 'success' })
            this.loadData()
          }.bind(this)).catch(function (err) {
            wx.hideLoading()
            wx.showToast({ title: (err && err.message) || '支付失败', icon: 'none' })
          })
        }
      }.bind(this),
    })
  },

  goToRenew(e) {
    var id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/fixed-space/renew?id=' + id })
  },
})
