import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 审计日志视图 */
export interface AuditLogVO {
  id: number
  tenantId: number
  targetType: string
  targetId: string
  action: string
  operatorId: number
  operatorName: string
  targetTenantId: number | null
  isProxy: number
  beforeValue: string
  afterValue: string
  result: string
  failReason: string
  reason: string
  clientIp: string
  createdAt: string
}

/** 分页查询审计日志 */
export function getAuditLogs(params: {
  page: number
  size: number
  tenantId?: number
  action?: string
  isProxy?: number
  operatorId?: number
  startTime?: string
  endTime?: string
}) {
  return request.get<PageResult<AuditLogVO>>('/admin/audit-logs', { params })
}

/** 查询审计日志详情 */
export function getAuditLog(id: number) {
  return request.get<AuditLogVO>(`/admin/audit-logs/${id}`)
}
