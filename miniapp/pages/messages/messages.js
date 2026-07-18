/**
 * 消息中心 — Phase 3 E3
 * 支付成功通知等消息列表
 */
const { get, put } = require('../../utils/request')

function formatTimeText(iso) {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    if (isNaN(d.getTime())) return iso
    const pad = n => n < 10 ? '0' + n : '' + n
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch (e) {
    return iso
  }
}

function enrichMessage(m) {
  return { ...m, timeText: formatTimeText(m.createdAt) }
}

Page({
  data: {
    loading: true,
    messages: [],
    total: 0,
    current: 1,
    pageSize: 20,
    hasMore: true,
    refreshing: false,
    unreadCount: 0,
  },

  onLoad() {
    this.loadMessages()
  },

  onShow() {
    this.fetchUnreadCount()
  },

  onPullDownRefresh() {
    this.refreshData().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadMore()
    }
  },

  /** 刷新数据 */
  async refreshData() {
    this.setData({ current: 1, refreshing: true })
    await this.loadMessages()
    this.fetchUnreadCount()
    this.setData({ refreshing: false })
  },

  /** 加载消息列表 */
  async loadMessages() {
    this.setData({ loading: true })
    try {
      const res = await get('/api/v1/mini/messages', {
        current: this.data.current,
        size: this.data.pageSize,
      })
      const records = ((res && res.records) || []).map(enrichMessage)
      this.setData({
        messages: records,
        total: (res && res.total) || 0,
        hasMore: records.length >= this.data.pageSize,
        loading: false,
      })
    } catch (err) {
      this.setData({ loading: false })
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    }
  },

  /** 加载更多 */
  async loadMore() {
    const nextPage = this.data.current + 1
    this.setData({ loading: true })
    try {
      const res = await get('/api/v1/mini/messages', {
        current: nextPage,
        size: this.data.pageSize,
      })
      const records = ((res && res.records) || []).map(enrichMessage)
      this.setData({
        messages: this.data.messages.concat(records),
        current: nextPage,
        total: (res && res.total) || 0,
        hasMore: records.length >= this.data.pageSize,
        loading: false,
      })
    } catch (err) {
      this.setData({ loading: false })
    }
  },

  /** 获取未读数量 */
  async fetchUnreadCount() {
    try {
      const res = await get('/api/v1/mini/messages/unread-count')
      this.setData({ unreadCount: (res && res.unreadCount) || 0 })
    } catch {
      // 忽略
    }
  },

  /** 点击消息 — 标记已读并查看详情 */
  async onMessageTap(e) {
    const msg = e.currentTarget.dataset.message
    if (!msg) return

    // 标记已读（如果未读）
    if (!msg.isRead) {
      try {
        await put(`/api/v1/mini/messages/${msg.id}/read`)
        // 更新本地状态
        const messages = this.data.messages.map(m => {
          if (m.id === msg.id) {
            return { ...m, isRead: true }
          }
          return m
        })
        this.setData({ messages, unreadCount: Math.max(0, this.data.unreadCount - 1) })
      } catch {
        // 忽略标记失败
      }
    }

    // 查看消息详情
    this.showMessageDetail(msg)
  },

  /** 显示消息详情 */
  showMessageDetail(msg) {
    const typeText = msg.type === 'PAY_SUCCESS' ? '支付成功' : msg.type || '系统通知'
    const timeText = this.formatTime(msg.createdAt)
    const content = msg.content || '无详细内容'
    const amountText = msg.relatedAmountYuan ? '¥' + msg.relatedAmountYuan : ''

    wx.showModal({
      title: msg.title || typeText,
      content: `${content}\n\n${msg.relatedPlate ? '车牌：' + msg.relatedPlate + '\n' : ''}${amountText ? '金额：' + amountText + '\n' : ''}时间：${timeText}`,
      showCancel: false,
      confirmText: '知道了',
    })
  },

  /** 跳转支付记录 */
  goToPay(e) {
    const orderId = e.currentTarget.dataset.orderId
    if (orderId) {
      wx.navigateTo({ url: `/pages/pay/pay?recordId=${orderId}` })
    }
  },

  /** 格式化时间 */
  formatTime(iso) {
    if (!iso) return ''
    try {
      const d = new Date(iso)
      if (isNaN(d.getTime())) return iso
      const pad = n => n < 10 ? '0' + n : '' + n
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    } catch {
      return iso
    }
  },
})
