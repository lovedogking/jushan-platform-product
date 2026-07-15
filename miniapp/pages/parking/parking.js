/**
 * 停车记录列表页
 * Task 6: 展示历史停车记录，下拉刷新，待支付记录高亮，点击进入支付
 */
const { get } = require('../../utils/request')

const PAGE_SIZE = 10

const PAY_STATUS_MAP = {
  UNPAID: '待支付',
  PAID: '已支付',
  FREE: '免费',
}

const STATUS_MAP = {
  IN: '在场',
  OUT: '已出场',
}

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

function enrichRecord(record) {
  return {
    ...record,
    recordId: record.recordId || record.id,
    entryTimeText: formatDateTime(record.entryTime),
    exitTimeText: formatDateTime(record.exitTime),
    durationText: formatDuration(record.durationMinutes),
    feeText: record.feeCents ? (record.feeCents / 100).toFixed(2) : '0.00',
    payStatusText: PAY_STATUS_MAP[record.payStatus] || record.payStatus,
    statusText: STATUS_MAP[record.status] || record.status,
  }
}

Page({
  data: {
    records: [],
    loading: false,
    hasMore: true,
    current: 1,
    size: PAGE_SIZE,
    plates: [],
    filterPlate: '',
  },

  onLoad() {
    this.loadPlates()
    this.loadRecords(true)
  },

  onPullDownRefresh() {
    this.setData({ current: 1, records: [], hasMore: true })
    this.loadRecords(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  async loadPlates() {
    try {
      const plates = await get('/api/v1/mini/bound-plates')
      this.setData({ plates: Array.isArray(plates) ? plates : [] })
    } catch {
      // 静默处理
    }
  },

  loadMore() {
    if (this.data.loading || !this.data.hasMore) return
    this.setData({ current: this.data.current + 1 })
    this.loadRecords(false)
  },

  async loadRecords(reset) {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const params = { current: this.data.current, size: PAGE_SIZE }
      let res
      if (this.data.filterPlate) {
        res = await get(`/api/v1/mini/parking-records/plate/${encodeURIComponent(this.data.filterPlate)}`, params)
      } else {
        res = await get('/api/v1/mini/parking-records', params)
      }

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
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 车牌切换 */
  onPlateChange(e) {
    const plate = this.data.plates[e.detail.value] || ''
    this.setData({ filterPlate: plate, current: 1, records: [], hasMore: true })
    this.loadRecords(true)
  },

  /** 点击进入支付 / 详情页 */
  goToPay(e) {
    const recordId = e.currentTarget.dataset.recordId
    if (recordId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${recordId}` })
    }
  },
})
