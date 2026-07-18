/**
 * 代缴停车费 — Phase 3 E1
 * 为任意车牌号代缴停车费
 * 两步流程：查询费用 → 确认代缴
 */
const { post } = require('../../utils/request')

function pad(n) { return n < 10 ? '0' + n : '' + n }

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
      content: '绑定手机号后即可使用代缴停车费等服务',
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
    // 输入
    plateNumber: '',
    searching: false,
    showFee: false,
    payable: false,

    // 停车详情
    plateNumberDisplay: '',
    parkingLotName: '',
    entryTime: '',
    entryTimeText: '',
    durationMinutes: 0,
    durationText: '',
    feeCents: 0,
    feeText: '0.00',

    // 支付
    paying: false,
    payResult: null, // null | 'success' | 'fail'
    orderId: null,
    orderNo: '',
    amountYuan: '',
    errorMsg: '',
  },

  onLoad() {
    if (!checkPhoneBinding()) { return }
    this.getClipboardPlate()
  },

  getClipboardPlate() {
    try {
      wx.getClipboardData({
        success: (res) => {
          if (res.data && /^[一-龥A-Za-z0-9]{5,10}$/.test(res.data.trim())) {
            this.setData({ plateNumber: res.data.trim().toUpperCase() })
          }
        },
      })
    } catch (e) {
      // 忽略剪切板读取失败
    }
  },

  onPlateInput(e) {
    this.setData({
      plateNumber: e.detail.value.toUpperCase(),
      showFee: false,
      payResult: null,
      errorMsg: '',
    })
  },

  /** 查询费用 */
  async onSearch() {
    const plate = this.data.plateNumber.replace(/\s+/g, '').toUpperCase()
    if (!plate || plate.length < 5) {
      wx.showToast({ title: '请输入完整的车牌号', icon: 'none' })
      return
    }

    this.setData({ searching: true, showFee: false, errorMsg: '' })
    try {
      const result = await post('/api/v1/mini/pay/proxy-preview', {
        plateNumber: plate,
      })
      this.setData({
        searching: false,
        showFee: true,
        payable: result.payable || false,
        plateNumberDisplay: result.plateNumber || plate,
        parkingLotName: result.parkingLotName || '未知停车场',
        entryTime: result.entryTime || '',
        entryTimeText: formatDateTime(result.entryTime),
        durationMinutes: result.durationMinutes || 0,
        durationText: formatDuration(result.durationMinutes),
        feeCents: result.feeCents || 0,
        feeText: result.feeYuan || '0.00',
      })
    } catch (err) {
      this.setData({
        searching: false,
        showFee: false,
        errorMsg: err.message || '查询失败',
      })
      wx.showToast({ title: err.message || '查询失败', icon: 'none' })
    }
  },

  /** 确认代缴 */
  async onConfirmPay() {
    if (this.data.paying) return

    this.setData({ paying: true, errorMsg: '' })
    try {
      const result = await post('/api/v1/mini/pay/proxy-pay', {
        plateNumber: this.data.plateNumber,
      })
      this.setData({
        paying: false,
        payResult: 'success',
        orderId: result.orderId,
        orderNo: result.orderNo || '',
        amountYuan: result.amountYuan || '0.00',
        feeText: result.amountYuan || '0.00',
      })
      wx.showToast({ title: '代缴成功', icon: 'success' })
      this.requestSubscribe()
    } catch (err) {
      this.setData({
        paying: false,
        payResult: 'fail',
        errorMsg: err.message || '代缴失败',
      })
    }
  },

  goBack() {
    wx.navigateBack()
  },

  goToHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  requestSubscribe() {
    wx.requestSubscribeMessage({
      tmplIds: [''],
      success: () => {},
      fail: () => {},
    })
  },
})
