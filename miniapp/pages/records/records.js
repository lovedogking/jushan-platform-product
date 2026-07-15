const { get } = require('../../utils/request')

const PAGE_SIZE = 10
// 真实后端接口：GET /api/v1/mini/parking-records?current=&size=
// 该接口已在 MiniUserController 实现，正常使用真实数据。
// 仅当请求失败（后端未启动/网络异常/非 401 错误）时回退到模拟数据用于界面预览。
const NOTICE_TEXT = '【接口请求失败】停车记录接口 /api/v1/mini/parking-records 调用失败，已展示模拟数据用于界面预览。'

const MOCK_RECORDS = [
  {
    id: 1,
    parkingLotName: '测试一号停车场',
    plateNumber: '京A12345',
    entryTime: '2026-07-14T08:00:00',
    exitTime: null,
    durationMinutes: 120,
    feeAmount: 15.00,
    feeCents: 1500,
    payStatus: 'UNPAID',
    status: 'IN',
  },
  {
    id: 2,
    parkingLotName: '测试二号停车场',
    plateNumber: '京B67890',
    entryTime: '2026-07-13T18:00:00',
    exitTime: '2026-07-13T20:30:00',
    durationMinutes: 150,
    feeAmount: 20.00,
    feeCents: 2000,
    payStatus: 'PAID',
    status: 'OUT',
  },
]

const PAY_STATUS_MAP = {
  UNPAID: '未支付',
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
  if (!iso) {
    return ''
  }
  const d = new Date(iso)
  if (isNaN(d.getTime())) {
    return iso
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDuration(minutes) {
  if (minutes === undefined || minutes === null) {
    return '-'
  }
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m > 0 ? `${h}小时${m}分钟` : `${h}小时`
}

function formatFeeFromCents(feeCents) {
  if (feeCents === undefined || feeCents === null) {
    return '0.00'
  }
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
    notice: '',
  },

  onLoad() {
    this.loadRecords(true)
  },

  onPullDownRefresh() {
    this.setData({
      current: 1,
      records: [],
      hasMore: true,
    })
    this.loadRecords(true).finally(() => {
      wx.stopPullDownRefresh()
    })
  },

  loadMore() {
    if (this.data.loading || !this.data.hasMore) {
      return
    }
    this.setData({ current: this.data.current + 1 })
    this.loadRecords(false)
  },

  async loadRecords(reset = false) {
    if (this.data.loading) {
      return
    }
    this.setData({ loading: true })
    try {
      // 真实接口：GET /api/v1/mini/parking-records，请求参数 current/size（MyBatis-Plus IPage）
      const res = await get('/api/v1/mini/parking-records', {
        current: this.data.current,
        size: PAGE_SIZE,
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
      this.setData({ records, hasMore, notice: '' })
    } catch (err) {
      if (err && err.status === 401) {
        getApp().logout()
        wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
        wx.reLaunch({ url: '/pages/index/index' })
        return
      }
      wx.showToast({ title: err.message || '请求失败', icon: 'none' })
      const mock = MOCK_RECORDS.map(enrichRecord)
      this.setData({ records: mock, hasMore: false, notice: NOTICE_TEXT })
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 点击记录进入支付/详情页 */
  goToPay(e) {
    const recordId = e.currentTarget.dataset.recordId
    if (recordId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${recordId}` })
    }
  },
})
