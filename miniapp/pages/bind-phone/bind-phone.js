/**
 * 手机号绑定引导页
 * 使用微信 getPhoneNumber 一键绑定
 */
const { post } = require('../../utils/request')

Page({
  data: {
    loading: false,
  },

  async onGetPhoneNumber(e) {
    if (e.detail.errMsg && e.detail.errMsg.includes('deny')) {
      wx.showToast({ title: '需要授权手机号才能继续', icon: 'none', duration: 2000 })
      return
    }
    if (!e.detail.code) return

    this.setData({ loading: true })
    const { code, iv, encryptedData } = e.detail
    try {
      await post('/api/v1/mini/phone', { code, iv, encryptedData })
      getApp().globalData.phoneBound = true
      wx.showToast({ title: '绑定成功', icon: 'success', duration: 1500 })
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' })
      }, 1500)
    } catch (err) {
      wx.showModal({
        title: '绑定失败',
        content: (err && err.message) || '请重试',
        confirmText: '重试',
        cancelText: '稍后绑定',
        success: (res) => {
          if (!res.confirm) {
            wx.switchTab({ url: '/pages/index/index' })
          }
        },
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onSkip() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
