import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 异常记录 VO */
export interface ExceptionAdminVO {
  id: number
  exceptionType: string
  exceptionTypeLabel: string
  plateNumber?: string
  parkingLotId: number
  parkingLotName?: string
  laneName?: string
  description?: string
  status: string
  statusLabel: string
  createdAt: string
  handledAt?: string
  handlerName?: string
}

/** 异常类型映射 */
export const EXCEPTION_TYPE_MAP: Record<string, string> = {
  DUP_ENTRY: '重复入场',
  RECOGNITION_FAIL: '识别失败',
  BLACKLIST: '黑名单告警',
  UNPAID_INTERCEPT: '未支付拦截',
}

/** 处理状态映射 */
export const EXCEPTION_STATUS_MAP: Record<string, string> = {
  UNHANDLED: '未处理',
  HANDLED: '已处理',
}

/**
 * 分页查询异常记录
 */
export function getExceptionRecordPage(params: {
  page?: number
  size?: number
  exceptionType?: string
}) {
  return request.get<PageResult<ExceptionAdminVO>>('/v1/admin/exception-records', params)
}

/**
 * 标记异常记录为已处理
 */
export function handleException(id: number) {
  return request.post<{ id: number; status: string }>(`/v1/admin/exception-records/${id}/handle`)
}
