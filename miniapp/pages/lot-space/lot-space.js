/**
 * 余位查询 — Phase 3 E2
 * 查看附近车场/全部车场的实时余位
 */
const { get } = require('../../utils/request')

Page({
  data: {
    loading: true,
    hasLocation: false,
    lots: [],
    refreshing: false,
    lastRefreshTime: '',
    errorMsg: '',
    showAll: false, // true=全部列表, false=附近

    // 定位
    latitude: 0,
    longitude: 0,
    radius: 5000,
    locationAuthDenied: false,

    // 筛选
    searchKeyword: '',
    filteredLots: [],
  },

  onLoad() {
    this.requestLocation()
  },

  onShow() {
    // 如果已有数据且非首次加载，自动刷新
    if (!this.data.loading && this.data.lots.length > 0) {
      this.refreshData()
    }
  },

  onPullDownRefresh() {
    this.refreshData().finally(() => wx.stopPullDownRefresh())
  },

  /** 请求定位权限 */
  requestLocation() {
    wx.getSetting({
      success: (res) => {
        if (res.authSetting['scope.userLocation']) {
          // 已授权，获取位置
          this.getLocationAndLoad()
        } else if (res.authSetting['scope.userLocation'] === false) {
          // 已拒绝定位权限，显示全部车场
          this.setData({ locationAuthDenied: true, showAll: true })
          this.loadAllLots()
        } else {
          // 未授权，申请授权
          wx.authorize({
            scope: 'scope.userLocation',
            success: () => this.getLocationAndLoad(),
            fail: () => {
              this.setData({ locationAuthDenied: true, showAll: true })
              this.loadAllLots()
            },
          })
        }
      },
      fail: () => {
        this.setData({ locationAuthDenied: true, showAll: true })
        this.loadAllLots()
      },
    })
  },

  /** 获取位置后加载附近车场 */
  getLocationAndLoad() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          hasLocation: true,
          showAll: false,
        })
        this.loadNearbyLots()
      },
      fail: () => {
        this.setData({ locationAuthDenied: true, showAll: true })
        this.loadAllLots()
      },
    })
  },

  /** 加载附近车场 */
  async loadNearbyLots() {
    this.setData({ loading: true, errorMsg: '' })
    try {
      const lots = await get('/api/v1/mini/parking-lots/nearby', {
        latitude: this.data.latitude,
        longitude: this.data.longitude,
        radius: this.data.radius,
      })
      this.processLots(lots || [])
    } catch (err) {
      this.setData({
        loading: false,
        errorMsg: err.message || '加载失败',
      })
    }
  },

  /** 加载全部车场 */
  async loadAllLots() {
    this.setData({ loading: true, errorMsg: '' })
    try {
      const lots = await get('/api/v1/mini/parking-lots')
      this.processLots(lots || [])
    } catch (err) {
      this.setData({
        loading: false,
        errorMsg: err.message || '加载失败',
      })
    }
  },

  /** 处理车场列表数据 */
  processLots(lots) {
    const now = new Date()
    const timeStr = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}`
    this.setData({
      lots: lots,
      filteredLots: lots,
      loading: false,
      refreshing: false,
      lastRefreshTime: timeStr,
    })
  },

  /** 刷新数据 */
  async refreshData() {
    this.setData({ refreshing: true })
    if (this.data.showAll || !this.data.hasLocation) {
      await this.loadAllLots()
    } else {
      await this.loadNearbyLots()
    }
  },

  /** 切换查看模式 */
  switchToList() {
    this.setData({ showAll: true })
    if (this.data.lots.length === 0) {
      this.loadAllLots()
    }
  },

  switchToNearby() {
    if (!this.data.hasLocation) {
      this.requestLocation()
      return
    }
    this.setData({ showAll: false })
    if (this.data.lots.length === 0) {
      this.loadNearbyLots()
    }
  },

  /** 搜索车场 */
  onSearchInput(e) {
    const keyword = (e.detail.value || '').trim().toUpperCase()
    this.setData({ searchKeyword: keyword })
    this.filterLots(keyword)
  },

  filterLots(keyword) {
    if (!keyword) {
      this.setData({ filteredLots: this.data.lots })
      return
    }
    const filtered = this.data.lots.filter(lot =>
      (lot.name && lot.name.toUpperCase().includes(keyword)) ||
      (lot.address && lot.address.toUpperCase().includes(keyword))
    )
    this.setData({ filteredLots: filtered })
  },

  /** 获取剩余车位状态文本 */
  getRemainText(remain) {
    if (remain === undefined || remain === null) return '--'
    if (remain <= 0) return '已满'
    if (remain <= 10) return '紧张'
    return String(remain)
  },

  /** 获取剩余车位状态颜色 */
  getRemainClass(remain) {
    if (remain === undefined || remain === null) return 'remain-none'
    if (remain <= 0) return 'remain-full'
    if (remain <= 10) return 'remain-low'
    return 'remain-ok'
  },

  /** 跳转到车场详情/导航 */
  goToLot(e) {
    const lotId = e.currentTarget.dataset.id
    const lat = e.currentTarget.dataset.lat
    const lng = e.currentTarget.dataset.lng
    if (lat && lng) {
      wx.openLocation({
        latitude: Number(lat),
        longitude: Number(lng),
        scale: 18,
      })
    }
  },

  /** 重新请求定位 */
  reAuthLocation() {
    wx.openSetting({
      success: (res) => {
        if (res.authSetting['scope.userLocation']) {
          this.getLocationAndLoad()
        }
      },
    })
  },
})
