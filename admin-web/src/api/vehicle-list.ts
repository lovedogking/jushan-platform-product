import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 黑白名单视图（对应后端 VehicleListVO） */
export interface VehicleListRecord {
  id: number
  plateNumber: string
  listType: string
  listTypeLabel: string
  parkingLotId: number
  parkingLotName: string
  startDate?: string
  endDate?: string
  triggerType?: string
  triggerTypeLabel?: string
  status: string
  statusLabel: string
  remark?: string
  createdAt?: string
}

/** 名单类型映射 */
export const LIST_TYPE_MAP: Record<string, string> = {
  BLACK: '黑名单',
  WHITE: '白名单',
}

/** 名单类型选项 */
export const LIST_TYPE_OPTIONS = [
  { label: '黑名单', value: 'BLACK' },
  { label: '白名单', value: 'WHITE' },
]

/** 黑名单触发类型映射 */
export const TRIGGER_TYPE_MAP: Record<string, string> = {
  ARREARS: '欠费类',
  MANAGEMENT: '管理类',
  OTHER: '其他类',
}

/** 黑名单触发行为映射 */
export const TRIGGER_ACTION_MAP: Record<string, string> = {
  ARREARS: '禁止入场',
  MANAGEMENT: '允许入场但告警',
  OTHER: '禁止入场',
}

/** 状态映射 */
export const LIST_STATUS_MAP: Record<string, string> = {
  ACTIVE: '生效中',
  EXPIRED: '已过期',
  DISABLED: '已禁用',
}

/** 分页查询 */
export function getVehicleListPage(params: {
  page?: number
  size?: number
  parkingLotId?: number
  listType?: string
  plateNumber?: string
}) {
  return request.get<PageResult<VehicleListRecord>>('/v1/admin/vehicle-list/page', params)
}

/** 详情 */
export function getVehicleListDetail(id: number) {
  return request.get<VehicleListRecord>(`/v1/admin/vehicle-list/${id}`)
}

/** 创建 */
export function createVehicleList(data: {
  plateNumber: string
  listType: string
  parkingLotId: number
  startDate?: string
  endDate?: string
  triggerType?: string
  remark?: string
}) {
  return request.post<VehicleListRecord>('/v1/admin/vehicle-list', data)
}

/** 编辑 */
export function updateVehicleList(id: number, data: {
  plateNumber: string
  listType: string
  parkingLotId?: number
  startDate?: string
  endDate?: string
  triggerType?: string
  remark?: string
}) {
  return request.put<VehicleListRecord>(`/v1/admin/vehicle-list/${id}`, data)
}

/** 删除 */
export function deleteVehicleList(id: number) {
  return request.delete(`/v1/admin/vehicle-list/${id}`)
}

/** 黑名单触发类型字典 */
export function getTriggerTypes() {
  return request.get<{ code: string; label: string }[]>('/v1/admin/vehicle-list/trigger-types')
}
