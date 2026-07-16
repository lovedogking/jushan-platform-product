import { Client, type IFrame, type IStompSocket, type StompSubscription } from '@stomp/stompjs'
import type { SpaceUpdatePayload, RecognitionEventPayload, AlertPayload, DeviceStatus } from '@/api/monitor-types'

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting'

export interface WebSocketHandlers {
  onConnectionChange?: (status: ConnectionStatus) => void
  onSpaceUpdate?: (payload: SpaceUpdatePayload) => void
  onRecognitionEvent?: (payload: RecognitionEventPayload) => void
  onDeviceStatus?: (payload: DeviceStatus) => void
  onAlert?: (payload: AlertPayload) => void
  onError?: (error: string) => void
}

export interface WebSocketOptions {
  token: string
  parkingLotId: number | string
  handlers: WebSocketHandlers
  /** 重连退避基数（毫秒），默认 1000 */
  reconnectBaseDelay?: number
  /** 最大重连间隔（毫秒），默认 30000 */
  maxReconnectDelay?: number
  /** 连续重连失败多少次后提示操作员，默认 5 */
  reconnectWarningThreshold?: number
}

/**
 * 岗亭监控 WebSocket 客户端封装。
 *
 * 基于 STOMP over WebSocket，支持：
 * - 自动重连（指数退避）
 * - 断线后恢复订阅
 * - 连接状态通知
 */
export class MonitorWebSocketClient {
  private client: Client | null = null
  private options: WebSocketOptions
  private subscriptions: StompSubscription[] = []
  private reconnectAttempts = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private baseDelay: number
  private maxDelay: number
  private warningThreshold: number
  private currentStatus: ConnectionStatus = 'disconnected'

  constructor(options: WebSocketOptions) {
    this.options = options
    this.baseDelay = options.reconnectBaseDelay ?? 1000
    this.maxDelay = options.maxReconnectDelay ?? 30000
    this.warningThreshold = options.reconnectWarningThreshold ?? 5
  }

  connect() {
    if (this.client?.active) {
      return
    }

    this.setStatus('connecting')
    const { token, parkingLotId } = this.options

    this.client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws?token=${encodeURIComponent(token)}`,
      reconnectDelay: 0, // 由本类自行管理重连
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.reconnectAttempts = 0
        this.setStatus('connected')
        this.subscribeAll(parkingLotId)
      },
      onDisconnect: () => {
        this.setStatus('disconnected')
        this.scheduleReconnect()
      },
      onStompError: (frame: IFrame) => {
        const message = frame.headers['message'] || 'STOMP 协议错误'
        this.options.handlers.onError?.(message)
        this.setStatus('disconnected')
        this.scheduleReconnect()
      },
      onWebSocketError: () => {
        this.options.handlers.onError?.('WebSocket 连接异常')
        this.setStatus('disconnected')
        this.scheduleReconnect()
      },
      onWebSocketClose: () => {
        this.setStatus('disconnected')
        this.scheduleReconnect()
      },
    })

    this.client.activate()
  }

  disconnect() {
    this.clearReconnectTimer()
    this.unsubscribeAll()
    if (this.client) {
      this.client.deactivate()
      this.client = null
    }
    this.setStatus('disconnected')
  }

  private subscribeAll(parkingLotId: number | string) {
    this.unsubscribeAll()
    if (!this.client?.connected) {
      return
    }

    const prefix = `/topic/booth/${parkingLotId}`
    const topics = [
      { path: `${prefix}/events`, handler: this.options.handlers.onRecognitionEvent },
      { path: `${prefix}/spaces`, handler: this.options.handlers.onSpaceUpdate },
      { path: `${prefix}/device-status`, handler: this.options.handlers.onDeviceStatus },
      { path: `${prefix}/alerts`, handler: this.options.handlers.onAlert },
    ]

    for (const topic of topics) {
      if (!topic.handler) continue
      const sub = this.client.subscribe(topic.path, (message) => {
        try {
          const payload = JSON.parse(message.body)
          ;(topic.handler as (p: unknown) => void)(payload)
        } catch (e) {
          console.warn('WebSocket 消息解析失败:', message.body, e)
        }
      })
      this.subscriptions.push(sub)
    }
  }

  private unsubscribeAll() {
    for (const sub of this.subscriptions) {
      try {
        sub.unsubscribe()
      } catch (e) {
        // ignore
      }
    }
    this.subscriptions = []
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) {
      return
    }

    this.setStatus('reconnecting')
    this.reconnectAttempts++

    if (this.reconnectAttempts >= this.warningThreshold) {
      this.options.handlers.onError?.(`网络连接异常，正在尝试第 ${this.reconnectAttempts} 次重连，请检查网络`)
    }

    const delay = Math.min(this.baseDelay * Math.pow(2, this.reconnectAttempts - 1), this.maxDelay)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private setStatus(status: ConnectionStatus) {
    if (this.currentStatus === status) {
      return
    }
    this.currentStatus = status
    this.options.handlers.onConnectionChange?.(status)
  }

  get status(): ConnectionStatus {
    return this.currentStatus
  }
}
