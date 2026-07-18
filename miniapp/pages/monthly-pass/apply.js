/**
 * 申请月卡页
 * 任务包 5-2 月卡部分
 */
const { get, post } = require('../../utils/request')

Page({
  data: {
    lots: [],
    lotIndex: 0,
    selectedLotId: null,
    plates: [],
    plateIndex: 0,
    selectedPlate: '',
    ownerName: '',
    ownerPhone: '',
    submitLoading: false,
    phoneBound: false,
  },

  onLoad() {
    var app = getApp()
    this.setData({ phoneBound: app.globalData.phoneBound || false })
    if (!this.data.phoneBound) {
      wx.showModal({
        title: '请先绑定手机号',
        content: '办理月卡前需要先绑定手机号',
        confirmText: '去绑定',
        cancelText: '返回',
        success: function (res) {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
          } else {
            wx.navigateBack()
          }
        },
      })
      return
    }
    this.loadLots()
    this.loadPlates()
  },

  async loadLots() {
    try {
      const lots = await get('/api/v1/mini/parking-lots')
      const lotArr = Array.isArray(lots) ? lots : []
      this.setData({ lots: lotArr })
      if (lotArr.length > 0) {
        this.setData({ selectedLotId: lotArr[0].id })
      }
    } catch (err) {
      wx.showToast({ title: '加载车场失败', icon: 'none' })
    }
  },

  async loadPlates() {
    try {
      const plates = await get('/api/v1/mini/bound-plates')
      const plateArr = Array.isArray(plates) ? plates : []
      // bound-plates returns objects with plateNumber field
      const plateNumbers = plateArr.map(function (p) {
        return typeof p === 'string' ? p : (p.plateNumber || p.plate || '')
      }).filter(function (p) { return p !== '' })
      this.setData({ plates: plateNumbers })
      if (plateNumbers.length > 0) {
        this.setData({ selectedPlate: plateNumbers[0] })
      }
    } catch (err) {
      wx.showToast({ title: '加载车牌失败', icon: 'none' })
    }
  },

  onLotChange(e) {
    var idx = e.detail.value
    var lot = this.data.lots[idx]
    this.setData({ lotIndex: idx, selectedLotId: lot ? lot.id : null })
  },

  onPlateChange(e) {
    var idx = e.detail.value
    this.setData({ plateIndex: idx, selectedPlate: this.data.plates[idx] })
  },

  onNameInput(e) {
    this.setData({ ownerName: e.detail.value })
  },

  onPhoneInput(e) {
    this.setData({ ownerPhone: e.detail.value })
  },

  async onSubmit() {
    if (!this.data.selectedLotId) {
      wx.showToast({ title: '请选择车场', icon: 'none' })
      return
    }
    if (!this.data.selectedPlate) {
      wx.showToast({ title: '请选择车牌', icon: 'none' })
      return
    }

    this.setData({ submitLoading: true })
    try {
      await post('/api/v1/mini/monthly-passes', {
        parkingLotId: this.data.selectedLotId,
        plateNumber: this.data.selectedPlate,
        ownerName: this.data.ownerName,
        ownerPhone: this.data.ownerPhone,
      })
      wx.showToast({ title: '申请成功', icon: 'success' })
      this.requestSubscribe()
      setTimeout(function () {
        wx.navigateBack()
      }, 1500)
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '申请失败', icon: 'none' })
    } finally {
      this.setData({ submitLoading: false })
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
