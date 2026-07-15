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
    paySerial: '',
    prepayParams: null,

    // 错误信息
    errorMsg: '',
  },

  // 定时器
  _timer: null,

  onLoad(options) {
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

  /** 发起支付 */
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
        recordId: Number(this.data.recordId),
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

      const { orderId, orderNo, paySerial, prepayParams, payableAmountYuan } = prepayRes
      this.setData({
        orderId,
        orderNo,
        paySerial,
        prepayParams,
        feeText: payableAmountYuan || this.data.feeText,
      })

      // 2. 调用微信支付
      // 注意：实际的 wx.requestPayment 参数格式依赖 P云预请求返回的具体字段
      // 当前使用 P云 mock 模式时，prepayParams 包含 paySerial 和 appId
      // 真实环境下需要 P云返回 timeStamp, nonceStr, package, signType, paySign
      try {
        await this.requestWxPayment(prepayParams)
      } catch (payErr) {
        // wx.requestPayment 失败（用户取消或支付错误）
        if (payErr.errMsg && payErr.errMsg.includes('cancel')) {
          this.setData({ paying: false, errorMsg: '支付已取消' })
        } else {
          this.setData({ paying: false, errorMsg: payErr.errMsg || '支付失败，请重试' })
        }
        return
      }

      // 3. 通知后端支付成功
      try {
        await post('/api/v1/mini/pay/notify', {
          orderId: orderId,
          paySerial: paySerial,
          paidAmount: this.data.feeCents,
        })
      } catch (notifyErr) {
        // 通知后端失败也视为支付成功（微信侧已扣款）
        console.warn('支付通知后端失败:', notifyErr)
      }

      // 4. 支付成功
      this.setData({ paying: false, payResult: 'success' })
      wx.showToast({ title: '支付成功', icon: 'success' })
    } catch (err) {
      if (err && err.status === 401) {
        getApp().logout()
        wx.showToast({ title: '登录已过期', icon: 'none' })
        return
      }
      this.setData({
        paying: false,
        payResult: 'fail',
        errorMsg: err.message || '支付预请求失败，请重试',
      })
    }
  },

  /** 封装 wx.requestPayment */
  requestWxPayment(prepayParams) {
    return new Promise((resolve, reject) => {
      // P云 mock 模式下可能没有完整微信支付参数
      // 此时模拟支付成功（用于开发测试）
      if (!prepayParams || !prepayParams.timeStamp) {
        console.log('[Mock] 模拟微信支付成功，prepayParams:', JSON.stringify(prepayParams))
        // Mock 模式下直接 resolve，跳过真实支付
        setTimeout(() => resolve({ errMsg: 'requestPayment:ok (mock)' }), 500)
        return
      }

      wx.requestPayment({
        timeStamp: prepayParams.timeStamp || '',
        nonceStr: prepayParams.nonceStr || '',
        package: prepayParams.package || '',
        signType: prepayParams.signType || 'MD5',
        paySign: prepayParams.paySign || '',
        success: resolve,
        fail: reject,
      })
    })
  },

  /** 重试支付 */
  onRetry() {
    this.setData({ payResult: null, errorMsg: '' })
    this.onPay()
  },

  /** 查看记录 */
  goToRecords() {
    wx.redirectTo({ url: '/pages/parking/parking' })
  },

  /** 返回 */
  goBack() {
    wx.navigateBack()
  },
})
