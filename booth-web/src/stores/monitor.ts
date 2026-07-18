import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import {
  getSnapshot,
  refreshDevices,
  acknowledgeAlert,
} from '@/api/monitor'
import { getChargeInfo } from '@/api/charge'
import type {
  BoothMonitorSnapshot,
  ChargeInfo,
  DeviceStatus,
  Lane,
  MonitorAlert,
  ParkingLotSummary,
  RecognitionEvent,
  SpaceUpdatePayload,
  RecognitionEventPayload,
  AlertPayload,
  RemoteGateAlertPayload,
} from '@/api/monitor-types'

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting'

export const useMonitorStore = defineStore('monitor', () => {
  // ========== state ==========
  const currentLotId = ref<number | null>(null)
  const connectionStatus = ref<ConnectionStatus>('disconnected')
  const parkingLot = ref<ParkingLotSummary | null>(null)
  const lanes = ref<Lane[]>([])
  const recentEvents = ref<RecognitionEvent[]>([])
  const deviceStatuses = ref<DeviceStatus[]>([])
  const alerts = ref<MonitorAlert[]>([])
  /** 远程开闸历史通知（Phase 1 B2） */
  const remoteGateAlerts = ref<RemoteGateAlertPayload[]>([])
  const loading = ref(false)
  const error = ref('')

  // ========== 收费面板状态 ==========
  /** 收费面板是否显示 */
  const chargePanelVisible = ref(false)
  /** 当前收费信息 */
  const currentChargeInfo = ref<ChargeInfo | null>(null)
  /** 收费操作加载中 */
  const chargeLoading = ref(false)
  /** 开闸结果 */
  const chargeResult = ref<{
    success: boolean
    message: string
    gateOpened: boolean | null
  } | null>(null)

  // ========== 交接班状态 ==========
  const currentShift = ref<any>(null)
  const shiftHistory = ref<any[]>([])
  const shiftLoading = ref(false)

  // ========== getters ==========
  const criticalAlerts = computed(() => alerts.value.filter((a) => a.severity === 'CRITICAL'))
  const hasLot = computed(() => parkingLot.value !== null)
  const isConnected = computed(() => connectionStatus.value === 'connected')

  // ========== actions ==========
  async function loadSnapshot(parkingLotId: number) {
    loading.value = true
    error.value = ''
    try {
      const snapshot = await getSnapshot(parkingLotId)
      currentLotId.value = parkingLotId
      parkingLot.value = snapshot.parkingLot
      lanes.value = snapshot.lanes || []
      recentEvents.value = snapshot.recentEvents || []
      deviceStatuses.value = snapshot.deviceStatuses || []
      alerts.value = snapshot.alerts || []
    } catch (e: any) {
      error.value = e?.message || '加载监控快照失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function setConnectionStatus(status: ConnectionStatus) {
    connectionStatus.value = status
  }

  function handleSpaceUpdate(payload: SpaceUpdatePayload) {
    if (!parkingLot.value) return
    parkingLot.value.remainingSpaces = payload.remainingSpaces
    parkingLot.value.currentVehicles = payload.currentVehicles
    if (payload.totalSpaces != null) {
      parkingLot.value.totalSpaces = payload.totalSpaces
    }
  }

  function handleRecognitionEvent(payload: RecognitionEventPayload) {
    const lane = lanes.value.find((l) => l.id === payload.laneId)
    const event: RecognitionEvent = {
      ...payload,
      laneName: lane?.name || '',
      deviceName: findDeviceName(payload.deviceId),
    }
    recentEvents.value.unshift(event)
    if (recentEvents.value.length > 50) {
      recentEvents.value = recentEvents.value.slice(0, 50)
    }
  }

  function handleDeviceStatus(payload: DeviceStatus) {
    const index = deviceStatuses.value.findIndex((d) => d.deviceId === payload.deviceId)
    if (index >= 0) {
      deviceStatuses.value[index] = payload
    } else {
      deviceStatuses.value.push(payload)
    }
  }

  function handleAlert(payload: AlertPayload) {
    if (!alerts.value.some((a) => a.id === payload.id)) {
      alerts.value.unshift(payload)
    }
  }

  function handleRemoteGateAlert(payload: RemoteGateAlertPayload) {
    // 去重：相同操作人+时间不重复添加
    const exists = remoteGateAlerts.value.some(
      (a) => a.operatorName === payload.operatorName && a.operationTime === payload.operationTime
    )
    if (exists) return
    remoteGateAlerts.value.unshift(payload)
    // 最多保留 20 条历史
    if (remoteGateAlerts.value.length > 20) {
      remoteGateAlerts.value = remoteGateAlerts.value.slice(0, 20)
    }
  }

  async function ackAlert(alertId: number) {
    try {
      const result = await acknowledgeAlert(alertId)
      // 如果被离线队列拦截（返回 { queued: true }），仍然从列表中移除
      alerts.value = alerts.value.filter((a) => a.id !== alertId)
      return result
    } catch (e: any) {
      // 网络错误或离线队列不可用时保留告警
      throw e
    }
  }

  async function refreshAllDevices() {
    if (!currentLotId.value) return
    deviceStatuses.value = await refreshDevices(currentLotId.value)
  }

  function findDeviceName(deviceId?: number) {
    const device = deviceStatuses.value.find((d) => d.deviceId === deviceId)
    return device?.deviceName || ''
  }

  function formatTime(time?: string) {
    if (!time) return '-'
    return dayjs(time).format('HH:mm:ss')
  }

  // ========== 收费面板操作 ==========

  /**
   * 根据车牌号查询收费信息并显示收费面板。
   * 用于 WebSocket EXIT 事件触发或手动查询。
   */
  async function showChargePanel(plateNumber: string, laneId: number) {
    chargeLoading.value = true
    chargeResult.value = null
    try {
      const session = await getChargeInfo(plateNumber)
      if (!session) {
        throw new Error('未找到在场记录')
      }
      const info: ChargeInfo = {
        sessionId: session.id,
        plateNumber: session.plateNumber,
        plateColor: session.plateColor || '',
        vehicleType: session.vehicleType || '临时车',
        entryTime: session.entryTime,
        durationMinutes: session.durationMinutes || 0,
        feeAmount: session.feeAmount || 0,
        feeCents: session.feeCents || 0,
        laneId,
      }
      currentChargeInfo.value = info
      chargePanelVisible.value = true
    } catch (e: any) {
      // 查询失败时仍然打开面板，但显示错误状态
      currentChargeInfo.value = {
        sessionId: 0,
        plateNumber,
        plateColor: '',
        vehicleType: '未知',
        entryTime: '',
        durationMinutes: 0,
        feeAmount: 0,
        feeCents: 0,
        laneId,
      }
      chargePanelVisible.value = true
      throw e
    } finally {
      chargeLoading.value = false
    }
  }

  /** 关闭收费面板 */
  function hideChargePanel() {
    chargePanelVisible.value = false
    currentChargeInfo.value = null
    chargeResult.value = null
  }

  /** 设置收费/开闸结果 */
  function setChargeResult(result: { success: boolean; message: string; gateOpened: boolean | null }) {
    chargeResult.value = result
  }

  // ========== 交接班操作 ==========

  async function loadCurrentShift() {
    try {
      const { getCurrentShift } = await import('@/api/shift')
      currentShift.value = await getCurrentShift()
    } catch {
      currentShift.value = null
    }
  }

  async function loadShiftHistory(parkingLotId: number) {
    shiftLoading.value = true
    try {
      const { getShiftHistory } = await import('@/api/shift')
      const result = await getShiftHistory({ parkingLotId, current: 1, size: 20 })
      shiftHistory.value = result.records || []
    } catch {
      shiftHistory.value = []
    } finally {
      shiftLoading.value = false
    }
  }

  function reset() {
    currentLotId.value = null
    connectionStatus.value = 'disconnected'
    parkingLot.value = null
    lanes.value = []
    recentEvents.value = []
    deviceStatuses.value = []
    alerts.value = []
    remoteGateAlerts.value = []
    error.value = ''
    chargePanelVisible.value = false
    currentChargeInfo.value = null
    chargeLoading.value = false
    chargeResult.value = null
    currentShift.value = null
    shiftHistory.value = []
    shiftLoading.value = false
  }

  return {
    currentLotId,
    connectionStatus,
    parkingLot,
    lanes,
    recentEvents,
    deviceStatuses,
    alerts,
    remoteGateAlerts,
    loading,
    error,
    criticalAlerts,
    hasLot,
    isConnected,
    loadSnapshot,
    setConnectionStatus,
    handleSpaceUpdate,
    handleRecognitionEvent,
    handleDeviceStatus,
    handleAlert,
    handleRemoteGateAlert,
    ackAlert,
    refreshAllDevices,
    formatTime,
    chargePanelVisible,
    currentChargeInfo,
    chargeLoading,
    chargeResult,
    showChargePanel,
    hideChargePanel,
    setChargeResult,
    currentShift,
    shiftHistory,
    shiftLoading,
    loadCurrentShift,
    loadShiftHistory,
    reset,
  }
})
