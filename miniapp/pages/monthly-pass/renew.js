/**
 * 月卡续费页
 * 任务包 5-2 月卡部分
 */
const { get, post } = require('../../utils/request')

const MONTH_OPTIONS = [
  { label: '1个月', value: 1, months: 1 },
  { label: '3个月', value: 3, months: 3 },
  { label: '6个月', value: 6, months: 6 },
  { label: '12个月', value: 12, months: 12 },
]

Page({
  data: {
    passId: null,
    plateNumber: '',
    parkingLotName: '',
    validEndDate: '',
    months: 1,
    monthOptions: MONTH_OPTIONS,
    monthIndex: 0,
    amountCents: 0,
    newValidEndDate: '',
    loading: false,
    submitting: false,
  },

  onLoad(options) {
    var id = options.id
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(function () { wx.navigateBack() }, 1500)
      return
    }
    this.setData({ passId: id })
    this.loadDetail()
  },

  async loadDetail() {
    this.setData({ loading: true })
    try {
      var detail = await get('/api/v1/mini/monthly-passes/' + this.data.passId)
      this.setData({
        plateNumber: detail.plateNumber || '',
        parkingLotName: detail.parkingLotName || '未知车场',
        validEndDate: detail.validEndDate || '',
      })
      this.calcAmount()
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onMonthChange(e) {
    var idx = e.detail.value
    var opt = MONTH_OPTIONS[idx]
    this.setData({ monthIndex: idx, months: opt.months })
    this.calcAmount()
  },

  calcAmount() {
    // Use a hardcoded default of 30000 cents/month since we don't expose the price via the API
    var pricePerMonth = 30000
    var amount = pricePerMonth * this.data.months
    this.setData({ amountCents: amount })

    // Calculate new valid end date preview
    var currentEnd = this.data.validEndDate
    if (currentEnd) {
      var d = new Date(currentEnd)
      if (!isNaN(d.getTime())) {
        d.setMonth(d.getMonth() + this.data.months)
        var y = d.getFullYear()
        var m = ('0' + (d.getMonth() + 1)).slice(-2)
        var day = ('0' + d.getDate()).slice(-2)
        this.setData({ newValidEndDate: y + '-' + m + '-' + day })
      }
    }
  },

  async onSubmit() {
    var that = this
    wx.showModal({
      title: '确认续费',
      content: '续费 ' + that.data.months + ' 个月，金额 ¥' + (that.data.amountCents / 100).toFixed(2) + '，确认支付？',
      success: function (res) {
        if (res.confirm) {
          that.doRenew()
        }
      },
    })
  },

  async doRenew() {
    this.setData({ submitting: true })
    try {
      await post('/api/v1/mini/monthly-passes/' + this.data.passId + '/renew', {
        months: this.data.months,
      })
      wx.showToast({ title: '续费成功', icon: 'success' })
      this.requestSubscribe()
      setTimeout(function () {
        wx.navigateBack()
      }, 1500)
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '续费失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  requestSubscribe() {
    wx.requestSubscribeMessage({
      tmplIds: [''],
      success: () => {},
      fail: () => {},
    })
  },
})
