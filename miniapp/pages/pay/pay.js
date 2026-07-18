/**
 * 停车缴费页面
 * Task 6: 显示停车详情，支持微信支付，支付成功通知后端
 */
const { get, post } = require('../../utils/request')

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

function checkPhoneBinding() {
  const app = getApp()
  if (!app.globalData.token) {
    wx.showModal({
      title: '请先登录',
      content: '需要登录后才能使用此功能',
      confirmText: '去登录',
      success: (res) => {
        if (res.confirm) { app.doLogin() }
        else { wx.switchTab({ url: '/pages/index/index' }) }
      },
    })
    return false
  }
  if (!app.globalData.phoneBound) {
    wx.showModal({
      title: '请先绑定手机号',
      content: '绑定手机号后即可使用停车缴费等服务',
      confirmText: '去绑定',
      success: (res) => {
        if (res.confirm) { wx.navigateTo({ url: '/pages/bind-phone/bind-phone' }) }
      },
    })
    return false
  }
  return true
}

Page({
  data: {
    recordId: null,
    loading: true,
    paying: false,
    payResult: null, // null | 'success' | 'fail'

    // 停车详情
    plateNumber: '',
    parkingLotName: '',
    entryTime: '',
    entryTimeText: '',
    exitTimeText: '',
    durationMinutes: 0,
    durationText: '',
    feeCents: 0,
    feeText: '0.00',
    status: '',
    statusText: '',
    payable: false,
    message: '',

    // 订单信息
    orderId: null,
    orderNo: '',

    // 错误信息
    errorMsg: '',
  },

  // 定时器
  _timer: null,

  onLoad(options) {
    if (!checkPhoneBinding()) { return }
    const recordId = options.recordId
    if (!recordId) {
      wx.showToast({ title: '缺少记录 ID', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1500)
      return
    }
    this.setData({ recordId })
    this.loadDetail()
  },

  onShow() {
    // 从支付页返回时刷新
    if (this.data.recordId && !this.data.loading) {
      this.loadDetail()
    }
  },

  onUnload() {
    if (this._timer) {
      clearInterval(this._timer)
      this._timer = null
    }
  },

  async loadDetail() {
    this.setData({ loading: true })
    try {
      const detail = await get(`/api/v1/mini/parking-records/${this.data.recordId}`)
      this.setData({
        plateNumber: detail.plateNumber || '',
        parkingLotName: detail.parkingLotName || '未知',
        entryTime: detail.entryTime,
        entryTimeText: formatDateTime(detail.entryTime),
        exitTimeText: formatDateTime(detail.exitTime),
        durationMinutes: detail.durationMinutes || 0,
        durationText: formatDuration(detail.durationMinutes),
        feeCents: detail.feeCents || 0,
        feeText: detail.feeCents ? (detail.feeCents / 100).toFixed(2) : '0.00',
        status: detail.status,
        statusText: detail.status === 'IN' ? '在场' : '已出场',
        payable: detail.status === 'IN' && detail.payStatus === 'UNPAID',
        message: detail.message || '',
        loading: false,
      })

      // 在场车辆定时刷新时长
      if (detail.status === 'IN') {
        this.startDurationTimer()
      }
    } catch (err) {
      if (err && err.status === 401) {
        getApp().logout()
        wx.showToast({ title: '登录已过期', icon: 'none' })
        wx.reLaunch({ url: '/pages/index/index' })
        return
      }
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
      this.setData({ loading: false })
    }
  },

  /** 定时刷新时长（每30秒） */
  startDurationTimer() {
    if (this._timer) clearInterval(this._timer)
    this._timer = setInterval(() => {
      if (!this.data.entryTime) return
      const entry = new Date(this.data.entryTime)
      if (isNaN(entry.getTime())) return
      const now = new Date()
      const diffMinutes = Math.floor((now - entry) / 60000)
      this.setData({
        durationMinutes: diffMinutes,
        durationText: formatDuration(diffMinutes),
      })
    }, 30000)
  },

  /** 发起支付（模拟支付：预下单 → 确认支付） */
  async onPay() {
    if (this.data.paying) return

    // 已支付的直接返回
    if (this.data.payResult === 'success') {
      wx.showToast({ title: '已支付成功', icon: 'none' })
      return
    }

    this.setData({ paying: true, errorMsg: '' })

    try {
      // 1. 预下单
      const prepayRes = await post('/api/v1/mini/pay/prepare', {
        recordId: this.data.recordId,
      })

      // 零元订单直接成功
      if (prepayRes.status === 'COMPLETED') {
        this.setData({
          paying: false,
          payResult: 'success',
          orderId: prepayRes.orderId,
          orderNo: prepayRes.orderNo,
          feeText: '0.00',
          feeCents: 0,
        })
        return
      }

      const { orderId, orderNo, payableAmountYuan } = prepayRes
      this.setData({
        orderId,
        orderNo,
        feeText: payableAmountYuan || this.data.feeText,
      })

      // 2. 模拟支付确认（直接调用后端通知，无需 wx.requestPayment）
      await post('/api/v1/mini/pay/notify', {
        orderId: orderId,
        paidAmount: this.data.feeCents,
      })

      // 3. 支付成功
      this.setData({ paying: false, payResult: 'success' })
      wx.showToast({ title: '支付成功', icon: 'success' })
      // 引导订阅消息授权（非阻塞）
      this.requestSubscribe()
    } catch (err) {
      if (err && err.status === 401) {
        getApp().logout()
        wx.showToast({ title: '登录已过期', icon: 'none' })
        return
      }
      this.setData({
        paying: false,
        payResult: 'fail',
        errorMsg: err.message || '支付失败，请重试',
      })
    }
  },

  /** 重试支付 */
  onRetry() {
    this.setData({ payResult: null, errorMsg: '' })
    this.onPay()
  },

  /** 查看记录 */
  goToRecords() {
    wx.redirectTo({ url: '/pages/records/records' })
  },

  /** 返回 */
  goBack() {
    wx.navigateBack()
  },

  /** 引导订阅消息授权（非阻塞） */
  requestSubscribe() {
    wx.requestSubscribeMessage({
      tmplIds: [''],
      success: () => {},
      fail: () => {},
    })
  },
})
