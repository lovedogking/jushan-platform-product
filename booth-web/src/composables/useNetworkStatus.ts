/**
 * 网络状态追踪 composable。
 *
 * 提供：
 * - online: 浏览器是否在线
 * - syncStatus: 同步状态（idle / syncing）
 * - pendingCount: 离线队列待处理数量
 *
 * 网络恢复时自动触发离线队列同步。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */

import { ref, onMounted, onUnmounted, readonly } from 'vue'
import { offlineQueue, executeOfflineOp, type QueueChangeCallback } from '@/utils/offline-queue'

export type SyncStatus = 'idle' | 'syncing'

/** 全局单例状态，跨组件共享 */
const online = ref(navigator.onLine)
const syncStatus = ref<SyncStatus>('idle')
const pendingCount = ref(0)

let initialized = false
let removeListener: (() => void) | null = null
let onlineHandler: (() => void) | null = null
let offlineHandler: (() => void) | null = null

/**
 * 初始化离线队列和网络监听。
 * 多次调用安全——仅首次执行初始化。
 */
async function ensureInitialized(): Promise<void> {
  if (initialized) return
  initialized = true

  try {
    await offlineQueue.init()
    pendingCount.value = await offlineQueue.getPendingCount()
  } catch (e) {
    console.warn('离线队列初始化失败:', e)
  }

  // 监听队列变更
  removeListener = offlineQueue.onChange((count: number) => {
    pendingCount.value = count
  })

  // 监听网络状态变化
  onlineHandler = async () => {
    online.value = true
    // 网络恢复后自动同步
    if (pendingCount.value > 0) {
      await syncPendingOps()
    }
  }

  offlineHandler = () => {
    online.value = false
  }

  window.addEventListener('online', onlineHandler)
  window.addEventListener('offline', offlineHandler)
}

/**
 * 同步离线队列中的待处理操作。
 */
async function syncPendingOps(): Promise<{ synced: number; failed: number }> {
  if (syncStatus.value === 'syncing') return { synced: 0, failed: 0 }
  syncStatus.value = 'syncing'

  try {
    const result = await offlineQueue.syncAll(executeOfflineOp)
    pendingCount.value = await offlineQueue.getPendingCount()
    return result
  } finally {
    syncStatus.value = 'idle'
  }
}

/**
 * 网络状态 composable。
 * 在组件的 setup 中调用以确保生命周期绑定正确。
 */
export function useNetworkStatus() {
  onMounted(() => {
    ensureInitialized()
  })

  // onUnmounted 不做清理——状态是全局的，组件卸载不应停止监听
  // 如需清理，调用 destroyNetworkStatus()

  return {
    online: readonly(online),
    syncStatus: readonly(syncStatus),
    pendingCount: readonly(pendingCount),
    syncPendingOps,
  }
}

/**
 * 销毁全局网络监听（仅在应用完全卸载时调用）。
 */
export function destroyNetworkStatus(): void {
  if (onlineHandler) window.removeEventListener('online', onlineHandler)
  if (offlineHandler) window.removeEventListener('offline', offlineHandler)
  if (removeListener) removeListener()
  initialized = false
}
