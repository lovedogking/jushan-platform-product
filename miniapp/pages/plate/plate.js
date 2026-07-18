/**
 * 车牌管理页
 * 提供车牌绑定、解绑、设为默认及列表展示功能。
 */
const { get, post, put, del } = require('../../utils/request')

const BIND_LIMIT = 3

const VEHICLE_TYPE_MAP = {
  SMALL: '小型车',
  NEW_ENERGY: '新能源车',
  LARGE: '大型车',
  MOTORCYCLE: '摩托车',
}

const VERIFY_STATUS_MAP = {
  APPROVED: '已认证',
  PENDING: '审核中',
  REJECTED: '认证失败',
  UNVERIFIED: '未认证',
}

function checkPhoneBinding() {
  var app = getApp()
  if (!app.globalData.token) {
    wx.showModal({
      title: '请先登录',
      content: '需要登录后才能使用此功能',
      confirmText: '去登录',
      success: function(res) {
        if (res.confirm) { app.doLogin() }
        else { wx.switchTab({ url: '/pages/index/index' }) }
      },
    })
    return false
  }
  if (!app.globalData.phoneBound) {
    wx.showModal({
      title: '请先绑定手机号',
      content: '绑定手机号后即可管理您的车辆',
      confirmText: '去绑定',
      success: function(res) {
        if (res.confirm) { wx.navigateTo({ url: '/pages/bind-phone/bind-phone' }) }
      },
    })
    return false
  }
  return true
}

Page({
  data: {
    newPlate: '',
    plates: [],
    loading: false,
    binding: false,
    actionLoading: false,
    backendMissingTip: '',
  },

  onLoad() {
    if (!checkPhoneBinding()) { return }
    this.loadPlates()
  },

  async onPullDownRefresh() {
    try {
      await this.loadPlates()
    } finally {
      wx.stopPullDownRefresh()
    }
  },

  onInputChange(e) {
    const { field } = e.currentTarget.dataset
    this.setData({ [field]: e.detail.value })
  },

  async loadPlates() {
    this.setData({ loading: true })
    try {
      const data = await get('/api/v1/mini/plates')
      const list = Array.isArray(data) ? data : (data && data.list) || []
      this.setData({
        plates: list.map(item => this.formatPlate(item)),
        backendMissingTip: '',
      })
    } catch (err) {
      this.setData({
        plates: [],
        backendMissingTip: '车牌列表加载失败，请检查网络后重试',
      })
      if (err.status === 401) {
        this.handleAuthError()
      } else {
        wx.showToast({ title: err.message || '加载失败', icon: 'none' })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  formatPlate(item) {
    return {
      ...item,
      vehicleTypeText: VEHICLE_TYPE_MAP[item.vehicleType] || item.vehicleType || '未知',
      verifyStatusText: VERIFY_STATUS_MAP[item.verifyStatus] || item.verifyStatus || '未知',
    }
  },

  async bindPlate() {
    const plate = (this.data.newPlate || '').trim().toUpperCase()
    if (!plate) {
      wx.showToast({ title: '请输入车牌号码', icon: 'none' })
      return
    }

    // 检查绑定上限
    if (this.data.plates.length >= BIND_LIMIT) {
      wx.showToast({ title: '绑定数量已达上限（' + BIND_LIMIT + '辆）', icon: 'none' })
      return
    }

    this.setData({ binding: true })
    try {
      await post('/wx/plates', { plate, vehicleType: 'SMALL' })
      wx.showToast({ title: '绑定成功', icon: 'success' })
      this.setData({ newPlate: '' })
      await this.loadPlates()
    } catch (err) {
      if (err.status === 401) {
        this.handleAuthError()
      } else {
        wx.showToast({ title: err.message || '绑定失败', icon: 'none' })
      }
    } finally {
      this.setData({ binding: false })
    }
  },

  async setDefault(e) {
    const { id } = e.currentTarget.dataset
    this.setData({ actionLoading: true })
    try {
      await put(`/wx/plates/${id}/default`)
      wx.showToast({ title: '设置成功', icon: 'success' })
      await this.loadPlates()
    } catch (err) {
      if (err.status === 401) {
        this.handleAuthError()
      } else {
        wx.showToast({ title: err.message || '设置失败', icon: 'none' })
      }
    } finally {
      this.setData({ actionLoading: false })
    }
  },

  async unbindPlate(e) {
    const { id, plate } = e.currentTarget.dataset
    const res = await wx.showModal({
      title: '确认解绑',
      content: `确定要解绑车牌 ${plate || id} 吗？`,
      confirmColor: '#f53f3f',
    })
    if (!res.confirm) return

    this.setData({ actionLoading: true })
    try {
      await del(`/wx/plates/${id}`)
      wx.showToast({ title: '解绑成功', icon: 'success' })
      await this.loadPlates()
    } catch (err) {
      if (err.status === 401) {
        this.handleAuthError()
      } else {
        wx.showToast({ title: err.message || '解绑失败', icon: 'none' })
      }
    } finally {
      this.setData({ actionLoading: false })
    }
  },

  handleAuthError() {
    const app = getApp()
    app.logout()
    wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
    wx.reLaunch({ url: '/pages/index/index' })
  },
})
