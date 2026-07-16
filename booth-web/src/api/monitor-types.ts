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
  /** 支付状态：PAID-已支付, UNPAID-待支付 */
  paymentStatus?: 'PAID' | 'UNPAID'
  /** 应收费用（元） */
  feeAmount?: number
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

// ========== 收费与开闸相关类型 ==========

/** 在场车辆记录（收费面板使用的后端返回） */
export interface ParkingSessionVO {
  id: number
  parkingLotId: number
  laneId: number
  plateNumber: string
  plateColor?: string
  vehicleType: string
  entryTime: string
  entryImage?: string
  exitTime?: string
  exitLaneId?: number
  status: string
  feeAmount: number
  feeCents: number
  paidAmount: number
  durationMinutes: number
  createdAt: string
  updatedAt: string
}

/** 收费面板展示信息 */
export interface ChargeInfo {
  sessionId: number
  plateNumber: string
  plateColor: string
  vehicleType: string
  entryTime: string
  durationMinutes: number
  feeAmount: number
  /** 费用（分），精度安全 */
  feeCents: number
  laneId: number
}

/** 收费提交请求 */
export interface ChargeRequest {
  sessionId: number
  exitLaneId: number
  feeAmount: number
  paidAmount: number
  paymentMethod: 'CASH' | 'WECHAT' | 'ALIPAY' | 'FREE'
  authCode?: string
  remark?: string
}

/** 支付方式 */
export type PaymentMethod = 'CASH' | 'WECHAT' | 'ALIPAY' | 'FREE'

/** 收费规则片段（岗亭端临时调整用） */
export interface FeeRuleSegment {
  id?: number
  startTime: string
  endTime: string
  price: number
  unitMinutes: number
  cap?: number
}

/** 收费规则（岗亭端查看/调整用） */
export interface FeeRule {
  id: number
  name: string
  lotId: number
  zoneId?: number
  billingMode: number
  freeMinutes: number
  unitMinutes: number
  firstPeriodPrice: number
  subsequentPrice: number
  dailyCap?: number
  nightCap?: number
  priority: number
  status: number
  effectiveStart?: string
  effectiveEnd?: string
  timeSegments: FeeRuleSegment[]
}

/** 开闸结果（对应后端 RecognitionResultVO） */
export interface GateOpenResult {
  eventId?: number
  plateNumber?: string
  vehicleType?: string
  allowPass?: boolean
  gateCommandSent: boolean
  gateDeviceAck: boolean
  /** 闸杆实际是否抬起。一期始终为 null，二期设备状态反馈后填充。 */
  gateOpened: boolean | null
  gateResult?: string
  feeAmount?: number
  sessionId?: number
  resultMessage?: string
  exception?: boolean
  exceptionType?: string
}

/** 人工放行原因 */
export type ReleaseReason = 'DEVICE_FAULT' | 'VIP_VEHICLE' | 'EMERGENCY' | 'OTHER'

/** 人工放行原因选项 */
export const RELEASE_REASON_OPTIONS: { value: ReleaseReason; label: string }[] = [
  { value: 'DEVICE_FAULT', label: '设备故障' },
  { value: 'VIP_VEHICLE', label: 'VIP车辆' },
  { value: 'EMERGENCY', label: '紧急车辆' },
  { value: 'OTHER', label: '其他' },
]
