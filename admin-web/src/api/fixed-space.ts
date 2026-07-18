import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 固定车位绑定视图 */
export interface FixedSpaceVO {
  id: number
  parkingLotId: number
  parkingLotName?: string
  zoneId?: number
  zoneName?: string
  spaceNo: string
  vehicleId: number
  plateNumber: string
  validStart?: string
  validEnd?: string
  /** 1=生效中, 2=已过期, 3=已注销 */
  status: number
  reviewStatus?: string
  reviewRemark?: string
  payMethod?: string
  paidAmountCents?: number
  remark?: string
  createdAt?: string
}

/** 固定车位绑定请求 */
export interface FixedSpaceCreateRequest {
  parkingLotId: number
  zoneId?: number
  spaceNo: string
  plateNumber: string
  validStart: string
  validEnd: string
  remark?: string
}

/** 固定车位续期请求 */
export interface FixedSpaceRenewRequest {
  newValidEnd: string
  remark?: string
}

/** 状态映射 */
export const FIXED_SPACE_STATUS_MAP: Record<number, string> = {
  1: '生效中',
  2: '已过期',
  3: '已注销',
}

/** 分页查询固定车位绑定列表 */
export function getFixedSpacePage(params: {
  page?: number
  size?: number
  parkingLotId?: number
  zoneId?: number
  spaceNo?: string
  plateNumber?: string
  status?: number
}) {
  return request.get<PageResult<FixedSpaceVO>>('/v1/fixed-spaces', params)
}

/** 到期预警列表 */
export function getExpiringSpaces(params: {
  days?: number
  page?: number
  size?: number
}) {
  return request.get<PageResult<FixedSpaceVO>>('/v1/fixed-spaces/expiring', params)
}

/** 绑定固定车位 */
export function createFixedSpace(data: FixedSpaceCreateRequest) {
  return request.post<FixedSpaceVO>('/v1/fixed-spaces', data)
}

/** 固定车位续期 */
export function renewFixedSpace(id: number, data: FixedSpaceRenewRequest) {
  return request.put<FixedSpaceVO>(`/v1/fixed-spaces/${id}/renew`, data)
}

/** 固定车位注销 */
export function cancelFixedSpace(id: number) {
  return request.put<FixedSpaceVO>(`/v1/fixed-spaces/${id}/cancel`)
}

/** 待审核固定车位列表 */
export function getFixedSpacePendingList(params: {
  page?: number
  size?: number
  parkingLotId?: number
}) {
  return request.get<PageResult<FixedSpaceVO>>('/v1/admin/fixed-space-audit/pending', params)
}

/** 通过固定车位审核 */
export function approveFixedSpace(id: number, remark?: string) {
  return request.post<FixedSpaceVO>(`/v1/admin/fixed-space-audit/${id}/approve`, null, {
    params: remark ? { remark } : undefined,
  })
}

/** 驳回固定车位审核 */
export function rejectFixedSpace(id: number, remark?: string) {
  return request.post<FixedSpaceVO>(`/v1/admin/fixed-space-audit/${id}/reject`, null, {
    params: remark ? { remark } : undefined,
  })
}
