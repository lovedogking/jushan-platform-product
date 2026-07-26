import request from '@/utils/request'
import type { BoothMonitorSnapshot, DeviceStatus, MonitorAlert, RecognitionEvent } from './monitor-types'

/** 获取岗亭监控初始化快照 */
export function getSnapshot(parkingLotId: number | string) {
  return request.get<BoothMonitorSnapshot>('/booth/monitor/snapshot', { parkingLotId })
}

/** 刷新设备状态 */
export function refreshDevices(parkingLotId: number | string) {
  return request.post<DeviceStatus[]>('/booth/monitor/devices/refresh', undefined, { params: { parkingLotId } })
}

/** 确认异常提醒 */
export function acknowledgeAlert(alertId: number | string) {
  return request.post<void>(`/booth/monitor/alerts/${alertId}/ack`)
}

// ========== 无牌车临时车牌处理 ==========

/** 获取建议临时车牌号（预览，不消耗序号） */
export function suggestTempPlate(parkingLotId: number | string) {
  return request.get<{ tempPlate: string }>('/booth/temp-plate/suggest', { parkingLotId })
}

/** 岗亭手动无牌车入场 */
export function manualTempPlateEntry(data: { parkingLotId: number; laneId: number; tempPlate?: string }) {
  return request.post<{ recordId: number; tempPlate: string; entryTime: string }>('/booth/temp-plate/entry', data)
}

/** 岗亭手动无牌车出场匹配计费 */
export function manualTempPlateExit(data: { tempPlate: string; parkingLotId: number; laneId: number }) {
  return request.post<{ exitRecordId: number; orderId: number; feeCents: number; message: string }>('/booth/temp-plate/exit-match', data)
}

/** 车牌校正 */
export function correctPlate(logId: number, correctedPlate: string) {
  return request.post<void>(`/booth/recognition/${logId}/correct`, { correctedPlate })
}

// ========== 余位调整 ==========

/** 岗亭端调整剩余车位数（SET 直接设定 / ADJUST 加减调整） */
export function adjustSpaces(data: {
  mode: 'SET' | 'ADJUST'
  value: number
  parkingLotId: number
  reason: string
}) {
  return request.post<void>('/booth/spaces/adjust', data)
}
