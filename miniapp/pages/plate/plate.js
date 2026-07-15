/**
 * 车牌管理页
 * 提供车牌绑定、解绑、设为默认及列表展示功能。
 */
const { get, post, put, del } = require('../../utils/request')

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

const MOCK_PLATES = [
  { id: 1, plate: '京A12345', vehicleType: 'SMALL', isDefault: true, verifyStatus: 'APPROVED', createdAt: '2026-07-10T10:00:00' },
  { id: 2, plate: '京B67890', vehicleType: 'NEW_ENERGY', isDefault: false, verifyStatus: 'APPROVED', createdAt: '2026-07-12T14:30:00' },
]

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
      // 【需后端补充】后端 WxUserController 仅提供 POST /wx/plates（绑定）、
      // DELETE /wx/plates/{id}（解绑）、PUT /wx/plates/{id}/default（默认），
      // 尚未提供 GET /wx/plates 列表查询接口。此处请求会 404，失败时回退到模拟数据，
      // 待后端补齐列表接口后即为真实数据，无需改动前端。
      const data = await get('/wx/plates')
      const list = Array.isArray(data) ? data : (data && data.list) || []
      this.setData({
        plates: list.map(item => this.formatPlate(item)),
        backendMissingTip: '',
      })
    } catch (err) {
      this.setData({
        plates: MOCK_PLATES.map(item => this.formatPlate(item)),
        backendMissingTip: '【需后端补充】车牌列表查询接口 GET /wx/plates 后端尚未提供，当前展示模拟数据。绑定/解绑/默认已对接真实接口。',
      })
      if (err.status === 401) {
        this.handleAuthError()
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
