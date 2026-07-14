/** 岗亭监控快照 */
export interface BoothMonitorSnapshot {
  parkingLot: ParkingLotSummary
  lanes: Lane[]
  recentEvents: RecognitionEvent[]
  deviceStatuses: DeviceStatus[]
  alerts: MonitorAlert[]
}

/** 车道 */
export interface Lane {
  id: number
  parkingLotId: number
  name: string
  code: string
  direction: 'ENTRY' | 'EXIT' | 'MIXED'
  status: string
  deviceId?: number
  deviceName?: string
}

/** 停车场摘要 */
export interface ParkingLotSummary {
  id: number
  tenantId: number
  name: string
  totalSpaces: number
  currentVehicles: number
  remainingSpaces: number
  status: string
}

/** 识别事件 */
export interface RecognitionEvent {
  eventId: string
  logId: number
  plateNumber: string
  standardizedPlate: string
  direction: 'ENTRY' | 'EXIT'
  eventTime: string
  confidence: number
  source: string
  status: string
  laneId: number
  laneName: string
  deviceId: number
  deviceName: string
  imagePath: string
  plateImagePath: string
}

/** 设备状态 */
export interface DeviceStatus {
  deviceId: number
  deviceName: string
  deviceCode: string
  deviceType: string
  deviceStatus: string
  online: boolean
  lastOnlineTime: string
  gateStatus: string
  gateConnectStatus: string
  statusDescription: string
  collectedAt: string
  snapshotAgeSeconds: number
  stale: boolean
  lastQuerySuccess: boolean
  lastErrorCode: string
  lastErrorMessage: string
}

/** 异常提醒 */
export interface MonitorAlert {
  id: number
  alertType: string
  severity: 'WARNING' | 'CRITICAL'
  sourceId: string
  message: string
  createdAt: string
}

/** WebSocket 车位更新 */
export interface SpaceUpdatePayload {
  remainingSpaces: number
  currentVehicles: number
  totalSpaces: number
  updatedAt: string
}

/** WebSocket 识别事件推送 */
export interface RecognitionEventPayload {
  eventId: string
  logId: number
  plateNumber: string
  standardizedPlate: string
  direction: 'ENTRY' | 'EXIT'
  eventTime: string
  confidence: number
  source: string
  status: string
  laneId: number
  deviceId: number
  imagePath: string
  plateImagePath: string
}

/** WebSocket 异常提醒推送 */
export interface AlertPayload {
  id: number
  alertType: string
  severity: 'WARNING' | 'CRITICAL'
  sourceId: string
  message: string
  createdAt: string
}
