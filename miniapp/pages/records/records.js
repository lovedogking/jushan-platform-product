const { get } = require('../../utils/request')

const PAGE_SIZE = 10

const TABS = [
  { key: 'in_progress', label: '在场中' },
  { key: 'pending_pay', label: '待支付' },
  { key: 'completed', label: '已完成' },
]

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

function formatFeeFromCents(feeCents) {
  if (feeCents === undefined || feeCents === null) return '0.00'
  return (feeCents / 100).toFixed(2)
}

function enrichRecord(record) {
  return {
    ...record,
    recordId: record.recordId || record.id,
    entryTimeText: formatDateTime(record.entryTime),
    exitTimeText: formatDateTime(record.exitTime),
    durationText: formatDuration(record.durationMinutes),
    feeText: record.feeCents ? formatFeeFromCents(record.feeCents) : '0.00',
    isArrears: record.payStatus === 'ARREARS',
    isProxyPay: record.proxyPay === true,
  }
}

Page({
  data: {
    tabs: TABS,
    activeTab: 'in_progress',
    records: [],
    loading: false,
    hasMore: true,
    current: 1,
    size: PAGE_SIZE,
  },

  onLoad() {
    this.loadRecords(true)
  },

  onPullDownRefresh() {
    this.setData({ current: 1, records: [], hasMore: true })
    this.loadRecords(true).finally(() => wx.stopPullDownRefresh())
  },

  /** 切换标签 */
  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.activeTab) return
    this.setData({
      activeTab: tab,
      current: 1,
      records: [],
      hasMore: true,
    })
    this.loadRecords(true)
  },

  loadMore() {
    if (this.data.loading || !this.data.hasMore) return
    this.setData({ current: this.data.current + 1 })
    this.loadRecords(false)
  },

  async loadRecords(reset = false) {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const res = await get('/api/v1/mini/parking-records', {
        current: this.data.current,
        size: PAGE_SIZE,
        tab: this.data.activeTab,
      })
      let list = []
      let total = null
      if (Array.isArray(res)) {
        list = res
      } else if (res && Array.isArray(res.records)) {
        list = res.records
        total = res.total
      } else if (res && Array.isArray(res.list)) {
        list = res.list
        total = res.total
      }
      const newRecords = list.map(enrichRecord)
      const records = reset ? newRecords : this.data.records.concat(newRecords)
      let hasMore = true
      if (typeof total === 'number') {
        hasMore = records.length < total
      } else {
        hasMore = list.length === PAGE_SIZE
      }
      this.setData({ records, hasMore })
    } catch (err) {
      if (err && err.status === 401) {
        getApp().logout()
        wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
        wx.reLaunch({ url: '/pages/index/index' })
        return
      }
      wx.showToast({ title: err.message || '请求失败', icon: 'none' })
      if (reset) {
        this.setData({ records: [], hasMore: false })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 点击待支付记录 → 进入支付页 */
  goToPay(e) {
    const recordId = e.currentTarget.dataset.recordId
    if (recordId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${recordId}` })
    }
  },

  /** 点击已完成记录 → 查看详情 */
  goToDetail(e) {
    const recordId = e.currentTarget.dataset.recordId
    if (recordId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${recordId}` })
    }
  },
})
