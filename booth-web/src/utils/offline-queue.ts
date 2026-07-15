/**
 * 离线操作队列。
 *
 * 当网络中断时，人工放行、异常处理等操作暂存到 IndexedDB；
 * 网络恢复后自动批量提交，提交失败的记录到"待处理"列表。
 *
 * 离线数据最多保留 24 小时，超时自动清理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */

const DB_NAME = 'booth-offline-queue'
const DB_VERSION = 1
const STORE_NAME = 'pending-ops'
const MAX_AGE_MS = 24 * 60 * 60 * 1000 // 24 小时

/** 离线操作类型 */
export type OfflineOpType = 'MANUAL_RELEASE' | 'EXCEPTION_HANDLE' | 'ACK_ALERT'

/** 离线操作状态 */
export type OfflineOpStatus = 'pending' | 'syncing' | 'synced' | 'failed'

/** 单条离线操作 */
export interface OfflineOp {
  id?: number
  type: OfflineOpType
  /** API 路径，如 /booth/monitor/alerts/123/ack */
  url: string
  /** HTTP 方法 */
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** 请求体（JSON 字符串） */
  body?: string
  /** 请求参数（JSON 字符串） */
  params?: string
  /** 操作描述（用于 UI 展示） */
  label: string
  status: OfflineOpStatus
  /** 创建时间戳 */
  createdAt: number
  /** 最后一次同步尝试时间戳 */
  lastSyncAt?: number
  /** 失败原因 */
  errorMessage?: string
}

/** 队列变更回调 */
export type QueueChangeCallback = (pendingCount: number) => void

/**
 * 打开 IndexedDB 数据库。
 */
function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, {
          keyPath: 'id',
          autoIncrement: true,
        })
        store.createIndex('status', 'status', { unique: false })
        store.createIndex('createdAt', 'createdAt', { unique: false })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

/**
 * 离线操作队列管理器。
 */
export class OfflineQueue {
  private db: IDBDatabase | null = null
  private listeners: Set<QueueChangeCallback> = new Set()
  private syncInProgress = false

  /**
   * 初始化数据库连接，清理过期数据。
   */
  async init(): Promise<void> {
    this.db = await openDB()
    await this.cleanExpired()
  }

  /**
   * 添加一条离线操作到队列。
   */
  async enqueue(op: Omit<OfflineOp, 'id' | 'status' | 'createdAt'>): Promise<number> {
    if (!this.db) await this.init()

    const record: OfflineOp = {
      ...op,
      status: 'pending',
      createdAt: Date.now(),
    }

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.add(record)
      request.onsuccess = () => {
        resolve(request.result as number)
        this.notifyListeners()
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 获取所有待同步的操作（pending + failed）。
   */
  async getPendingOps(): Promise<OfflineOp[]> {
    if (!this.db) await this.init()

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('status')
      const range = IDBKeyRange.only('pending')

      const results: OfflineOp[] = []
      const request = index.openCursor(range)
      request.onsuccess = () => {
        const cursor = request.result
        if (cursor) {
          results.push(cursor.value)
          cursor.continue()
        } else {
          resolve(results)
        }
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 获取待处理数量。
   */
  async getPendingCount(): Promise<number> {
    if (!this.db) await this.init()

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('status')
      const request = index.count(IDBKeyRange.only('pending'))
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 获取所有操作（用于调试）。
   */
  async getAllOps(): Promise<OfflineOp[]> {
    if (!this.db) await this.init()

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.getAll()
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 更新操作状态。
   */
  async updateOpStatus(
    id: number,
    status: OfflineOpStatus,
    errorMessage?: string,
  ): Promise<void> {
    if (!this.db) await this.init()

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const getRequest = store.get(id)
      getRequest.onsuccess = () => {
        const record = getRequest.result
        if (!record) {
          resolve()
          return
        }
        record.status = status
        record.lastSyncAt = Date.now()
        if (errorMessage) record.errorMessage = errorMessage
        store.put(record)
        resolve()
      }
      getRequest.onerror = () => reject(getRequest.error)
    })
  }

  /**
   * 删除已完成的操作（同步完成后调用）。
   */
  async removeSynced(): Promise<void> {
    if (!this.db) await this.init()

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('status')
      const range = IDBKeyRange.only('synced')
      const request = index.openCursor(range)
      request.onsuccess = () => {
        const cursor = request.result
        if (cursor) {
          cursor.delete()
          cursor.continue()
        } else {
          resolve()
          this.notifyListeners()
        }
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 清理超过 24 小时的过期数据。
   */
  async cleanExpired(): Promise<void> {
    if (!this.db) await this.init()

    const cutoff = Date.now() - MAX_AGE_MS

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('createdAt')
      // 只清理已同步或失败的过期记录；pending 的记录即使过期也保留
      const range = IDBKeyRange.upperBound(cutoff)
      const request = index.openCursor(range)
      request.onsuccess = () => {
        const cursor = request.result
        if (cursor) {
          const record = cursor.value
          if (record.status === 'synced' || record.status === 'failed') {
            cursor.delete()
          }
          cursor.continue()
        } else {
          resolve()
        }
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 注册队列变更监听器。
   */
  onChange(callback: QueueChangeCallback): () => void {
    this.listeners.add(callback)
    return () => {
      this.listeners.delete(callback)
    }
  }

  private async notifyListeners(): Promise<void> {
    const count = await this.getPendingCount()
    this.listeners.forEach((cb) => cb(count))
  }

  /**
   * 批量同步所有 pending 操作到后端。
   * 返回同步结果：{ synced: number; failed: number }
   */
  async syncAll(requestFn: (op: OfflineOp) => Promise<Response>): Promise<{ synced: number; failed: number }> {
    if (this.syncInProgress) return { synced: 0, failed: 0 }
    this.syncInProgress = true

    let synced = 0
    let failed = 0

    try {
      const pendingOps = await this.getPendingOps()
      if (pendingOps.length === 0) {
        return { synced: 0, failed: 0 }
      }

      for (const op of pendingOps) {
        if (!op.id) continue
        try {
          // 标记为同步中
          await this.updateOpStatus(op.id, 'syncing')

          const response = await requestFn(op)

          if (response.ok) {
            await this.updateOpStatus(op.id, 'synced')
            synced++
          } else {
            const errorText = await response.text().catch(() => '未知错误')
            await this.updateOpStatus(op.id, 'failed', `HTTP ${response.status}: ${errorText}`)
            failed++
          }
        } catch (e: any) {
          if (op.id) {
            await this.updateOpStatus(op.id, 'failed', e?.message || '网络请求失败')
          }
          failed++
        }
      }

      // 清理已同步的记录
      await this.removeSynced()
    } finally {
      this.syncInProgress = false
    }

    return { synced, failed }
  }

  get isSyncing(): boolean {
    return this.syncInProgress
  }
}

/** 全局单例 */
export const offlineQueue = new OfflineQueue()

/**
 * 执行离线操作的 HTTP 请求。
 * 使用原生 fetch 避免 axios 拦截器干扰（如 NProgress、token 刷新等）。
 */
export async function executeOfflineOp(op: OfflineOp): Promise<Response> {
  const TOKEN_KEY = 'jushan_access_token'
  const token = localStorage.getItem(TOKEN_KEY)

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  let url = `/api${op.url}`
  if (op.params) {
    const params = JSON.parse(op.params) as Record<string, string>
    const searchParams = new URLSearchParams(params)
    url += `?${searchParams.toString()}`
  }

  return fetch(url, {
    method: op.method,
    headers,
    body: op.body || undefined,
  })
}
