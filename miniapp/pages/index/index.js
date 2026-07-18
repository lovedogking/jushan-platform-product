/**
 * 首页 — 当前停车状态与快捷操作
 * Task 6: 支持查看当前在场车辆、最近停车记录，并提供缴费入口
 */
const { get } = require('../../utils/request')

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function formatDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDuration(minutes) {
  if (minutes === undefined || minutes === null) return '-'
  if (minutes < 60) return `${minutes}分钟`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m > 0 ? `${h}小时${m}分钟` : `${h}小时`
}

function formatFeeYuan(cents) {
  if (cents === undefined || cents === null) return '0.00'
  return (cents / 100).toFixed(2)
}

Page({
  data: {
    loading: true,
    loggedIn: false,
    plates: [],
    activeSessions: [],
    recentRecords: [],
    defaultPlate: '',
    defaultPlateText: '全部车牌',
  },

  onLoad() {
    this.checkLoginAndLoad()
  },

  onShow() {
    if (this.data.loggedIn) {
      this.loadData()
    } else {
      this.checkLoginAndLoad()
    }
  },

  onPullDownRefresh() {
    this.loadData().finally(() => wx.stopPullDownRefresh())
  },

  checkLoginAndLoad() {
    const app = getApp()
    if (app.globalData.token) {
      this.setData({ loggedIn: true })
      this.loadData()
    } else {
      this.setData({ loading: false, loggedIn: false })
    }
  },

  async loadData() {
    this.setData({ loading: true })
    try {
      const [plates, sessions, records] = await Promise.all([
        this.fetchBoundPlates(),
        this.fetchCurrentSessions(),
        this.fetchRecentRecords(),
      ])
      this.setData({
        plates: plates || [],
        activeSessions: sessions || [],
        recentRecords: records || [],
        defaultPlateText: plates && plates.length > 0 ? (plates[0] || '全部车牌') : '未绑定车牌',
        loading: false,
      })
    } catch (err) {
      if (err && err.status === 401) {
        getApp().logout()
        this.setData({ loggedIn: false, loading: false })
        return
      }
      this.setData({ loading: false })
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    }
  },

  async fetchBoundPlates() {
    try {
      const plates = await get('/api/v1/mini/bound-plates')
      return Array.isArray(plates) ? plates : []
    } catch {
      return []
    }
  },

  async fetchCurrentSessions() {
    try {
      const sessions = await get('/api/v1/mini/current-sessions')
      if (!Array.isArray(sessions)) return []
      return sessions.map(s => ({
        ...s,
        entryTimeText: formatDateTime(s.entryTime),
        durationText: formatDuration(s.durationMinutes),
        feeText: s.feeCents ? formatFeeYuan(s.feeCents) : '0.00',
      }))
    } catch {
      return []
    }
  },

  async fetchRecentRecords() {
    try {
      const res = await get('/api/v1/mini/parking-records', { current: 1, size: 3 })
      let list = []
      if (Array.isArray(res)) {
        list = res
      } else if (res && Array.isArray(res.records)) {
        list = res.records
      }
      return list.map(r => ({
        ...r,
        entryTimeText: formatDateTime(r.entryTime),
        exitTimeText: formatDateTime(r.exitTime),
        durationText: formatDuration(r.durationMinutes),
        feeText: r.feeCents ? formatFeeYuan(r.feeCents) : '0.00',
        payStatusText: r.payStatus === 'PAID' ? '已支付' : r.payStatus === 'FREE' ? '免费' : '待支付',
        statusText: r.status === 'IN' ? '在场' : '已出场',
      }))
    } catch {
      return []
    }
  },

  /** 跳转车牌管理 */
  goToPlateManage() {
    wx.navigateTo({ url: '/pages/plate/plate' })
  },

  /** 跳转停车记录 */
  goToRecords() {
    wx.navigateTo({ url: '/pages/records/records' })
  },

  /** 跳转支付页 */
  goToPay(e) {
    const recordId = e.currentTarget.dataset.recordId
    if (recordId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${recordId}` })
    }
  },

  /** 在场车辆立即缴费 */
  goToPayActive(e) {
    const recordId = e.currentTarget.dataset.recordId
    if (recordId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${recordId}` })
    }
  },

  /** Phase 3 E1: 代缴停车费 */
  goToProxyPay() {
    wx.navigateTo({ url: '/pages/proxy-pay/proxy-pay' })
  },

  /** Phase 3 E2: 余位查询 */
  goToLotSpace() {
    wx.navigateTo({ url: '/pages/lot-space/lot-space' })
  },
})
