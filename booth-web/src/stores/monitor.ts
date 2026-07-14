import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import {
  getSnapshot,
  refreshDevices,
  acknowledgeAlert,
} from '@/api/monitor'
import type {
  BoothMonitorSnapshot,
  DeviceStatus,
  Lane,
  MonitorAlert,
  ParkingLotSummary,
  RecognitionEvent,
  SpaceUpdatePayload,
  RecognitionEventPayload,
  AlertPayload,
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
  const loading = ref(false)
  const error = ref('')

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

  async function ackAlert(alertId: number) {
    await acknowledgeAlert(alertId)
    alerts.value = alerts.value.filter((a) => a.id !== alertId)
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

  function reset() {
    currentLotId.value = null
    connectionStatus.value = 'disconnected'
    parkingLot.value = null
    lanes.value = []
    recentEvents.value = []
    deviceStatuses.value = []
    alerts.value = []
    error.value = ''
  }

  return {
    currentLotId,
    connectionStatus,
    parkingLot,
    lanes,
    recentEvents,
    deviceStatuses,
    alerts,
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
    ackAlert,
    refreshAllDevices,
    formatTime,
    reset,
  }
})
