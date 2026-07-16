import request from '@/utils/request'

/** 系统参数视图 */
export interface SystemParamVO {
  key: string
  value?: string
  groupName: string
  valueType: string
  options?: string
  description?: string
}

/** 系统参数更新请求 */
export interface SystemParamUpdateRequest {
  value: string
}

/** 查询所有系统参数 */
export function getAllParams() {
  return request.get<SystemParamVO[]>('/v1/system-params')
}

/** 查询系统参数（按分组聚合） */
export function getGroupedParams() {
  return request.get<Record<string, SystemParamVO[]>>('/v1/system-params/grouped')
}

/** 查询单个系统参数 */
export function getParam(key: string) {
  return request.get<SystemParamVO>(`/v1/system-params/${key}`)
}

/** 更新系统参数值 */
export function updateParam(key: string, data: SystemParamUpdateRequest) {
  return request.put<SystemParamVO>(`/v1/system-params/${key}`, data)
}
