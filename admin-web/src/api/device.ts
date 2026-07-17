import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 设备视图 */
export interface DeviceVO {
  id: number
  parkingLotId: number
  laneId: number | null
  executorDeviceId: number | null
  vendorId: number
  vendorName: string
  modelId: number
  modelName: string
  name: string
  code: string
  deviceSn: string
  deviceType: string
  recognitionDirection: number | null
  cameraRole: number | null
  status: string
  capabilities: string
  description: string
  createdAt: string
  updatedAt: string
}

/** 设备状态快照 */
export interface DeviceStatusVO {
  deviceId: number
  deviceName: string
  deviceCode: string
  deviceType: string
  deviceStatus: string
  online: boolean | null
  lastOnlineTime: string | null
  gateStatus: string | null
  gateConnectStatus: string | null
  statusDescription: string | null
  collectedAt: string | null
  snapshotAgeSeconds: number | null
  stale: boolean | null
  lastQuerySuccess: boolean | null
  lastErrorCode: string | null
  lastErrorMessage: string | null
}

/** 校时结果 */
export interface TimeSyncResult {
  success: boolean
  deviceCode: number
  message: string
}

/** 命令执行结果（开闸/关闸） */
export interface CommandResult {
  success: boolean
  deviceCode: number
  message: string
}

/** 显示屏操作结果 */
export interface DisplayResult {
  success: boolean
  message: string
}

/** 语音播报结果 */
export interface VoiceResult {
  success: boolean
  message: string
}

/** 显示屏文字请求 */
export interface DisplayTextRequest {
  content: string
  direction: string
  fontSize: number
  color: string
}

/** 显示屏配置请求 */
export interface DisplayConfigRequest {
  configType: string
  intValue: number
  stringValue?: string
}

/** 语音控制请求 */
export interface VoiceControlRequest {
  action: string
  voiceId: number
  variable?: string
}

/** 厂商 */
export interface DeviceVendor {
  id: number
  name: string
  code: string
}

/** 型号 */
export interface DeviceModel {
  id: number
  vendorId: number
  name: string
  code: string
  deviceType: string
}

/** 分页查询设备列表 */
export function getDevices(params: {
  page: number
  size: number
  parkingLotId?: number
  status?: string
  deviceType?: string
}) {
  return request.get<PageResult<DeviceVO>>('/admin/devices', { params })
}

/** 查询设备详情 */
export function getDevice(id: number) {
  return request.get<DeviceVO>(`/admin/devices/${id}`)
}

/** 创建设备 */
export function createDevice(data: {
  parkingLotId: number
  vendorId: number
  modelId: number
  name: string
  code: string
  deviceSn: string
  deviceType: string
  recognitionDirection?: number
  cameraRole?: number
  capabilities?: string
  description?: string
}) {
  return request.post<DeviceVO>('/admin/devices', data)
}

/** 更新设备 */
export function updateDevice(id: number, data: Record<string, any>) {
  return request.put<DeviceVO>(`/admin/devices/${id}`, data)
}

/** 启用/停用设备 */
export function updateDeviceStatus(id: number, action: string) {
  return request.post(`/admin/devices/${id}/status`, { action })
}

/** 获取厂商列表 */
export function getVendors() {
  return request.get<DeviceVendor[]>('/admin/devices/vendors')
}

/** 获取型号列表 */
export function getModels(vendorId?: number) {
  return request.get<DeviceModel[]>('/admin/devices/models', { params: vendorId ? { vendorId } : {} })
}

/** 绑定车道 */
export function bindLane(deviceId: number, laneId: number) {
  return request.post<DeviceVO>(`/admin/devices/${deviceId}/bind-lane`, { laneId })
}

/** 解绑车道 */
export function unbindLane(deviceId: number) {
  return request.delete<DeviceVO>(`/admin/devices/${deviceId}/bind-lane`)
}

/** 设置道闸执行相机 */
export function setExecutor(deviceId: number, executorDeviceId: number) {
  return request.post<DeviceVO>(`/admin/devices/${deviceId}/executor`, { executorDeviceId })
}

/** 设备校时 */
export function syncDeviceTime(id: number, reason: string) {
  return request.post<TimeSyncResult>(`/admin/devices/${id}/sync-time`, { reason })
}

/** 查询设备实时状态 */
export function queryDeviceStatus(id: number) {
  return request.post<DeviceStatusVO>(`/admin/devices/${id}/query-status`)
}

/** 批量查询设备实时状态 */
export function queryDeviceStatusBatch(deviceIds: number[]) {
  return request.post<DeviceStatusVO[]>('/admin/devices/query-status-batch', { deviceIds })
}

/** 获取设备最新状态快照 */
export function getDeviceStatusSnapshot(id: number) {
  return request.get<DeviceStatusVO>(`/admin/devices/${id}/status`)
}

/** 批量获取设备状态快照 */
export function getDeviceStatusSnapshots(deviceIds: number[]) {
  return request.post<DeviceStatusVO[]>('/admin/devices/status-snapshots', { deviceIds })
}

// ==================== 设备控制（v0.4） ====================

/** 开闸 */
export function openGate(deviceId: number, reason?: string) {
  return request.post<CommandResult>(`/admin/devices/${deviceId}/open-gate`, { reason: reason || '' })
}

/** 关闸 */
export function closeGate(deviceId: number, reason?: string) {
  return request.post<CommandResult>(`/admin/devices/${deviceId}/close-gate`, { reason: reason || '' })
}

/** 显示屏实时文字 */
export function displayText(deviceId: number, data: DisplayTextRequest) {
  return request.post<DisplayResult>(`/admin/devices/${deviceId}/display-text`, data)
}

/** 显示屏配置 */
export function displayConfig(deviceId: number, data: DisplayConfigRequest) {
  return request.post<DisplayResult>(`/admin/devices/${deviceId}/display-config`, data)
}

/** 语音播报 */
export function voiceControl(deviceId: number, data: VoiceControlRequest) {
  return request.post<VoiceResult>(`/admin/devices/${deviceId}/voice-control`, data)
}
