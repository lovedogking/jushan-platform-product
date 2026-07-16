import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 手动开闸记录 VO */
export interface ManualGateRecordAdminVO {
  id: number
  operatorName: string
  operationTime: string
  parkingLotId: number
  parkingLotName?: string
  laneName?: string
  reason?: string
  plateNumber?: string
  feeCents?: number
  commandStatus: string
  source: string
  commandType: string
  createdAt: string
}

/** 命令状态映射 */
export const COMMAND_STATUS_MAP: Record<string, string> = {
  PENDING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败',
  UNCERTAIN: '不确定',
  REJECTED: '已拒绝',
  NOT_IMPLEMENTED: '未实现',
}

export const STATUS_COLOR_MAP: Record<string, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  UNCERTAIN: 'orange',
  PENDING: 'processing',
  REJECTED: 'default',
  NOT_IMPLEMENTED: 'default',
}

/**
 * 分页查询手动开闸记录
 */
export function getManualGateRecordPage(params: {
  page?: number
  size?: number
  startTime?: string
  endTime?: string
  parkingLotId?: number
  operatorName?: string
}) {
  return request.get<PageResult<ManualGateRecordAdminVO>>('/v1/admin/manual-gate-records', params)
}
