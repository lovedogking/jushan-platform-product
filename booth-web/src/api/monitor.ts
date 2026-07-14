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
